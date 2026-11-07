package dev.workflow.app.clients

import dev.workflow.app.ports.HttpCaller
import dev.workflow.core.HttpSpec
import dev.workflow.core.Vars
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
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

private val OK_RANGE = 200..299

/** One outbound call: where, what and with which vars. */
private class Call(val spec: HttpSpec, val uri: URI, val vars: Vars, val body: String?) {
    fun headers(h: HttpHeaders) {
        if (body != null) h.contentType = MediaType.APPLICATION_JSON
        spec.headers.forEach { (k, v) -> h.add(k, vars.fill(v)) }
    }
}

/** The built-in HTTP service task, over Boot's RestClient builder so the call is observed (client span, duration metric, traceparent out). */
@Component
class HttpCallerClient(
    builder: RestClient.Builder,
    private val json: JsonMapper,
    @Value("\${workflow.http.allowed-hosts:}") allowedHosts: String,
) : HttpCaller {
    private val allowed = allowedHosts.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    private val http = builder.requestFactory(factory()).build()

    override fun call(spec: HttpSpec, vars: Vars): Map<String, Any?> {
        val call = Call(spec, allowedUri(vars.fill(spec.url)), vars, spec.bodyFrom?.let { json.writeValueAsString(vars[it]) })
        val (status, text) = exchange(call)
        check(status in OK_RANGE) { "${spec.method} ${call.uri} returned $status" }
        return mapOf("status" to status, "body" to parse(text))
    }

    private fun factory() = JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build())
        .also { it.setReadTimeout(Duration.ofSeconds(30)) }

    private fun allowedUri(url: String): URI = URI.create(url).also {
        check(it.host?.lowercase() in allowed) { "host '${it.host?.lowercase()}' is not in workflow.http.allowed-hosts" }
    }

    private fun exchange(call: Call): Pair<Int, String> {
        val request = http.method(HttpMethod.valueOf(call.spec.method.name)).uri(call.uri).headers(call::headers)
        return (call.body?.let { request.body(it) } ?: request).exchange { _, res -> res.statusCode.value() to String(res.body.readAllBytes(), Charsets.UTF_8) }
    }

    private fun parse(text: String): Any? = if (text.isBlank()) null else runCatching { json.readValue<Any?>(text) }.getOrDefault(text)
}
