package dev.workflow.app

import dev.workflow.app.api.ReadinessController
import dev.workflow.app.ports.DatabaseProbe
import dev.workflow.app.runtime.ReadinessService
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** obs.3: `/ready` answers for the app's own database only; `/health` stays liveness. */
class ReadinessTest : ApiTest() {
    private fun raw(path: String): HttpResponse<String> = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create("http://localhost:${env.getProperty("local.server.port")}$path")).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    @Test
    fun `E12 ready is 200 with the db up, needs no auth and is not an alias`() {
        val res = raw("/ready")
        assertEquals(200, res.statusCode())
        assertEquals(mapOf("status" to "ready", "checks" to mapOf("db" to "up")), json.readValue(res.body(), Map::class.java))
        assertNull(res.headers().firstValue("Deprecation").orElse(null))
    }

    @Test
    fun `E12 ready is 503 naming the db when the database is down`() {
        val down = DatabaseProbe { false }
        MockMvcBuilders.standaloneSetup(ReadinessController(ReadinessService(down))).build()
            .perform(get("/ready"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(content().json("""{"status":"not-ready","checks":{"db":"down"}}""", true))
    }
}
