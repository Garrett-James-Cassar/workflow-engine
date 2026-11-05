package dev.workflow.app.runtime

import dev.workflow.core.HttpSpec
import dev.workflow.core.ServiceContext
import dev.workflow.core.ServiceHandler
import dev.workflow.core.Step
import dev.workflow.core.Vars
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val allowed = allowedHosts.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

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
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .method(spec.method.name, body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
            .apply {
                if (body != null) header("Content-Type", "application/json")
                spec.headers.forEach { (k, v) -> header(k, vars.fill(v)) }
            }
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("${spec.method} $uri returned ${response.statusCode()}")
        }
        val text = response.body()
        val parsed: Any? = if (text.isBlank()) null else runCatching { json.readValue<Any?>(text) }.getOrDefault(text)
        return mapOf("status" to response.statusCode(), "body" to parsed)
    }
}
