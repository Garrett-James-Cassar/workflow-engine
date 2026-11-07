package dev.workflow.app

import dev.workflow.app.adapters.LocalRbac
import dev.workflow.app.adapters.WorkflowRegistry
import dev.workflow.app.persistence.Store
import dev.workflow.app.runtime.ServiceRunner

import dev.workflow.core.Assignment
import dev.workflow.app.runtime.InstanceService
import dev.workflow.app.runtime.TaskService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * t.unit.b: sociable unit tests of the service layer, with real collaborators (Store, Engine,
 * WorkflowRegistry, ServiceRunner, LocalRbac) and fakes only at process edges (MutableClock).
 */
class InstanceServiceTest : ApiTest() {
    @Autowired lateinit var service: InstanceService
    @Autowired lateinit var tasks: TaskService

    @Test
    fun `start creates a waiting instance and returns its state`() {
        val state = service.start("confirmation-approval", doc(), "a.ops")
        assertEquals("waiting", state.status.name.lowercase())
        assertEquals("legal-review", state.currentStep)
    }

    @Test
    fun `get returns the latest state`() {
        val started = service.start("confirmation-approval", doc(), "a.ops")
        val fetched = service.get(UUID.fromString(started.id))
        assertEquals(started.currentStep, fetched.currentStep)
    }

    @Test
    fun `get throws for an unknown id`() {
        assertFailsWith<Exception> { service.get(UUID.randomUUID()) }
    }

    @Test
    fun `tasksFor returns open tasks the user is eligible for`() {
        service.start("confirmation-approval", doc(), "a.ops")
        val tasks = tasks.tasksFor("l.jones")
        assertTrue(tasks.any { it.step == "legal-review" }, "l.jones should see legal-review")
    }

    @Test
    fun `tasksFor is empty for a user with no matching role`() {
        service.start("confirmation-approval", doc(), "a.ops")
        assertEquals(emptyList(), tasks.tasksFor("p.maint"))
    }

    @Test
    fun `completeTask advances the instance`() {
        service.start("confirmation-approval", doc(), "a.ops")
        val task = tasks.tasksFor("l.jones").first { it.step == "legal-review" }
        val state = tasks.completeTask(task.id, "l.jones", "REJECT", mapOf("comment" to "fix clause 4"))
        assertEquals("amend-draft", state.currentStep)
    }

    @Test
    fun `completeTask throws for an ineligible user`() {
        service.start("confirmation-approval", doc(), "a.ops")
        val task = tasks.tasksFor("l.jones").first { it.step == "legal-review" }
        assertFailsWith<Exception> { tasks.completeTask(task.id, "a.ops", "APPROVE", emptyMap()) }
    }

    @Test
    fun `completeTask throws for an invalid outcome`() {
        service.start("confirmation-approval", doc(), "a.ops")
        val task = tasks.tasksFor("l.jones").first { it.step == "legal-review" }
        assertFailsWith<Exception> { tasks.completeTask(task.id, "l.jones", "MAYBE", emptyMap()) }
    }

    @Test
    fun `reassignTask moves a task to another team`() {
        service.start("confirmation-approval", doc(), "a.ops")
        val task = tasks.tasksFor("l.jones").first { it.step == "legal-review" }
        // ops.admin holds the 'ops' role needed by reassignLocked's holdsRole check
        tasks.reassignTask(task.id, "ops.admin", Assignment(Assignment.Type.ROLE, "compliance", mapOf("region" to "EU")))
    }

    @Test
    fun `retry re-enters a failed instance`() {
        FlakyHandler.failing = true
        val state = service.start("flaky", emptyMap(), "a.ops")
        assertEquals("failed", state.status.name.lowercase())

        FlakyHandler.failing = false
        val retried = service.retry(UUID.fromString(state.id), "i.handler")
        assertEquals("completed", retried.endStatus)
    }

    @Test
    fun `restartAt re-enters at the given user task`() {
        var state = service.start("confirmation-approval", doc(), "a.ops")
        val task = tasks.tasksFor("l.jones").first { it.step == "legal-review" }
        state = tasks.completeTask(task.id, "l.jones", "REJECT", mapOf("comment" to "fix"))
        assertEquals("amend-draft", state.currentStep)

        val restarted = service.restartAt(UUID.fromString(state.id), "e.admin", "legal-review")
        assertEquals("legal-review", restarted.currentStep)
    }
}