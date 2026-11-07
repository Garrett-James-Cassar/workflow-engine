package dev.workflow.app.observability

import ch.qos.logback.classic.spi.ILoggingEvent
import dev.workflow.app.security.Identity
import io.micrometer.tracing.Tracer
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.slf4j.event.Level
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.json.JsonWriter
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

const val ACCESS_LOGGER = "workflow-engine.access"
private const val APP_DATASET = "workflow-engine.app"
private const val QUIET_PATHS = "/health,/ready"

/**
 * One access line per request (OBSERVABILITY.md section 1): method, path without query, status, duration
 * in nanoseconds and the login. Never headers, tokens or bodies. Runs inside the server observation, so
 * the line carries the request's trace.id and span.id, and the response echoes the `traceparent`.
 */
@Component
class AccessLogFilter(private val tracer: ObjectProvider<Tracer>) : OncePerRequestFilter(), Ordered {
    private val log = LoggerFactory.getLogger(ACCESS_LOGGER)

    override fun getOrder() = Ordered.HIGHEST_PRECEDENCE + 10

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val started = System.nanoTime()
        echoTraceparent(response)
        try {
            chain.doFilter(request, response)
        } finally {
            write(request, response.status, System.nanoTime() - started)
            EndpointContext.clear()
        }
    }

    private fun echoTraceparent(response: HttpServletResponse) {
        val ctx = tracer.ifAvailable?.currentSpan()?.context() ?: return
        response.setHeader("traceparent", "00-${ctx.traceId()}-${ctx.spanId()}-${if (ctx.sampled() == true) "01" else "00"}")
    }

    private fun write(request: HttpServletRequest, status: Int, nanos: Long) {
        val path = request.requestURI
        val level = when {
            status >= 500 -> Level.ERROR
            status >= 400 -> Level.WARN
            QUIET_PATHS.split(',').any { path == it } -> Level.DEBUG
            else -> Level.INFO
        }
        log.atLevel(level)
            .addKeyValue("http.request.method", request.method)
            .addKeyValue("url.path", path)
            .addKeyValue("http.response.status_code", status)
            .addKeyValue("event.dataset", ACCESS_LOGGER)
            .addKeyValue("event.duration", nanos)
            .also { b -> MDC.get(EndpointContext.ACTION)?.let { b.addKeyValue("event.action", it) } }
            .also { b -> (request.getAttribute(Identity.LOGIN) as String?)?.let { b.addKeyValue("user.name", it) } }
            .log("Request completed")
    }
}

/**
 * ECS fields Boot's formatter does not fill by itself, all read from the MDC: `event.dataset` and `event.action`
 * (request lines set their own, so they get none here), `labels.endpoint` and `labels.origin` (the endpoint label
 * and the endpoint that started the chain), and `trace.id` / `span.id` from the active span (Micrometer Tracing
 * puts them in the MDC as traceId and spanId). A line written outside a span or a request has none of them.
 */
class EventDatasetCustomizer : StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {
    override fun customize(members: JsonWriter.Members<ILoggingEvent>) {
        members.add("event") { e: ILoggingEvent -> if (e.loggerName == ACCESS_LOGGER) null else appEvent(e) }.whenNotNull()
        members.add("labels") { e: ILoggingEvent -> labels(e) }.whenNotNull()
        members.add("trace") { e: ILoggingEvent -> e.mdcPropertyMap["traceId"]?.let { mapOf("id" to it) } }.whenNotNull()
        members.add("span") { e: ILoggingEvent -> e.mdcPropertyMap["spanId"]?.let { mapOf("id" to it) } }.whenNotNull()
    }

    private fun appEvent(e: ILoggingEvent): Map<String, String> =
        listOfNotNull("dataset" to APP_DATASET, e.mdcPropertyMap[EndpointContext.ACTION]?.let { "action" to it }).toMap()

    private fun labels(e: ILoggingEvent): Map<String, String>? =
        listOfNotNull(
            e.mdcPropertyMap[EndpointContext.ENDPOINT]?.let { "endpoint" to it },
            e.mdcPropertyMap[EndpointContext.ORIGIN]?.let { "origin" to it },
        ).toMap().takeIf { it.isNotEmpty() }
}
