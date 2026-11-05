package dev.workflow.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Task scope (user vs team) and the separately permissioned reassign operation. */
class ReassignE2ETest : ApiTest() {

    @Test
    fun `E5 every open task says whether it is user-scoped or team-scoped, and who can reassign it`() {
        val i = start(doc())
        val team = i.map["openTasks"].let { (it as List<*>).single() as Map<*, *> }
        assertEquals("team", team["scopedTo"])
        assertEquals("ops", team["reassignableBy"])

        val rejected = complete(i, "l.jones", "REJECT")
        val user = (rejected.map["openTasks"] as List<*>).single() as Map<*, *>
        assertEquals("user", user["scopedTo"])
        assertEquals("ops", user["reassignableBy"])
    }

    @Test
    fun `E5 only the reassign role can move a task`() {
        val i = start(doc())
        val taskId = openTask(i)
        val refused = call("POST", "/tasks/$taskId/reassign", "l.jones", mapOf("to" to "d.head"))
        assertEquals(403, refused.status)
        assertTrue((refused.map["reason"] as String).contains("'ops'"), "${refused.map["reason"]}")
    }

    @Test
    fun `E5 ops reassigns a team task to a user, who then completes it, and the old holder loses it`() {
        val i = start(doc())
        val id = i.map["id"] as String
        val taskId = openTask(i)

        val moved = call("POST", "/tasks/$taskId/reassign", "ops.admin", mapOf("to" to "j.smith", "toTeam" to false))
        assertEquals(200, moved.status, "${moved.body}")
        val task = (moved.map["openTasks"] as List<*>).single() as Map<*, *>
        assertEquals("user", task["scopedTo"])
        assertEquals("j.smith", (task["assignee"] as Map<*, *>)["name"])

        assertEquals(403, complete(i, "l.jones", "APPROVE").status, "legal lost the task")
        assertEquals(200, call("POST", "/tasks/$taskId/complete", "j.smith", mapOf("outcome" to "APPROVE")).status)

        val history = call("GET", "/instances/$id/events").list.single { it["type"] == "task-reassigned" }
        assertEquals("ops.admin", history["actor"])
        val payload = history["payload"] as Map<*, *>
        assertEquals("ROLE", ((payload["from"] as Map<*, *>)["type"] as String).uppercase())
        assertEquals("j.smith", (payload["to"] as Map<*, *>)["name"])
    }

    @Test
    fun `E5 a task can be reassigned to a team, with its scope`() {
        val i = start(doc(counterparty = "ACME"))
        val taskId = openTask(complete(i, "l.jones", "COUNTER"))
        val moved = call(
            "POST", "/tasks/$taskId/reassign", "ops.admin",
            mapOf("to" to "compliance", "toTeam" to true, "scope" to mapOf("region" to "EU")),
        )
        assertEquals(200, moved.status, "${moved.body}")
        val task = (moved.map["openTasks"] as List<*>).single() as Map<*, *>
        assertEquals("team", task["scopedTo"])
        assertEquals(mapOf("region" to "EU"), (task["assignee"] as Map<*, *>)["scope"])
    }

    @Test
    fun `E5 a finished task cannot be reassigned`() {
        val i = start(doc())
        val taskId = openTask(i)
        complete(i, "l.jones", "APPROVE")
        assertEquals(409, call("POST", "/tasks/$taskId/reassign", "ops.admin", mapOf("to" to "d.head")).status)
    }
}
