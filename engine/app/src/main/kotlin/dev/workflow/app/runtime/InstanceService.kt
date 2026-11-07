package dev.workflow.app.runtime

import dev.workflow.app.observability.UseCaseSpans
import dev.workflow.app.persistence.Store
import dev.workflow.app.persistence.TaskRow
import dev.workflow.app.persistence.WaitRow
import dev.workflow.app.rbac.RbacDecision
import dev.workflow.app.rbac.RbacPort
import dev.workflow.app.security.Authorizer
import dev.workflow.app.security.Permissions
import dev.workflow.app.registry.WorkflowRegistry
import dev.workflow.core.Assignment
import dev.workflow.core.Definition
import dev.workflow.core.Effect
import dev.workflow.core.Engine
import dev.workflow.core.FieldType
import dev.workflow.core.IllegalTransitionException
import dev.workflow.core.InstanceState
import dev.workflow.core.ServiceContext
import dev.workflow.core.Step
import dev.workflow.core.Transition
import dev.workflow.core.Vars
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.UUID

/**
 * Applies engine transitions and carries out their effects: persists state and events, opens user
 * tasks and waits, and runs service tasks until the instance parks or ends.
 *
 * Every operation locks the instance row in one transaction. v1 runs service handlers inside that
 * transaction too: simple and consistent, at the cost of holding the lock during the call.
 */
@Service
class InstanceService(
    private val registry: WorkflowRegistry,
    private val store: Store,
    private val engine: Engine,
    private val services: ServiceRunner,
    private val rbac: RbacPort,
    private val authorizer: Authorizer,
    private val tx: TransactionTemplate,
    private val clock: Clock,
    private val spans: UseCaseSpans,
) {
    fun start(name: String, vars: Map<String, Any?>, login: String): InstanceState = spans.run("workflow.instance.start", "workflow" to name) {
        authorizer.require(login, Permissions.INSTANCES_START)
        inTx {
            val def = registry.latest(name)
            val id = UUID.randomUUID()
            val t = engine.start(def, id.toString(), vars, login)
            store.insertInstance(t.state.copy(currentStep = null))
            apply(def, t)
        }
    }

    fun get(id: UUID): InstanceState = store.instance(id) ?: throw NotFoundException("no instance $id")

    /** A user task is for the person it names; a team task is for anyone holding the team's role in the task's scope. */
    fun eligibility(task: TaskRow, user: String): RbacDecision = when (task.assignment.type) {
        Assignment.Type.USER ->
            if (task.assignment.name == user) RbacDecision(true, "assigned to '$user'")
            else RbacDecision(false, "task is assigned to user '${task.assignment.name}', not '$user'")
        Assignment.Type.ROLE -> rbac.holdsRole(user, task.assignment.name, task.assignment.scope)
    }

    fun tasksFor(user: String): List<TaskRow> = store.openTasks().filter { eligibility(it, user).allowed }

    /**
     * Move an open task to another user or team. Separately permissioned: the caller must hold the
     * task's `reassignableBy` role (ops by default). Recorded in the event log with both holders.
     */
    /**
     * Restart an instance at any user task. Needs the workflow-support role. Open tasks and waits are
     * cancelled, and the instance re-enters [stepId] with its vars and history kept.
     */
    fun restartAt(instanceId: UUID, user: String, stepId: String): InstanceState = spans.run("workflow.instance.restart", "step" to stepId) {
        authorizer.require(user, Permissions.INSTANCES_RESTART)
        inTx { restartLocked(instanceId, user, stepId) }
    }

    private fun restartLocked(instanceId: UUID, user: String, stepId: String): InstanceState {
        val state = lock(instanceId)
        val def = definitionOf(state)
        if (!def.hasStep(stepId) || def.step(stepId) !is Step.UserTask) {
            throw BadRequestException("restart at a user task; '$stepId' is not one in ${def.name}")
        }
        store.cancelOpenWork(UUID.fromString(state.id))
        return apply(def, engine.restartAt(def, state, stepId, user))
    }

    fun reassignTask(taskId: UUID, user: String, to: Assignment): InstanceState = spans.run("workflow.task.reassign") {
        val task = store.task(taskId) ?: throw NotFoundException("no task $taskId")
        authorizer.require(user, Permissions.TASKS_REASSIGN, task.assignment.scope)
        inTx { reassignLocked(taskId, task, user, to) }
    }

    private fun reassignLocked(taskId: UUID, task: TaskRow, user: String, to: Assignment): InstanceState {
        val state = lock(task.instanceId)
        if (task.status != "OPEN") throw ConflictException("task $taskId is already ${task.status}")
        val def = definitionOf(state)
        val step = def.step(task.step) as Step.UserTask
        val permitted = step.reassignableBy
            ?: throw ForbiddenException("task ${task.step} cannot be reassigned", "this task is not reassignable")
        val decision = rbac.holdsRole(user, permitted.name, permitted.scope)
        if (!decision.allowed) {
            throw ForbiddenException("'$user' may not reassign task $taskId", "reassigning needs role '${permitted.name}': ${decision.reason}")
        }
        if (to.name.isBlank()) throw BadRequestException("reassign target needs a user or team name")

        store.reassignTask(taskId, to)
        store.appendEvents(
            UUID.fromString(state.id),
            listOf(
                dev.workflow.core.EngineEvent(
                    "task-reassigned", task.step, actor = user,
                    payload = mapOf(
                        "taskId" to taskId.toString(),
                        "from" to mapOf("type" to task.assignment.type.name.lowercase(), "name" to task.assignment.name),
                        "to" to mapOf("type" to to.type.name.lowercase(), "name" to to.name, "scope" to to.scope),
                    ),
                    at = clock.instant(),
                ),
            ),
        )
        return state
    }

    fun completeTask(taskId: UUID, user: String, outcome: String, form: Map<String, Any?>): InstanceState = spans.run("workflow.task.complete") {
        val task = store.task(taskId) ?: throw NotFoundException("no task $taskId")
        val decision = eligibility(task, user)
        if (!decision.allowed) throw ForbiddenException("'$user' may not complete task $taskId", decision.reason)
        // A team task needs the completing permission in the task's scope; a person's own task needs none.
        if (task.assignment.type == Assignment.Type.ROLE) authorizer.require(user, Permissions.TASKS_COMPLETE, task.assignment.scope)
        inTx { completeLocked(taskId, task, user, outcome, form) }
    }

    private fun completeLocked(taskId: UUID, task: TaskRow, user: String, outcome: String, form: Map<String, Any?>): InstanceState {
        val state = lock(task.instanceId)
        if (task.status != "OPEN") throw ConflictException("task $taskId is already ${task.status}")

        val def = definitionOf(state)
        val step = def.step(task.step) as Step.UserTask
        if (outcome !in step.outcomes) throw BadRequestException("outcome '$outcome' is not one of ${step.outcomes}")
        validateForm(step, form)

        store.closeTask(taskId, outcome, form, user, clock.instant())
        return apply(def, engine.complete(def, state, task.step, outcome, form, user))
    }

    /** Wake every instance parked on [event] whose correlation matches. Returns the woken instance ids. */
    fun receiveEvent(event: String, correlation: Map<String, String>, payload: Map<String, Any?>, login: String): List<UUID> = spans.run("workflow.event.receive", "event" to event) {
        authorizer.require(login, Permissions.EVENTS_SEND)
        val matching = store.openWaits(event).filter { it.correlationKey == null || correlation[it.correlationKey] == it.correlationValue }
        matching.mapNotNull { wait ->
            inTx {
                val state = lock(wait.instanceId)
                // Re-check under the lock: another request may have consumed the wait.
                val stillOpen = store.openWaitsFor(wait.instanceId).any { it.id == wait.id }
                if (!stillOpen) return@inTx null
                store.closeWait(wait.id)
                val def = definitionOf(state)
                apply(def, engine.complete(def, state, wait.step, RECEIVED, payload, "event:$event"))
                wait.instanceId
            }
        }
    }

    /**
     * Fire an event wait's timeout: its TIMED_OUT outcome. [login] is the caller on the HTTP route; null is
     * the in-process poller, which is the system and needs no permission.
     */
    fun fireTimeout(instanceId: UUID, step: String, login: String? = null): InstanceState = spans.run("workflow.timeout.fire", "step" to step) {
        login?.let { authorizer.require(it, Permissions.INSTANCES_FIRE_TIMEOUT) }
        inTx { fireTimeoutLocked(instanceId, step) }
    }

    private fun fireTimeoutLocked(instanceId: UUID, step: String): InstanceState {
        val state = lock(instanceId)
        val wait = store.openWaitsFor(instanceId).firstOrNull { it.step == step && it.expiresAt != null }
            ?: throw ConflictException("instance $instanceId has no open timed wait at '$step'")
        store.closeWait(wait.id)
        val def = definitionOf(state)
        return apply(def, engine.complete(def, state, step, TIMED_OUT, emptyMap(), "timer"))
    }

    fun expiredWaits(): List<WaitRow> = store.expiredWaits(clock.instant())

    fun retry(instanceId: UUID, login: String?): InstanceState = spans.run("workflow.instance.retry") {
        login?.let { authorizer.require(it, Permissions.INSTANCES_RETRY) }
        inTx {
            val state = lock(instanceId)
            val def = definitionOf(state)
            apply(def, engine.retry(def, state, login))
        }
    }

    private fun apply(def: Definition, transition: Transition): InstanceState {
        var t = transition
        while (true) {
            val instanceId = UUID.fromString(t.state.id)
            store.saveInstance(t.state)
            store.appendEvents(instanceId, t.events)
            when (val effect = t.effects.singleOrNull()) {
                null, is Effect.Ended -> return t.state
                is Effect.CreateUserTask -> {
                    store.insertTask(instanceId, effect.stepId, effect.assignment)
                    return t.state
                }
                is Effect.ParkForEvent -> {
                    store.insertWait(instanceId, effect.stepId, effect.event, effect.correlationKey, effect.correlationValue, effect.expiresAt)
                    return t.state
                }
                is Effect.RunService -> {
                    val step = def.step(effect.stepId) as Step.ServiceTask
                    val ctx = ServiceContext(t.state.id, step.id, Vars(t.state.vars))
                    t = when (val result = services.run(step, ctx)) {
                        is ServiceOutcome.Ok ->
                            if (result.outcome in step.outcomes) {
                                engine.complete(def, t.state, step.id, result.outcome, result.output, result.actor)
                            } else {
                                engine.fail(t.state, step.id, "handler returned unknown outcome '${result.outcome}'")
                            }
                        is ServiceOutcome.Failed -> engine.fail(t.state, step.id, result.message)
                    }
                }
            }
        }
    }

    private fun validateForm(step: Step.UserTask, form: Map<String, Any?>) {
        val fields = step.form.associateBy { it.name }
        val unknown = form.keys - fields.keys
        if (unknown.isNotEmpty()) throw BadRequestException("unknown form field(s) $unknown; expected ${fields.keys}")
        for (field in step.form) {
            val value = form[field.name]
            if (value == null) {
                if (field.required) throw BadRequestException("form field '${field.name}' is required")
                continue
            }
            val ok = when (field.type) {
                FieldType.TEXT -> value is String
                FieldType.NUMBER -> value is Number
                FieldType.BOOLEAN -> value is Boolean
            }
            if (!ok) throw BadRequestException("form field '${field.name}' must be ${field.type.name.lowercase()}")
        }
    }

    private fun lock(id: UUID): InstanceState = store.lockInstance(id) ?: throw NotFoundException("no instance $id")

    private fun definitionOf(state: InstanceState): Definition = registry.get(state.definition, state.version)

    private fun <T> inTx(block: () -> T): T = try {
        tx.execute { block() } as T
    } catch (e: IllegalTransitionException) {
        throw ConflictException(e.message ?: "illegal transition")
    }

    private companion object {
        const val RECEIVED = "RECEIVED"
        const val TIMED_OUT = "TIMED_OUT"
    }
}
