package dev.workflow.app

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * NFR A2 and A3 on a real PostgreSQL, against the real application (not MockMvc): a collaborator being down never takes
 * the engine down, and a restart loses nothing that was committed. Runs in `postgresTest`.
 */
@Tag("postgres")
class ResilienceTest {
    private val json = JsonMapper()
    private val http = HttpClient.newHttpClient()

    private fun boot(vararg extra: String): ConfigurableApplicationContext = SpringApplication.run(
        WorkflowEngineApplication::class.java,
        "--server.port=0", "--spring.datasource.url=${pg.jdbcUrl}", "--spring.datasource.username=${pg.username}",
        "--spring.datasource.password=${pg.password}", "--workflow.rate-limit.enabled=false", "--workflow.timeouts.poll-interval=1h",
        *extra,
    )

    private fun ConfigurableApplicationContext.send(method: String, path: String, body: Any? = null): HttpResponse<String> = http.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:${environment.getProperty("local.server.port")}$path"))
            .method(method, body?.let { HttpRequest.BodyPublishers.ofString(json.writeValueAsString(it)) } ?: HttpRequest.BodyPublishers.noBody())
            .header("Content-Type", "application/json").header("X-User", "a.ops").build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun count(table: String): Int = DriverManagerDataSource(pg.jdbcUrl, pg.username, pg.password).connection.use { c ->
        c.createStatement().use { st -> st.executeQuery("select count(*) from $table").use { it.next(); it.getInt(1) } }
    }

    @Test
    fun `A2 with rbac unreachable the engine starts, stays ready and answers with a clear 503`() {
        boot("--workflow.rbac.url=http://127.0.0.1:1").use { app ->
            assertEquals(200, app.send("GET", "/health").statusCode())
            assertEquals(200, app.send("GET", "/ready").statusCode(), "/ready checks only the engine's own database")
            val res = app.send("GET", "/api/v1/instances")
            assertEquals(503, res.statusCode(), "the affected feature degrades with an error, it does not allow")
            assertTrue(res.body().contains("rbac"), res.body())
        }
    }

    @Test
    fun `A3 a restart during writes loses no run the engine acknowledged`() {
        val acknowledged = AtomicInteger()
        val first = AtomicReference(boot())
        val before = count("instances")
        val pool = Executors.newFixedThreadPool(8)
        repeat(8) {
            pool.submit {
                while (!Thread.currentThread().isInterrupted) {
                    val res = runCatching {
                        first.get().send("POST", "/api/v1/definitions/confirmation-approval/instances", mapOf("vars" to Mother.doc(75_000_000, "ACME")))
                    }.getOrNull()
                    if (res?.statusCode() == 201) acknowledged.incrementAndGet() else if (res == null) break
                }
            }
        }
        while (acknowledged.get() < 40) Thread.sleep(50)
        first.get().close() // the process goes away mid-write
        pool.shutdownNow()
        boot().use { second ->
            assertEquals(200, second.send("GET", "/ready").statusCode())
            assertTrue(count("instances") - before >= acknowledged.get(), "a run the engine acknowledged is gone after the restart")
        }
    }

    companion object {
        private val pg: PostgreSQLContainer = PostgreSQLContainer("postgres:17-alpine").also { it.start() }

        @JvmStatic
        @AfterAll
        fun stop() = pg.stop()
    }
}
