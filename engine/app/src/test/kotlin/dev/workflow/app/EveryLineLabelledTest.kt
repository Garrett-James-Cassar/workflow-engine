package dev.workflow.app

import dev.workflow.app.runtime.TimeoutPoller
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * obs.1 origin coverage: boot in-process, make one request, run one job, shut down, and every JSON line on
 * stdout (Spring, Hibernate, Flyway, Tomcat included) has labels.endpoint, labels.origin and event.action.
 */
@ExtendWith(OutputCaptureExtension::class)
class EveryLineLabelledTest {
    @Test
    fun `E10 every stdout line carries labels endpoint origin and event action`(out: CapturedOutput) {
        val ctx = SpringApplicationBuilder(WorkflowEngineApplication::class.java)
            .web(WebApplicationType.SERVLET)
            .run(
                "--server.port=0", "--workflow.timeouts.poll-interval=1h", "--logging.structured.ecs.service.environment=test",
                "--spring.datasource.url=jdbc:h2:mem:workflow-every-line;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
                "--spring.datasource.username=sa", "--spring.datasource.password=", "--spring.flyway.locations=classpath:db/migration-h2",
            )
        val port = ctx.environment.getProperty("local.server.port")
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port/definitions")).header("X-User", "e.admin").GET().build()
        assertEquals(200, HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
        ctx.getBean(TimeoutPoller::class.java).fireDue()
        ctx.close()

        val lines = out.out.lines().filter { it.startsWith("{") }.map { JsonMapper().readValue<Map<String, Any?>>(it) }
        assertTrue(lines.size > 20, "expected the whole boot on stdout, got ${lines.size} lines")
        val missing = lines.filter { l -> listOf("labels.endpoint", "labels.origin", "event.action").any { l.at(it).toString().isBlank() || l.at(it) == null } }
        assertTrue(missing.isEmpty(), "lines without all three labels: ${missing.take(3)}")
        val endpoints = lines.map { it.at("labels.endpoint") }.toSet()
        listOf("Startup", "Background", "Shutdown", "List workflows").forEach { assertTrue(it in endpoints, "no $it line in $endpoints") }
        assertTrue(lines.all { (it.at("labels.origin") as String).startsWith("workflow-engine: ") })
    }
}
