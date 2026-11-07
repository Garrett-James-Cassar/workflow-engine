package dev.workflow.app

import com.sun.net.httpserver.HttpServer
import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import tools.jackson.module.kotlin.readValue
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestConfiguration(proxyBeanMethods = false)
class SpanCapture {
    @Bean fun spanExporter(): InMemorySpanExporter = InMemorySpanExporter.create()

    @Bean fun spanProcessor(exporter: InMemorySpanExporter): SpanProcessor = SimpleSpanProcessor.create(exporter)
}

/** obs.2, obs.4, obs.5 and the endpoint labels, with an in-memory span exporter (no collector needed). */
@ExtendWith(OutputCaptureExtension::class)
@Import(SpanCapture::class)
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:workflow-trace;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "workflow.http.allowed-hosts=localhost,127.0.0.1",
    ],
)
class TracingTest : ApiTest() {
    @Autowired lateinit var spans: InMemorySpanExporter
    @Autowired lateinit var meters: MeterRegistry

    private val client = HttpClient.newHttpClient()
    private val traceId = "4bf92f3577b34da6a3ce929d0e0e4736"
    private val inbound = "00-$traceId-00f067aa0ba902b7-01"

    @BeforeEach fun clean() { spans.reset() }

    @AfterEach fun reset() { FlakyHandler.failing = false }

    private fun post(path: String, headers: Map<String, String>, body: Any): HttpResponse<String> {
        val b = HttpRequest.newBuilder(URI.create("http://localhost:${env.getProperty("local.server.port")}$path"))
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).header("Content-Type", "application/json").header("X-User", "a.ops")
        headers.forEach { (k, v) -> b.header(k, v) }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun lines(out: CapturedOutput): List<Map<String, Any?>> =
        out.out.lines().filter { it.startsWith("{") }.map { json.readValue<Map<String, Any?>>(it) }

    private fun traceSpans(id: String): List<SpanData> {
        repeat(60) {
            val found = spans.finishedSpanItems.filter { it.traceId == id }
            if (found.any { it.attributes.asMap().keys.any { k -> k.key == "fleet.endpoint" } }) return found
            Thread.sleep(50)
        }
        return spans.finishedSpanItems.filter { it.traceId == id }
    }

    private fun originOf(baggage: String?) = dev.workflow.app.observability.EndpointContext.originIn(baggage)

    private fun SpanData.attr(key: String): String? = attributes.asMap().entries.firstOrNull { it.key.key == key }?.value?.toString()

    @Test
    fun `an inbound traceparent continues the trace and every log line in the request holds its ids and labels`(out: CapturedOutput) {
        FlakyHandler.failing = true
        val res = post("/api/v1/definitions/flaky/instances", mapOf("traceparent" to inbound), mapOf("vars" to emptyMap<String, Any>()))
        assertEquals(201, res.statusCode())
        assertTrue(res.headers().firstValue("traceparent").orElse("").startsWith("00-$traceId-"), "the response echoes the same trace")

        val exported = traceSpans(traceId)
        val spanIds = exported.map { it.spanId }.toSet()
        val mine = lines(out).filter { it.at("trace.id") == traceId }
        val accessLines = mine.filter { it.at("event.dataset") == "workflow-engine.access" }
        assertEquals(1, accessLines.size, "one access line per request: $accessLines")
        val access = accessLines.single()
        val warn = mine.first { it.at("log.level") == "WARN" && it.at("event.dataset") == "workflow-engine.app" }
        val error = mine.first { it.at("log.level") == "ERROR" }
        listOf(access, warn, error).forEach { line ->
            assertTrue(line.at("span.id") in spanIds, "span.id belongs to the trace: $line")
            assertEquals("Start run", line.at("labels.endpoint"), "$line")
            assertEquals("instances.start", line.at("event.action"), "$line")
            assertEquals("workflow-engine: Start run", line.at("labels.origin"), "$line")
        }
        assertNotNull(error.at("error.stack_trace"), "the error line carries the stack")

        val tagged = exported.filter { it.attr("fleet.endpoint") != null }
        assertEquals(1, tagged.size, "one span carries the labels: ${exported.map { it.name + "/" + it.kind + "/" + it.attr("fleet.endpoint") }}")
        val server = tagged.single()
        assertEquals("Start run", server.attr("fleet.endpoint"))
        assertEquals("instances.start", server.attr("fleet.action"))
        assertEquals("workflow-engine: Start run", server.attr("fleet.origin"))
        assertTrue(exported.any { it.name == "workflow.instance.start" }, "one manual span per use case: ${exported.map { it.name }}")
        assertTrue(exported.any { it.attr("db.system") != null || it.attr("db.system.name") != null }, "DB spans: ${exported.map { it.name }}")
        val serverTimer = meters.find("http.server.request.duration").timer()
        assertNotNull(serverTimer, "RED server histogram")
        listOf("http.request.method", "http.response.status_code", "http.route").forEach { assertNotNull(serverTimer.id.getTag(it), "semconv tag $it on ${serverTimer.id.tags}") }
    }

    @Test
    fun `the outbound call carries the trace and keeps an inbound origin`() {
        val seen = ConcurrentHashMap<String, String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/callout") { ex ->
                seen["traceparent"] = ex.requestHeaders.getFirst("traceparent") ?: ""
                seen["baggage"] = ex.requestHeaders.getFirst("baggage") ?: ""
                ex.sendResponseHeaders(204, -1)
                ex.close()
            }
            start()
        }
        try {
            val vars = mapOf("vars" to mapOf("target" to mapOf("url" to "http://127.0.0.1:${server.address.port}/callout"), "payload" to mapOf("id" to "D-1")))
            assertEquals(201, post("/api/v1/definitions/http-callout/instances", mapOf("traceparent" to inbound, "baggage" to "fleet.origin=forge%3A%20Publish%20rule"), vars).statusCode())
            assertTrue(seen["traceparent"]!!.startsWith("00-$traceId-"), "outbound traceparent: ${seen["traceparent"]}")
            assertEquals("forge: Publish rule", originOf(seen["baggage"]), "an inbound origin is kept unchanged: ${seen["baggage"]}")

            val other = "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01"
            assertEquals(201, post("/api/v1/definitions/http-callout/instances", mapOf("traceparent" to other), vars).statusCode())
            assertEquals("workflow-engine: Start run", originOf(seen["baggage"]), "without one, this app's own origin is sent: ${seen["baggage"]}")
            val client = meters.find("http.client.request.duration").timer()
            assertNotNull(client, "RED client histogram")
            listOf("http.request.method", "http.response.status_code", "server.address").forEach { assertNotNull(client.id.getTag(it), "semconv tag $it on ${client.id.tags}") }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a background tick is its own root span with the job's labels on its log lines`(out: CapturedOutput) {
        poller.fireDue()
        val tick = spans.finishedSpanItems.last { it.name == "job.timeouts.fire" }
        assertEquals("Fire due timeouts", tick.attr("fleet.endpoint"))
        assertEquals("timeouts.fire", tick.attr("fleet.action"))
        assertEquals("workflow-engine: Fire due timeouts", tick.attr("fleet.origin"))
        assertTrue(!tick.parentSpanContext.isValid, "a job starts its own root span")
    }
}
