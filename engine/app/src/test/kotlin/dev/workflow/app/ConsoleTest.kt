package dev.workflow.app

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsoleTest : ApiTest() {
    private fun raw(path: String, accept: String): HttpResponse<String> =
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://localhost:${env.getProperty("local.server.port")}$path")).header("Accept", accept).header("X-User", ADMIN).build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun `export returns a picture page to browsers and Mermaid text to everyone else`() {
        val page = raw("/definitions/trade-booking/export", "text/html,application/xhtml+xml")
        assertTrue(page.headers().firstValue("Content-Type").get().startsWith("text/html"))
        assertTrue(page.body().contains("<pre class=\"mermaid\">"))
        assertTrue(page.body().contains("/vendor/mermaid.min.js"), "Mermaid is vendored, not loaded from a CDN")
        assertEquals(200, raw("/vendor/mermaid.min.js", "*/*").statusCode())
        assertTrue(page.body().contains("Download PNG"))
        assertTrue(page.body().contains("n_book_trade --&gt;|"), "mermaid source is HTML-escaped inside the page")

        val text = raw("/definitions/trade-booking/export", "*/*")
        assertTrue(text.body().contains("%% trade-booking v1") && text.body().contains("flowchart LR"))
        assertEquals(400, raw("/definitions/trade-booking/export?format=gif", "*/*").statusCode())
    }

    @Test
    fun `the console is served at the root, with its data endpoints`() {
        val index = raw("/", "text/html")
        assertEquals(200, index.statusCode())
        assertTrue(index.body().contains("<title>Workflow engine</title>"))
        assertEquals(200, raw("/app.js", "*/*").statusCode())
        assertTrue(call("GET", "/rbac/people").list.any { it["user"] == "l.jones" && (it["teams"] as List<*>).contains("legal") })
        assertEquals(mapOf("up" to true), call("GET", "/demo/booking-system").map)
    }
}
