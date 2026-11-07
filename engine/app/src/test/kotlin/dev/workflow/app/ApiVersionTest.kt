package dev.workflow.app

import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** api.3: business routes live under /api/v1; the old unversioned paths answer as deprecated aliases. */
class ApiVersionTest : ApiTest() {
    private fun raw(path: String): HttpResponse<String> = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create("http://localhost:${env.getProperty("local.server.port")}$path")).header("X-User", ADMIN).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    @Test
    fun `E11 the versioned path answers without deprecation headers`() {
        val res = raw("/api/v1/definitions")
        assertEquals(200, res.statusCode())
        assertNull(res.headers().firstValue("Deprecation").orElse(null))
    }

    @Test
    fun `E11 the unversioned alias answers with Deprecation, Sunset and successor Link`() {
        val res = raw("/definitions/trade-booking")
        assertEquals(200, res.statusCode())
        assertEquals("true", res.headers().firstValue("Deprecation").orElse(null))
        assertTrue(res.headers().firstValue("Sunset").orElse("").endsWith("GMT"), "Sunset: ${res.headers().firstValue("Sunset")}")
        assertEquals("</api/v1/definitions/trade-booking>; rel=\"successor-version\"", res.headers().firstValue("Link").orElse(null))
        assertEquals(raw("/api/v1/definitions/trade-booking").body(), res.body())
    }

    @Test
    fun `E11 operational routes stay at the root`() {
        assertEquals(200, raw("/health").statusCode())
        assertEquals(404, raw("/api/v1/health").statusCode())
        val docs = raw("/openapi.json").body()
        assertTrue(docs.contains("\"servers\":[{\"url\":\"/api/v1\"") && docs.contains("\"/definitions\":"), docs.take(300))
    }
}
