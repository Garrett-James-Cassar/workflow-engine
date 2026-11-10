package dev.workflow.app

import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** sec.2: security headers on responses and strict CORS from CORS_ORIGINS. */
class SecurityHeadersTest : ApiTest() {
    private fun url(path: String) = URI.create("http://localhost:${env.getProperty("local.server.port")}$path")

    private fun send(request: HttpRequest.Builder): HttpResponse<String> =
        HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString())

    private fun preflight(origin: String) = send(
        HttpRequest.newBuilder(url("/api/v1/definitions"))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .header("Origin", origin)
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "x-user"),
    )

    @Test
    fun `E13 responses carry nosniff and the API CSP, and docs keep scripts`() {
        val res = send(HttpRequest.newBuilder(url("/api/v1/definitions")).header("X-User", ADMIN).GET())
        assertEquals("nosniff", res.headers().firstValue("X-Content-Type-Options").orElse(null))
        assertEquals("default-src 'none'; frame-ancestors 'none'", res.headers().firstValue("Content-Security-Policy").orElse(null))
        assertEquals("DENY", res.headers().firstValue("X-Frame-Options").orElse(null))
        assertEquals("no-referrer", res.headers().firstValue("Referrer-Policy").orElse(null))
        assertEquals("same-origin", res.headers().firstValue("Cross-Origin-Resource-Policy").orElse(null))
        assertNull(res.headers().firstValue("Strict-Transport-Security").orElse(null))
        val docs = send(HttpRequest.newBuilder(url("/docs")).GET())
        assertNull(docs.headers().firstValue("Content-Security-Policy").orElse(null))
        assertEquals("nosniff", docs.headers().firstValue("X-Content-Type-Options").orElse(null))
    }

    @Test
    fun `E13 a preflight from an allowed origin answers 204 with the allow headers`() {
        val res = preflight("http://localhost:5183")
        assertEquals(204, res.statusCode())
        assertEquals("http://localhost:5183", res.headers().firstValue("Access-Control-Allow-Origin").orElse(null))
        assertTrue(res.headers().firstValue("Access-Control-Allow-Headers").orElse("").contains("x-user"))
        assertTrue(res.headers().firstValue("Access-Control-Allow-Methods").orElse("").contains("POST"))
    }

    @Test
    fun `E13 an unknown origin gets no allow-origin header`() {
        val pre = preflight("https://evil.example")
        assertNull(pre.headers().firstValue("Access-Control-Allow-Origin").orElse(null))
        val res = send(HttpRequest.newBuilder(url("/health")).header("Origin", "https://evil.example").GET())
        assertEquals(200, res.statusCode())
        assertNull(res.headers().firstValue("Access-Control-Allow-Origin").orElse(null))
    }
}
