package dev.workflow.app

import org.junit.jupiter.api.Test
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals

/**
 * api.5: the served OpenAPI document is committed as `openapi.json`, so CI can diff it against `origin/main` (oasdiff).
 * This test fails when the committed file is stale; `make openapi` (OPENAPI_WRITE=1) rewrites it.
 */
class OpenApiSpecTest : ApiTest() {
    private val sorted = JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build()

    private fun served(): String {
        val res = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://localhost:${env.getProperty("local.server.port")}/openapi.json")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, res.statusCode())
        return sorted.writerWithDefaultPrettyPrinter().writeValueAsString(sorted.readValue(res.body(), Map::class.java)) + "\n"
    }

    @Test
    fun `E14 the committed openapi json matches the served spec`() {
        val file = File(System.getProperty("openapiFile"))
        val current = served()
        if (System.getenv("OPENAPI_WRITE") == "1") file.writeText(current)
        assertEquals(current, file.takeIf { it.exists() }?.readText(), "openapi.json is stale: run `make openapi` and commit it")
    }
}
