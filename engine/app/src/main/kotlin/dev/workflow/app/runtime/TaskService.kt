package dev.workflow.app.runtime

import dev.workflow.app.domain.BadRequestException
import dev.workflow.app.domain.ConflictException
import dev.workflow.app.domain.ForbiddenException
import dev.workflow.app.domain.NotFoundException
import dev.workflow.app.domain.RbacDecision
import dev.workflow.app.domain.TaskDecision
import dev.workflow.app.domain.TaskRow
import dev.workflow.app.ports.RbacPort
import dev.workflow.app.ports.WorkflowStore
import dev.workflow.app.security.Authorizer
import dev.workflow.app.security.Permissions
import dev.workflow.app.telemetry.UseCaseSpans
import dev.workflow.core.Assignment
import dev.workflow.core.Engine
import dev.workflow.core.EngineEvent
import dev.workflow.core.InstanceState
import dev.workflow.core.Step
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

/** Who may act on which user task, and what happens when they do: eligibility, reassigning and completing. */
@Service
class TaskService(
    private val store: WorkflowStore,
    private val engine: Engine,
    private val transitions: Transitions,
    private val rbac: RbacPort,
    private val authorizer: Authorizer,
    private val spans: UseCaseSpans,
    private val clock: Clock,
) {
    /** A user task is for the person it names; a team task is for anyone holding the team's role in the task's scope. */
    fun eligibility(task: TaskRow, user: String): RbacDecision = when (task.assignment.type) {
        Assignment.Type.USER -> assignedTo(task, user)
        Assignment.Type.ROLE -> rbac.holdsRole(user, task.assignment.name, task.assignment.scope)
    }

    private fun assignedTo(task: TaskRow, user: String): RbacDecision =
        if (task.assignment.name == user) RbacDecision(true, "assigned to '$user'") else RbacDecision(false, "task is assigned to user '${task.assignment.name}', not '$user'")

    fun tasksFor(user: String): List<TaskRow> = store.openTasks().filter { eligibility(it, user).allowed }

    /** The open tasks the caller may read, in the scopes they hold the read permission for. */
    fun readableTasks(login: String): List<TaskRow> = store.openTasks().filter { rbac.check(login, Permissions.TASKS_READ, it.assignment.scope).allowed }

    /**
     * Move an open task to another user or team. Separately permissioned: the caller must hold the
     * task's `reassignableBy` role (ops by default). Recorded in the event log with both holders.
     */
    fun reassignTask(taskId: UUID, user: String, to: Assignment): InstanceState = spans.run("workflow.task.reassign") {
        val task = openTask(taskId)
        authorizer.require(user, Permissions.TASKS_REASSIGN, task.assignment.scope)
        transitions.inTx { reassignLocked(task, user, to) }
    }

    private fun reassignLocked(task: TaskRow, user: String, to: Assignment): InstanceState {
        val state = transitions.lock(task.instanceId)
        requireOpen(task)
        val step = transitions.definitionOf(state).step(task.step) as Step.UserTask
        requireMayReassign(task, user, step)
        if (to.name.isBlank()) throw BadRequestException("reassign target needs a user or team name")
        store.reassignTask(task.id, to)
        store.appendEvents(UUID.fromString(state.id), listOf(reassigned(task, user, to)))
        return state
    }

    private fun requireMayReassign(task: TaskRow, user: String, step: Step.UserTask) {
        val permitted = step.reassignableBy ?: throw ForbiddenException("task ${task.step} cannot be reassigned", "this task is not reassignable")
        val decision = rbac.holdsRole(user, permitted.name, permitted.scope)
        if (!decision.allowed) throw ForbiddenException("'$user' may not reassign task ${task.id}", "reassigning needs role '${permitted.name}': ${decision.reason}")
    }

    private fun reassigned(task: TaskRow, user: String, to: Assignment) = EngineEvent(
        "task-reassigned", task.step, actor = user, at = clock.instant(),
        payload = mapOf("taskId" to task.id.toString(), "from" to holder(task.assignment), "to" to holder(to) + ("scope" to to.scope)),
    )

    private fun holder(a: Assignment) = mapOf("type" to a.type.name.lowercase(), "name" to a.name)

    fun completeTask(taskId: UUID, user: String, outcome: String, form: Map<String, Any?>): InstanceState = spans.run("workflow.task.complete") {
        val task = store.task(taskId) ?: throw NotFoundException("no task $taskId")
        val decision = eligibility(task, user)
        if (!decision.allowed) throw ForbiddenException("'$user' may not complete task $taskId", decision.reason)
        // A team task needs the completing permission in the task's scope; a person's own task needs none.
        if (task.assignment.type == Assignment.Type.ROLE) authorizer.require(user, Permissions.TASKS_COMPLETE, task.assignment.scope)
        transitions.inTx { completeLocked(task, TaskDecision(outcome, form, user, clock.instant())) }
    }

    private fun completeLocked(task: TaskRow, decision: TaskDecision): InstanceState {
        val state = transitions.lock(task.instanceId)
        requireOpen(task)
        val def = transitions.definitionOf(state)
        val step = def.step(task.step) as Step.UserTask
        if (decision.outcome !in step.outcomes) throw BadRequestException("outcome '${decision.outcome}' is not one of ${step.outcomes}")
        FormValidation.check(step, decision.form)
        store.closeTask(task.id, decision)
        return transitions.apply(def, engine.complete(def, state, task.step, decision.outcome, decision.form, decision.by))
    }

    private fun openTask(taskId: UUID): TaskRow = store.task(taskId) ?: throw NotFoundException("no task $taskId")

    private fun requireOpen(task: TaskRow) {
        if (task.status != "OPEN") throw ConflictException("task ${task.id} is already ${task.status}")
    }
}
