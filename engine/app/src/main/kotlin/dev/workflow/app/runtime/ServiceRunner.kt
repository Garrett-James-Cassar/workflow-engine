package dev.workflow.app.runtime

import dev.workflow.core.HttpSpec
import dev.workflow.core.ServiceContext
import dev.workflow.core.ServiceHandler
import dev.workflow.core.Step
import dev.workflow.core.Vars
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration

sealed interface ServiceOutcome {
    data class Ok(val outcome: String, val output: Map<String, Any?>, val actor: String) : ServiceOutcome

    data class Failed(val message: String) : ServiceOutcome
}

/** Runs a service task's linked code (a handler bean, or the built-in HTTP call) with retries. */
@Component
class ServiceRunner(
    private val context: ApplicationContext,
    private val json: JsonMapper,
    @Value("\${workflow.http.allowed-hosts:}") allowedHosts: String,
    @Value("\${workflow.service.backoff:200ms}") private val backoff: Duration,
    builder: RestClient.Builder,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val allowed = allowedHosts.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    // Boot's builder, so the built-in http call is observed: client span, duration metric, traceparent out.
    private val http = builder.requestFactory(
        JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build())
            .also { it.setReadTimeout(Duration.ofSeconds(30)) },
    ).build()

    fun run(step: Step.ServiceTask, ctx: ServiceContext): ServiceOutcome {
        var lastError: Exception? = null
        for (attempt in 0..step.retries) {
            try {
                return attemptOnce(step, ctx)
            } catch (e: Exception) {
                lastError = e
                log.warn("service task '{}' attempt {} of {} failed: {}", step.id, attempt + 1, step.retries + 1, e.message)
                if (attempt < step.retries) Thread.sleep(backoff.toMillis() shl attempt)
            }
        }
        val message = "service task '${step.id}' failed after ${step.retries + 1} attempt(s): ${lastError?.message ?: lastError}"
        log.error("service task '{}' failed after {} attempt(s); the run stops on an incident", step.id, step.retries + 1, lastError)
        return ServiceOutcome.Failed(message)
    }

    private fun attemptOnce(step: Step.ServiceTask, ctx: ServiceContext): ServiceOutcome.Ok {
        step.http?.let { return ServiceOutcome.Ok("OK", call(it, ctx.vars), "service:http") }
        @Suppress("UNCHECKED_CAST")
        val type = step.handler!!.java as Class<ServiceHandler<*>>
        val handler: ServiceHandler<*> = context.getBeanProvider(type).getIfAvailable {
            context.autowireCapableBeanFactory.createBean(type)
        }
        val result = handler.execute(ctx)
        return ServiceOutcome.Ok(result.outcome.name, result.output, "service:${type.simpleName}")
    }

    private fun call(spec: HttpSpec, vars: Vars): Map<String, Any?> {
        val uri = URI.create(vars.fill(spec.url))
        val host = uri.host?.lowercase()
        if (host == null || host !in allowed) {
            throw IllegalStateException("host '$host' is not in workflow.http.allowed-hosts")
        }
        val body = spec.bodyFrom?.let { json.writeValueAsString(vars[it]) }
        val response = http.method(HttpMethod.valueOf(spec.method.name)).uri(uri)
            .headers { h ->
                if (body != null) h.contentType = MediaType.APPLICATION_JSON
                spec.headers.forEach { (k, v) -> h.add(k, vars.fill(v)) }
            }
            .let { r -> if (body != null) r.body(body) else r }
            .exchange { _, res -> res.statusCode.value() to String(res.body.readAllBytes(), Charsets.UTF_8) }
        if (response.first !in 200..299) {
            throw IllegalStateException("${spec.method} $uri returned ${response.first}")
        }
        val text = response.second
        val parsed: Any? = if (text.isBlank()) null else runCatching { json.readValue<Any?>(text) }.getOrDefault(text)
        return mapOf("status" to response.first, "body" to parsed)
    }
}
