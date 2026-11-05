package dev.workflow.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The workflow-support role can restart a workflow at any user task. */
class RestartE2ETest : ApiTest() {

    private fun restart(instance: Res, user: String, step: String): Res =
        call("POST", "/instances/${instance.map["id"]}/restart", user, mapOf("step" to step))

    @Test
    fun `E5 only the workflow-support role can restart a workflow`() {
        val i = start(doc())
        val refused = restart(i, "a.ops", "amend-draft")
        assertEquals(403, refused.status)
        assertTrue((refused.map["reason"] as String).contains("workflow-support"), "${refused.map["reason"]}")
        assertEquals("legal-review", call("GET", "/instances/${i.map["id"]}").map["currentStep"], "nothing moved")
    }

    @Test
    fun `E5 support restarts at a later user task, cancelling the open work and keeping vars`() {
        val i = start(doc())
        val id = i.map["id"]
        val oldTask = openTask(i)

        val moved = restart(i, "w.support", "amend-draft")
        assertEquals(200, moved.status, "${moved.body}")
        assertEquals("amend-draft", moved.map["currentStep"])
        assertEquals("waiting", moved.map["status"])
        val task = (moved.map["openTasks"] as List<*>).single() as Map<*, *>
        assertEquals("amend-draft", task["step"])
        assertEquals("a.ops", (task["assignee"] as Map<*, *>)["name"], "assignee comes from vars, as usual")
        assertEquals("a.ops", call("GET", "/instances/$id").vars("doc.requester"), "vars are kept")

        assertEquals(409, call("POST", "/tasks/$oldTask/complete", "l.jones", mapOf("outcome" to "APPROVE")).status,
            "the old legal task was cancelled")
        val restarted = call("GET", "/instances/$id/events").list.single { it["type"] == "restarted" }
        assertEquals("w.support", restarted["actor"])
        assertEquals("legal-review", (restarted["payload"] as Map<*, *>)["from"])
    }

    @Test
    fun `E5 support can reopen a finished workflow at a user task`() {
        val i = start(doc())
        val ended = complete(complete(i, "l.jones", "REJECT"), "a.ops", "WITHDRAW")
        assertEquals("withdrawn", ended.map["endStatus"])

        val reopened = restart(ended, "w.support", "legal-review")
        assertEquals(200, reopened.status, "${reopened.body}")
        assertEquals("waiting", reopened.map["status"])
        assertEquals("legal-review", reopened.map["currentStep"])
    }

    @Test
    fun `E5 restart only targets user tasks`() {
        val i = start(doc())
        assertEquals(400, restart(i, "w.support", "notify-requester").status)
        assertEquals(400, restart(i, "w.support", "no-such-step").status)
    }
}
