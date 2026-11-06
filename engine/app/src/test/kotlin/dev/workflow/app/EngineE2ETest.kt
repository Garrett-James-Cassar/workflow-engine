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

class EngineE2ETest : ApiTest() {
    @Test
    fun `E5 E6 reject, rework, resubmit, approve, senior path, signature event, completed`() {
        var i = start(doc())
        val id = i.map["id"] as String
        assertEquals("waiting", i.map["status"])
        assertEquals("legal-review", i.map["currentStep"])

        // Task inbox is RBAC-resolved.
        // A requester holds no tasks.read, so the task listing is refused outright (deny by default).
        assertEquals(403, call("GET", "/tasks?mine=true&instance=$id", "a.ops").status)
        assertEquals("legal-review", call("GET", "/tasks?mine=true&instance=$id", "l.jones").list.single()["step"])
        assertEquals(listOf("APPROVE", "REJECT", "COUNTER"), call("GET", "/tasks?instance=$id").list.single()["outcomes"])

        // Not legal: 403 with the RBAC reason.
        val forbidden = complete(i, "a.ops", "APPROVE")
        assertEquals(403, forbidden.status)
        assertEquals("'a.ops' does not hold role 'legal'", forbidden.map["reason"])

        // Validation: outcome must be in the enum, form fields must be declared.
        assertEquals(400, complete(i, "l.jones", "MAYBE").status)
        assertEquals(400, complete(i, "l.jones", "REJECT", mapOf("nope" to "x")).status)

        // REJECT → rework path: notify (service) → amend-draft assigned to the requester from vars.
        i = complete(i, "l.jones", "REJECT", mapOf("comment" to "fix clause 4"))
        assertEquals(200, i.status, "${i.body}")
        assertEquals("amend-draft", i.map["currentStep"])
        assertEquals("a.ops", i.vars("notify-requester.notified"))
        assertEquals("fix clause 4", i.vars("legal-review.comment"))
        assertEquals(403, complete(i, "l.jones", "RESUBMIT").status)

        // RESUBMIT loops back to legal, which approves; LARGE notional takes the senior path.
        i = complete(i, "a.ops", "RESUBMIT")
        assertEquals("legal-review", i.map["currentStep"])
        i = complete(i, "l.jones", "APPROVE")
        assertEquals("desk-head-sign-off", i.map["currentStep"])
        assertEquals("LARGE", i.vars("size-check.outcome"))

        // Senior path rejoins: send-for-signature runs, then the instance parks on the signed event.
        i = complete(i, "d.head", "OK")
        assertEquals("signed", i.map["currentStep"])
        val envelope = i.vars("send-for-signature.envelopeId") as String

        assertEquals(404, call("POST", "/events/signed", body = mapOf("correlation" to mapOf("envelopeId" to "ENV-other"))).status)
        val woke = call("POST", "/events/signed", body = mapOf("correlation" to mapOf("envelopeId" to envelope), "payload" to mapOf("signer" to "c.acme")))
        assertEquals(202, woke.status)
        assertEquals(listOf(id), woke.map["woke"])

        i = call("GET", "/instances/$id")
        assertEquals("completed", i.map["status"])
        assertEquals("completed", i.map["endStatus"])
        assertEquals("c.acme", i.vars("signed.signer"))
        assertNotNull(i.vars("stamp.stampedAt"))

        // The event log tells the story, with actors.
        val completed = call("GET", "/instances/$id/events").list.filter { it["type"] == "step-completed" }
            .map { "${it["step"]}:${it["outcome"]}:${it["actor"]}" }
        assertEquals(
            listOf(
                "legal-review:REJECT:l.jones",
                "notify-requester:OK:service:NotifyRequester",
                "amend-draft:RESUBMIT:a.ops",
                "legal-review:APPROVE:l.jones",
                "size-check:LARGE:engine",
                "desk-head-sign-off:OK:d.head",
                "send-for-signature:SENT:service:SendForSignature",
                "signed:RECEIVED:event:signed",
                "stamp:OK:service:StampDocument",
            ),
            completed,
        )
    }

    @Test
    fun `E5 withdraw ends with its own status`() {
        var i = start(doc())
        i = complete(i, "l.jones", "REJECT")
        i = complete(i, "a.ops", "WITHDRAW")
        assertEquals("completed", i.map["status"])
        assertEquals("withdrawn", i.map["endStatus"])
    }

    @Test
    fun `E5 counter goes to the counterparty, scoped by RBAC`() {
        var i = start(doc(counterparty = "ACME"))
        i = complete(i, "l.jones", "COUNTER")
        assertEquals("negotiate", i.map["currentStep"])
        i = complete(i, "c.acme", "AGREED", mapOf("proposal" to "governing law: England"))
        assertEquals("legal-review", i.map["currentStep"])

        var other = start(doc(counterparty = "GLOBEX"))
        other = complete(other, "l.jones", "COUNTER")
        val res = complete(other, "c.acme", "AGREED")
        assertEquals(403, res.status)
        assertTrue((res.map["reason"] as String).contains("only in"), "${res.map["reason"]}")
    }

    @Test
    fun `E6 a timeout is just the TIMED_OUT outcome`() {
        var i = start(doc(notional = 1_000))
        val id = i.map["id"] as String
        i = complete(i, "l.jones", "APPROVE")
        assertEquals("signed", i.map["currentStep"])

        assertEquals(0, poller.fireDue())
        clock.advanceDays(8)
        assertTrue(poller.fireDue() >= 1)

        i = call("GET", "/instances/$id")
        assertEquals("expired", i.map["endStatus"])
        assertEquals("timer", call("GET", "/instances/$id/events").list.last { it["type"] == "step-completed" }["actor"])
    }

    @Test
    fun `E4 handler business outcome routes to its own end`() {
        val vars = mapOf("doc" to (doc(notional = 1_000)["doc"] as Map<*, *> + ("providerRejects" to true)))
        var i = start(vars)
        i = complete(i, "l.jones", "APPROVE")
        assertEquals("signature-failed", i.map["endStatus"])
    }

    @Test
    fun `E4 built-in http service task calls the third-party API`() {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        var received: String? = null
        var header: String? = null
        server.createContext("/hook") { ex ->
            received = ex.requestBody.readAllBytes().decodeToString()
            header = ex.requestHeaders.getFirst("X-Doc")
            val out = """{"accepted":true}""".toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.start()
        try {
            val i = start(
                mapOf("target" to mapOf("url" to "http://127.0.0.1:${server.address.port}/hook"), "payload" to mapOf("id" to "D-9")),
                name = "http-callout",
            )
            assertEquals("completed", i.map["status"], "${i.body}")
            assertEquals(200, i.vars("call.status"))
            assertEquals(true, i.vars("call.body.accepted"))
            assertEquals("""{"id":"D-9"}""", received)
            assertEquals("D-9", header)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `E4 http calls to hosts outside the allowlist raise an incident`() {
        val i = start(mapOf("target" to mapOf("url" to "http://example.com/hook"), "payload" to emptyMap<String, Any>()), name = "http-callout")
        assertEquals("failed", i.map["status"])
    }

    @Test
    fun `E4 a failing handler raises an incident, and retry resumes`() {
        FlakyHandler.failing = true
        val i = start(emptyMap(), name = "flaky")
        val id = i.map["id"] as String
        assertEquals("failed", i.map["status"])
        val incident = call("GET", "/instances/$id/events").list.last()
        assertEquals("incident", incident["type"])
        assertTrue(((incident["payload"] as Map<*, *>)["message"] as String).contains("after 2 attempt(s)"))

        FlakyHandler.failing = false
        val retried = call("POST", "/instances/$id/retry", "ops.admin")
        assertEquals("completed", retried.map["status"], "${retried.body}")
    }

    @Test
    fun `E1 definitions, graph and mermaid export are served`() {
        assertEquals(200, call("GET", "/health").status)
        val names = call("GET", "/definitions").list.map { it["name"] }
        assertTrue("confirmation-approval" in names)
        val graph = call("GET", "/definitions/confirmation-approval/graph").map
        @Suppress("UNCHECKED_CAST")
        val kinds = (graph["nodes"] as List<Map<String, Any?>>).associate { it["id"] to it["kind"] }
        assertEquals("user-task", kinds["legal-review"])
        assertEquals("event-wait", kinds["signed"])
        val mmd = call("GET", "/definitions/confirmation-approval/export?format=mermaid").body as String
        assertTrue(mmd.contains("flowchart LR"))
        assertEquals(404, call("GET", "/definitions/nope").status)
    }
}
