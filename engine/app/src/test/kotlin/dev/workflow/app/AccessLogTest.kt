package dev.workflow.app

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.test.context.TestPropertySource
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** obs.1: ECS JSON on stdout. Its own context (own DB name), so stdout is captured from startup. */
@ExtendWith(OutputCaptureExtension::class)
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:workflow-obs;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "logging.structured.ecs.service.environment=test",
    ],
)
class AccessLogTest : ApiTest() {
    private val client = HttpClient.newHttpClient()

    private fun get(path: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> {
        val b = HttpRequest.newBuilder(URI.create("http://localhost:${env.getProperty("local.server.port")}$path")).GET()
        headers.forEach { (k, v) -> b.header(k, v) }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun lines(out: CapturedOutput): List<Map<String, Any?>> =
        out.out.lines().filter { it.startsWith("{") }.map { json.readValue<Map<String, Any?>>(it) }

    private fun access(out: CapturedOutput, path: String) =
        lines(out).lastOrNull { it.at("event.dataset") == "workflow-engine.access" && it.at("url.path") == path }

    @Test
    fun `an access line is ECS JSON with the required fields`(out: CapturedOutput) {
        assertEquals(200, get("/definitions", mapOf("X-User" to "e.admin")).statusCode())
        val line = assertNotNull(access(out, "/definitions"), "no access line in stdout")
        listOf("@timestamp", "log.level", "message", "service.name", "service.environment", "event.dataset").forEach {
            assertTrue(line.at(it) != null, "missing $it in $line")
        }
        assertEquals("INFO", line.at("log.level").toString().uppercase())
        assertEquals("Request completed", line.at("message"))
        assertEquals("workflow-engine", line.at("service.name"))
        assertEquals("test", line.at("service.environment"))
        assertEquals("GET", line.at("http.request.method"))
        assertEquals(200, line.at("http.response.status_code"))
        assertTrue((line.at("event.duration") as Number).toLong() > 0, "event.duration is in nanoseconds")
        assertEquals("e.admin", line.at("user.name"))
        assertEquals(null, line.at("url.query"), "no query string")
    }

    @Test
    fun `application lines are in the app dataset`(out: CapturedOutput) {
        get("/definitions", mapOf("X-User" to "e.admin"))
        val app = lines(out).filter { it.at("event.dataset") != "workflow-engine.access" }
        assertTrue(app.isNotEmpty() && app.all { it.at("event.dataset") == "workflow-engine.app" })
    }

    @Test
    fun `the query string, the bearer token and the cookie never reach the logs`(out: CapturedOutput) {
        get("/definitions?secret=hunter2", mapOf("X-User" to "e.admin", "Authorization" to "Bearer x.y.z", "Cookie" to "sid=abc123"))
        assertNotNull(access(out, "/definitions"))
        listOf("x.y.z", "hunter2", "abc123", "Bearer").forEach { assertFalse(out.out.contains(it), "$it was logged") }
    }

    @Test
    fun `levels follow the status and health stays quiet`(out: CapturedOutput) {
        get("/health")
        get("/definitions") // 401: no identity
        get("/definitions/no-such-workflow", mapOf("X-User" to "e.admin")) // 404
        assertTrue(lines(out).none { it.at("url.path") == "/health" }, "/health must not log at info")
        assertEquals("WARN", access(out, "/definitions")!!.at("log.level").toString().uppercase())
        assertEquals("WARN", access(out, "/definitions/no-such-workflow")!!.at("log.level").toString().uppercase())
    }
}
