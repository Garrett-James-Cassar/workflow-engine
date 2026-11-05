package dev.workflow.app

import dev.workflow.app.runtime.TimeoutPoller
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestSupport::class)
@TestPropertySource(properties = ["workflow.timeouts.poll-interval=1h", "workflow.service.backoff=1ms"])
abstract class ApiTest {
    @Autowired lateinit var env: Environment
    @Autowired lateinit var json: JsonMapper
    @Autowired lateinit var clock: MutableClock
    @Autowired lateinit var poller: TimeoutPoller
    @Autowired lateinit var jdbc: JdbcClient

    private val http = HttpClient.newHttpClient()

    data class Res(val status: Int, val body: Any?) {
        @Suppress("UNCHECKED_CAST")
        val map get() = body as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val list get() = body as List<Map<String, Any?>>
    }

    protected fun call(method: String, path: String, user: String? = null, body: Any? = null): Res {
        val req = HttpRequest.newBuilder(URI.create("http://localhost:${env.getProperty("local.server.port")}$path"))
            .method(method, body?.let { HttpRequest.BodyPublishers.ofString(json.writeValueAsString(it)) } ?: HttpRequest.BodyPublishers.noBody())
            .header("Content-Type", "application/json")
            .apply { if (user != null) header("X-User", user) }
            .build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        val parsed: Any? = if (res.body().isBlank()) null else runCatching { json.readValue<Any?>(res.body()) }.getOrDefault(res.body())
        return Res(res.statusCode(), parsed)
    }

    protected fun doc(notional: Long = 75_000_000, counterparty: String = "ACME") =
        mapOf("doc" to mapOf("id" to "D-1", "requester" to "a.ops", "counterparty" to counterparty, "notional" to notional))

    protected fun start(vars: Map<String, Any?>, name: String = "confirmation-approval"): Res =
        call("POST", "/definitions/$name/instances", "a.ops", mapOf("vars" to vars)).also { assertEquals(201, it.status, "${it.body}") }

    protected fun openTask(instance: Res): String {
        @Suppress("UNCHECKED_CAST")
        val tasks = instance.map["openTasks"] as List<Map<String, Any?>>
        return tasks.single()["id"] as String
    }

    protected fun complete(instance: Res, user: String, outcome: String, form: Map<String, Any?> = emptyMap()): Res =
        call("POST", "/tasks/${openTask(instance)}/complete", user, mapOf("outcome" to outcome, "form" to form))

    @Suppress("UNCHECKED_CAST")
    protected fun Res.vars(path: String): Any? =
        path.split('.').fold(map["vars"]) { node, key -> (node as? Map<String, Any?>)?.get(key) }

}
