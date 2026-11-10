package dev.workflow.app

import dev.workflow.app.domain.AuditOutcome
import dev.workflow.app.ports.PortableStore
import dev.workflow.app.runtime.Audit
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The audit log (sec.6): one row per state-changing use case, denials, exports, imports and ticks; same transaction as the change; no values. */
class AuditTest : ApiTest() {
    @Autowired lateinit var audit: Audit
    @Autowired lateinit var tx: TransactionTemplate
    @Autowired lateinit var portable: PortableStore

    private fun rows(action: String, actor: String? = null): List<Map<String, Any?>> =
        call("GET", "/api/v1/audit?action=$action&limit=500${actor?.let { "&actor=$it" } ?: ""}").also { assertEquals(200, it.status, "${it.body}") }.list

    private fun count(action: String) = jdbc.sql("select count(*) from audit_log where action = :a").param("a", action).query(Long::class.java).single()

    @Test
    fun `E16 starting a run is audited with the actor, the resource and the names of the variables, never their values`() {
        val before = count("instances.start")
        val i = start(doc(counterparty = "SECRET-CPTY-9"))
        assertEquals(before + 1, count("instances.start"))
        val row = rows("instances.start", "a.ops").first()
        assertEquals("ok", row["outcome"])
        assertEquals("workflow:confirmation-approval", row["resource"])
        assertEquals("a.ops", row["actor"])
        assertTrue((row["changes"] as List<*>).any { it.toString().startsWith("vars.") }, "${row["changes"]}")
        val everything = jdbc.sql("select action || resource || changes || actor from audit_log").query(String::class.java).list().joinToString()
        assertTrue("SECRET-CPTY-9" !in everything && "75000000" !in everything, "no value may reach the audit log")
        assertNotNull(i.map["id"])
    }

    @Test
    fun `E16 completing and reassigning tasks are audited`() {
        val i = start(doc())
        val taskId = openTask(i)
        val reassigned = call("POST", "/api/v1/tasks/$taskId/reassign", "ops.admin", mapOf("to" to "j.smith", "toTeam" to false))
        assertEquals(200, reassigned.status, "${reassigned.body}")
        assertEquals("task:$taskId", rows("tasks.reassign", "ops.admin").first()["resource"])
        assertEquals(200, call("POST", "/api/v1/tasks/$taskId/complete", "j.smith", mapOf("outcome" to "APPROVE")).status)
        val done = rows("tasks.complete", "j.smith").first()
        assertEquals("ok", done["outcome"])
        assertEquals("task:$taskId", done["resource"])
    }

    @Test
    fun `E16 retry and restart are audited`() {
        FlakyHandler.failing = true
        val id = start(emptyMap(), name = "flaky").map["id"] as String
        FlakyHandler.failing = false
        assertEquals(200, call("POST", "/api/v1/instances/$id/retry", "i.handler").status)
        assertEquals("ok", rows("instances.retry", "i.handler").first { it["resource"] == "instance:$id" }["outcome"])
        val denied = call("POST", "/api/v1/instances/$id/restart", "a.ops", mapOf("step" to "x"))
        assertEquals(403, denied.status)
        assertTrue(rows("instances.restart", "a.ops").any { it["outcome"] == "denied" })
    }

    @Test
    fun `E16 events and timeouts are audited, a ticking job is the system actor`() {
        assertEquals(404, call("POST", "/api/v1/events/nobody-waits", ADMIN, mapOf("correlation" to emptyMap<String, String>())).status)
        assertEquals("failed", rows("events.send", ADMIN).first()["outcome"])

        var i = start(doc(notional = 1_000))
        val id = i.map["id"] as String
        i = complete(i, "l.jones", "APPROVE")
        assertEquals("signed", i.map["currentStep"])
        clock.advanceDays(8)
        assertTrue(poller.fireDue() >= 1)
        val tick = rows("timeouts.fire", "system:timeout-poller").first { it["resource"] == "instance:$id" }
        assertEquals("ok", tick["outcome"])
    }

    @Test
    fun `E16 an import is one row with counts, an export is recorded as resource export`() {
        val before = count("workflows.import")
        val doc = call("GET", "/api/v1/workflows/flaky:1/export").map.toMutableMap().apply { put("name", "audited-copy"); put("key", "audited-copy:1") }
        assertEquals(200, call("POST", "/api/v1/workflows/import", ADMIN, doc).status)
        assertEquals(before + 1, count("workflows.import"))
        val row = rows("workflows.import").first()
        assertEquals(listOf("created=1", "updated=0", "skipped=0", "conflicts=0"), row["changes"])
        assertEquals(200, call("GET", "/api/v1/instances/export").status)
        assertEquals(200, call("GET", "/api/v1/workflows/export").status)
        assertEquals(ADMIN, rows("instances.export").first()["actor"])
        assertEquals("workflows", rows("workflows.export").first()["resource"])
    }

    @Test
    fun `E16 the demo reset is audited`() {
        assertEquals(200, call("POST", "/demo/reset").status)
        assertEquals(ADMIN, rows("demo.reset").first()["actor"])
    }

    @Test
    fun `E16 a refused request writes a denied row with the permission as the action`() {
        val before = count("instances.start")
        assertEquals(403, call("POST", "/api/v1/definitions/confirmation-approval/instances", "r.viewer", mapOf("vars" to emptyMap<String, Any>())).status)
        val denied = rows("instances.start", "r.viewer").first()
        assertEquals("denied", denied["outcome"])
        assertTrue((denied["resource"] as String).startsWith("POST "), "${denied["resource"]}")
        assertEquals(before + 1, count("instances.start"))
    }

    @Test
    fun `E16 a change that rolls back leaves neither its row nor the audit row`() {
        val ctx = audit.context("e.admin")
        val rowsBefore = count("test.rollback")
        assertFailsWith<IllegalStateException> {
            audit.run(ctx, "test.rollback", "config:rollback") {
                portable.put("workflow", "rolled-back:1", "{}", clock.instant())
                error("the use case fails after writing")
            }
        }
        assertEquals(rowsBefore, count("test.rollback"))
        assertTrue("rolled-back:1" !in portable.docs("workflow"))
        audit.run(ctx, "test.rollback", "config:commit") { portable.put("workflow", "kept:1", "{}", clock.instant()) }
        assertEquals(rowsBefore + 1, count("test.rollback"))
        assertTrue("kept:1" in portable.docs("workflow"))
        jdbc.sql("delete from config_docs where doc_key = 'kept:1'").update() // the context is shared: leave no stray document
    }

    @Test
    fun `E16 a row written inside an outer transaction rolls back with it`() {
        val before = count("test.outer")
        assertFailsWith<IllegalStateException> {
            tx.execute {
                audit.record(audit.context("e.admin"), "test.outer", "x", AuditOutcome.OK)
                error("outer fails")
            }
        }
        assertEquals(before, count("test.outer"))
    }

    @Test
    fun `E16 a callerless record is the system actor and the caller header becomes via`() {
        assertEquals("system:ticker", audit.context(component = "ticker").actor)
        val res = raw("POST", "/api/v1/events/nobody-waits", ADMIN, "{}")
        assertEquals(404, res.status)
        val req = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:${env.getProperty("local.server.port")}/api/v1/events/via-check"))
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}")).header("Content-Type", "application/json")
            .header("X-User", ADMIN).header("X-Fleet-Caller", "workflow-studio").build()
        java.net.http.HttpClient.newHttpClient().send(req, java.net.http.HttpResponse.BodyHandlers.ofString())
        assertEquals("workflow-studio", rows("events.send").first { it["resource"] == "event:via-check" }["via"])
    }

    @Test
    fun `E16 the audit log is read with filters and paging, exported, and only by those who may`() {
        start(doc())
        assertEquals(1, call("GET", "/api/v1/audit?limit=1").list.size)
        val older = call("GET", "/api/v1/audit?limit=1&offset=1").list.single()
        assertTrue(older["id"] != call("GET", "/api/v1/audit?limit=1").list.single()["id"])
        assertTrue(call("GET", "/api/v1/audit?actor=nobody").list.isEmpty())
        assertEquals(200, call("GET", "/api/v1/audit?from=2000-01-01T00:00:00Z&to=2999-01-01T00:00:00Z&resource=workflow:confirmation-approval").status)
        assertEquals(200, call("GET", "/api/v1/audit", "r.viewer").status)
        assertEquals(403, call("GET", "/api/v1/audit", "a.ops").status)
        assertEquals(403, call("GET", "/api/v1/audit/export", "r.viewer").status)
        val export = raw("GET", "/api/v1/audit/export")
        assertEquals(200, export.status)
        assertTrue(export.text.contains("\"kind\":\"audit\""), export.text.take(200))
        assertEquals("audit.export", call("GET", "/api/v1/audit?action=audit.export").list.first()["action"])
        for (method in listOf("PUT", "PATCH", "DELETE", "POST")) assertTrue(call(method, "/api/v1/audit", ADMIN, emptyMap<String, String>()).status in setOf(404, 405), method)
    }
}
