package dev.workflow.app.observability

import dev.workflow.app.security.Authenticated
import dev.workflow.app.security.Public
import dev.workflow.app.security.Requires
import io.micrometer.tracing.Span
import io.micrometer.tracing.Tracer
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.restclient.RestClientCustomizer
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A background job's label and action (OBSERVABILITY.md, endpoint labels), declared next to its schedule the
 * way a route declares its permission. The action is `<resource>.<verb>` of the job.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Job(val label: String, val action: String)

/**
 * What the current request or job is, held in the MDC so the logger adds it to every line: `labels.endpoint`
 * (the label), `event.action` (the permission name) and `labels.origin` (`<app>: <label>` of the endpoint that
 * started the chain). The same values go on the span as `fleet.endpoint`, `fleet.action`, `fleet.origin`.
 */
object EndpointContext {
    const val APP = "workflow-engine"
    const val ENDPOINT = "labels.endpoint"
    const val ACTION = "event.action"
    const val ORIGIN = "labels.origin"
    const val BAGGAGE = "baggage"
    const val ORIGIN_KEY = "fleet.origin"
    private const val MAX_ORIGIN = 200

    fun enter(label: String, action: String, origin: String, span: Span?) {
        MDC.put(ENDPOINT, label)
        MDC.put(ACTION, action)
        MDC.put(ORIGIN, origin)
        span?.tag("fleet.endpoint", label)?.tag("fleet.action", action)?.tag("fleet.origin", origin)
    }

    fun clear() = listOf(ENDPOINT, ACTION, ORIGIN).forEach(MDC::remove)

    fun origin(): String? = MDC.get(ORIGIN)

    /** The `fleet.origin` member of a W3C `baggage` header, decoded; null when absent. An inbound origin is kept unchanged. */
    fun originIn(baggage: String?): String? = baggage?.split(',')
        ?.map { it.substringBefore(';').trim() }
        ?.firstOrNull { it.substringBefore('=').trim() == ORIGIN_KEY }
        ?.substringAfter('=', "")
        ?.let { URLDecoder.decode(it.replace("+", "%2B"), Charsets.UTF_8) }
        ?.filter { !it.isISOControl() }
        ?.take(MAX_ORIGIN)
        ?.takeIf { it.isNotBlank() }

    /** The header value to send: any existing members, plus `fleet.origin=<percent-encoded>`. */
    fun baggageWith(existing: String?, origin: String): String {
        if (existing?.contains("$ORIGIN_KEY=") == true) return existing
        val member = "$ORIGIN_KEY=" + URLEncoder.encode(origin, Charsets.UTF_8).replace("+", "%20")
        return if (existing.isNullOrBlank()) member else "$existing,$member"
    }
}

/**
 * First interceptor: names the request from its handler's annotation. `@Requires` and `@Authenticated` carry a
 * label; `@Public` routes, static assets and docs use their route (`GET /health`). The origin is the inbound
 * `fleet.origin` baggage if there is one, else this app's own `<app>: <label>`.
 */
@Component
class EndpointLabelInterceptor(private val tracer: ObjectProvider<Tracer>) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val route = "${request.method} ${request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) ?: request.requestURI}"
        val (label, action) = (handler as? HandlerMethod)?.let { declared(it) } ?: (route to fallbackAction(request))
        val origin = EndpointContext.originIn(request.getHeader(EndpointContext.BAGGAGE)) ?: "${EndpointContext.APP}: $label"
        EndpointContext.enter(label.ifBlank { route }, action, origin, tracer.ifAvailable?.currentSpan())
        return true
    }

    private fun declared(h: HandlerMethod): Pair<String, String>? {
        val m = h.method
        m.getAnnotation(Requires::class.java)?.let { return it.label to it.permission }
        m.getAnnotation(Authenticated::class.java)?.let { return it.label to it.action }
        return null
    }

    private fun fallbackAction(request: HttpServletRequest) = if (request.method == "GET") "public.read" else "public.write"
}

/** Sends the origin on every outbound call in the W3C `baggage` header, next to `traceparent`. */
@Component
class OriginPropagation : RestClientCustomizer {
    override fun customize(builder: org.springframework.web.client.RestClient.Builder) {
        builder.requestInterceptor(ClientHttpRequestInterceptor { request, body, execution ->
            EndpointContext.origin()?.let { request.headers.set(EndpointContext.BAGGAGE, EndpointContext.baggageWith(request.headers.getFirst(EndpointContext.BAGGAGE), it)) }
            execution.execute(request, body)
        })
    }
}

/** Runs a background job as its own root span and its own origin, with the job's label on every line it logs. */
@Component
class JobRunner(private val spans: UseCaseSpans, private val tracer: ObjectProvider<Tracer>) {
    fun <T> run(job: Job, block: () -> T): T = spans.run("job.${job.action}") {
        EndpointContext.enter(job.label, job.action, "${EndpointContext.APP}: ${job.label}", tracer.ifAvailable?.currentSpan())
        try {
            block()
        } finally {
            EndpointContext.clear()
        }
    }
}
