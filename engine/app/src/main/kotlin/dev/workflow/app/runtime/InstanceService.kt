package dev.workflow.app.runtime

import dev.workflow.app.domain.AuditContext
import dev.workflow.app.domain.AuditOutcome
import dev.workflow.app.domain.BadRequestException
import dev.workflow.app.domain.ForbiddenException
import dev.workflow.app.domain.ConflictException
import dev.workflow.app.domain.NotFoundException
import dev.workflow.app.domain.WaitRow
import dev.workflow.app.ports.WorkflowDefinitions
import dev.workflow.app.ports.WorkflowStore
import dev.workflow.app.security.Authorizer
import dev.workflow.app.security.Permissions
import dev.workflow.app.telemetry.UseCaseSpans
import dev.workflow.core.Engine
import dev.workflow.core.InstanceState
import dev.workflow.core.Step
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

private const val RECEIVED = "RECEIVED"
private const val TIMED_OUT = "TIMED_OUT"

/**
 * Starts, restarts, retries and wakes runs. Every operation locks the instance row in one transaction
 * (see [Transitions]). Task operations live in [TaskService].
 */
@Service
class InstanceService(
    private val defs: WorkflowDefinitions,
    private val store: WorkflowStore,
    private val engine: Engine,
    private val transitions: Transitions,
    private val authorizer: Authorizer,
    private val spans: UseCaseSpans,
    private val audit: Audit,
    private val clock: Clock,
) {
    fun start(name: String, vars: Map<String, Any?>, login: String): InstanceState = spans.run("workflow.instance.start", "workflow" to name) {
        audit.run(audit.context(login), "instances.start", "workflow:$name", listOf("instance") + vars.keys.map { "vars.$it" }, { authorizer.require(login, Permissions.INSTANCES_START) }) {
            transitions.inTx { startLocked(name, vars, login) }
        }
    }

    private fun startLocked(name: String, vars: Map<String, Any?>, login: String): InstanceState {
        val def = defs.latest(name)
        val t = engine.start(def, UUID.randomUUID().toString(), vars, login)
        store.insertInstance(t.state.copy(currentStep = null))
        return transitions.apply(def, t)
    }

    fun get(id: UUID): InstanceState = store.instance(id) ?: throw NotFoundException("no instance $id")

    /**
     * Restart an instance at any user task. Needs the workflow-support role. Open tasks and waits are
     * cancelled, and the instance re-enters [stepId] with its vars and history kept.
     */
    fun restartAt(instanceId: UUID, user: String, stepId: String): InstanceState = spans.run("workflow.instance.restart", "step" to stepId) {
        audit.run(audit.context(user), "instances.restart", "instance:$instanceId", listOf("currentStep", "status"), { authorizer.require(user, Permissions.INSTANCES_RESTART) }) {
            transitions.inTx { restartLocked(instanceId, user, stepId) }
        }
    }

    private fun restartLocked(instanceId: UUID, user: String, stepId: String): InstanceState {
        val state = transitions.lock(instanceId)
        val def = transitions.definitionOf(state)
        if (!def.hasStep(stepId) || def.step(stepId) !is Step.UserTask) {
            throw BadRequestException("restart at a user task; '$stepId' is not one in ${def.name}")
        }
        store.cancelOpenWork(UUID.fromString(state.id))
        return transitions.apply(def, engine.restartAt(def, state, stepId, user))
    }

    fun expiredWaits(): List<WaitRow> = store.expiredWaits(clock.instant())

    /** Like [receiveEvent], but 404 when nothing was waiting for the event. */
    fun deliverEvent(event: String, correlation: Map<String, String>, payload: Map<String, Any?>, login: String): List<UUID> =
        receiveEvent(event, correlation, payload, login).ifEmpty {
            audit.record(audit.context(login), "events.send", "event:$event", AuditOutcome.FAILED)
            throw NotFoundException("no instance is waiting for '$event' with that correlation")
        }

    /** Wake every instance parked on [event] whose correlation matches. Returns the woken instance ids. */
    fun receiveEvent(event: String, correlation: Map<String, String>, payload: Map<String, Any?>, login: String): List<UUID> =
        spans.run("workflow.event.receive", "event" to event) {
            val ctx = audit.context(login)
            authorizeEvent(ctx, event, login)
            val matching = store.openWaits(event).filter { it.correlationKey == null || correlation[it.correlationKey] == it.correlationValue }
            matching.mapNotNull { wait -> transitions.inTx { wake(wait, payload, event)?.also { audit.record(ctx, "events.send", "instance:$it", AuditOutcome.OK, listOf("wait", "status") + payload.keys.map { k -> "payload.$k" }) } } }
        }

    private fun authorizeEvent(ctx: AuditContext, event: String, login: String) = try {
        authorizer.require(login, Permissions.EVENTS_SEND)
    } catch (e: ForbiddenException) {
        audit.record(ctx, "events.send", "event:$event", AuditOutcome.DENIED)
        throw e
    }

    /** Re-check under the lock: another request may have consumed the wait. */
    private fun wake(wait: WaitRow, payload: Map<String, Any?>, event: String): UUID? {
        val state = transitions.lock(wait.instanceId)
        if (store.openWaitsFor(wait.instanceId).none { it.id == wait.id }) return null
        store.closeWait(wait.id)
        val def = transitions.definitionOf(state)
        transitions.apply(def, engine.complete(def, state, wait.step, RECEIVED, payload, "event:$event"))
        return wait.instanceId
    }

    /**
     * Fire an event wait's timeout: its TIMED_OUT outcome. [login] is the caller on the HTTP route; null is
     * the in-process poller, which is the system and needs no permission.
     */
    fun fireTimeout(instanceId: UUID, step: String, login: String? = null): InstanceState =
        spans.run("workflow.timeout.fire", "step" to step) {
            audit.run(audit.context(login, "timeout-poller"), "timeouts.fire", "instance:$instanceId", listOf("status", "wait"), { login?.let { authorizer.require(it, Permissions.INSTANCES_FIRE_TIMEOUT) } }) {
                transitions.inTx { fireTimeoutLocked(instanceId, step) }
            }
        }

    private fun fireTimeoutLocked(instanceId: UUID, step: String): InstanceState {
        val state = transitions.lock(instanceId)
        val wait = store.openWaitsFor(instanceId).firstOrNull { it.step == step && it.expiresAt != null }
            ?: throw ConflictException("instance $instanceId has no open timed wait at '$step'")
        store.closeWait(wait.id)
        val def = transitions.definitionOf(state)
        return transitions.apply(def, engine.complete(def, state, step, TIMED_OUT, emptyMap(), "timer"))
    }

    fun retry(instanceId: UUID, login: String?): InstanceState = spans.run("workflow.instance.retry") {
        audit.run(audit.context(login, "retry"), "instances.retry", "instance:$instanceId", listOf("status", "currentStep"), { login?.let { authorizer.require(it, Permissions.INSTANCES_RETRY) } }) {
            transitions.inTx { retryLocked(instanceId, login) }
        }
    }

    private fun retryLocked(instanceId: UUID, login: String?): InstanceState {
        val state = transitions.lock(instanceId)
        val def = transitions.definitionOf(state)
        return transitions.apply(def, engine.retry(def, state, login))
    }
}
