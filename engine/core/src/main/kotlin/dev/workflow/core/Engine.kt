package dev.workflow.core

import java.time.Clock
import java.time.Instant

enum class InstanceStatus {
    /** A service task is due to run (the adapter executes [Effect.RunService]). */
    RUNNING,

    /** Parked on a user task or an event wait. */
    WAITING,

    /** Reached an end; see [InstanceState.endStatus]. */
    COMPLETED,

    /** An incident: a handler kept failing or a var was missing. `retry` re-enters [InstanceState.currentStep]. */
    FAILED,
}

data class InstanceState(
    val id: String,
    val definition: String,
    val version: Int,
    val status: InstanceStatus,
    val currentStep: String?,
    val endStatus: String?,
    val vars: Map<String, Any?>,
)

/** The resolved assignee of a user task: a username or a role within a scope. */
data class Assignment(val type: Type, val name: String, val scope: Map<String, String>) {
    enum class Type { USER, ROLE }
}

/** What the adapter must do after a transition. */
sealed interface Effect {
    data class RunService(val stepId: String) : Effect

    data class CreateUserTask(val stepId: String, val assignment: Assignment) : Effect

    data class ParkForEvent(
        val stepId: String,
        val event: String,
        val correlationKey: String?,
        val correlationValue: String?,
        val expiresAt: Instant?,
    ) : Effect

    data class Ended(val status: String) : Effect
}

/** Append-only history entry. */
data class EngineEvent(
    val type: String,
    val step: String?,
    val outcome: String? = null,
    val actor: String? = null,
    val payload: Map<String, Any?> = emptyMap(),
    val at: Instant,
)

data class Transition(val state: InstanceState, val effects: List<Effect>, val events: List<EngineEvent>)

class IllegalTransitionException(message: String) : RuntimeException(message)

/**
 * The whole engine: pure functions from (definition, state, trigger) to (state', effects, events).
 * No I/O, so every path is unit-testable and history can be replayed.
 */
class Engine(private val clock: Clock = Clock.systemUTC()) {

    fun start(def: Definition, instanceId: String, vars: Map<String, Any?>, actor: String?): Transition {
        val state = InstanceState(instanceId, def.name, def.version, InstanceStatus.RUNNING, null, null, vars)
        val started = EngineEvent("instance-started", null, actor = actor, payload = mapOf("vars" to vars), at = now())
        return enter(def, state, Target.ToStep(def.first.id), listOf(started))
    }

    /** Step [stepId] produced [outcome]: record it in vars and follow its route. */
    fun complete(
        def: Definition,
        state: InstanceState,
        stepId: String,
        outcome: String,
        output: Map<String, Any?>,
        actor: String?,
    ): Transition {
        if (state.status == InstanceStatus.COMPLETED) throw IllegalTransitionException("instance ${state.id} has ended")
        if (state.currentStep != stepId) {
            throw IllegalTransitionException("instance ${state.id} is at '${state.currentStep}', not '$stepId'")
        }
        val step = def.step(stepId)
        if (outcome !in step.outcomes) {
            throw IllegalTransitionException("step '$stepId' has no outcome '$outcome' (expected one of ${step.outcomes})")
        }
        val recorded = state.copy(vars = state.vars + (stepId to record(output, outcome, actor)))
        val event = EngineEvent("step-completed", stepId, outcome, actor, output, now())
        return enter(def, recorded, def.resolve(stepId, outcome), listOf(event))
    }

    /** The adapter could not complete the current step (e.g. retries exhausted). */
    fun fail(state: InstanceState, stepId: String, message: String): Transition {
        if (state.currentStep != stepId) {
            throw IllegalTransitionException("instance ${state.id} is at '${state.currentStep}', not '$stepId'")
        }
        val failed = state.copy(status = InstanceStatus.FAILED)
        return Transition(failed, emptyList(), listOf(EngineEvent("incident", stepId, payload = mapOf("message" to message), at = now())))
    }

    /**
     * Support restart: put the instance back at [stepId] and enter it, keeping its vars and history.
     * The caller cancels the open tasks and waits first; this only moves the instance.
     */
    fun restartAt(def: Definition, state: InstanceState, stepId: String, actor: String?): Transition {
        if (!def.hasStep(stepId)) throw IllegalTransitionException("no step '$stepId' in ${def.name}")
        val event = EngineEvent("restarted", stepId, actor = actor, payload = mapOf("from" to state.currentStep), at = now())
        return enter(def, state.copy(status = InstanceStatus.RUNNING), Target.ToStep(stepId), listOf(event))
    }

    /** Re-enter the step an incident stopped at. */
    fun retry(def: Definition, state: InstanceState, actor: String?): Transition {
        if (state.status != InstanceStatus.FAILED) throw IllegalTransitionException("instance ${state.id} is not failed")
        val step = state.currentStep ?: throw IllegalTransitionException("instance ${state.id} has no current step")
        val event = EngineEvent("retried", step, actor = actor, at = now())
        return enter(def, state.copy(status = InstanceStatus.RUNNING), Target.ToStep(step), listOf(event))
    }

    private fun enter(def: Definition, initial: InstanceState, initialTarget: Target, initialEvents: List<EngineEvent>): Transition {
        var state = initial
        var target = initialTarget
        val events = initialEvents.toMutableList()

        // Decisions are pure, so they are evaluated inline; anything else parks or hands off to the adapter.
        repeat(MAX_INLINE_HOPS) {
            when (val t = target) {
                is Target.ToEnd -> {
                    state = state.copy(status = InstanceStatus.COMPLETED, currentStep = null, endStatus = t.status)
                    events += EngineEvent("instance-ended", null, payload = mapOf("status" to t.status), at = now())
                    return Transition(state, listOf(Effect.Ended(t.status)), events)
                }
                is Target.ToStep -> {
                    val step = def.step(t.id)
                    state = state.copy(currentStep = step.id)
                    events += EngineEvent("step-entered", step.id, at = now())
                    try {
                        when (step) {
                            is Step.Decision -> {
                                val outcome = step.evaluate(Vars(state.vars))
                                state = state.copy(vars = state.vars + (step.id to record(emptyMap(), outcome, ENGINE)))
                                events += EngineEvent("step-completed", step.id, outcome, ENGINE, at = now())
                                target = def.resolve(step.id, outcome)
                            }
                            is Step.ServiceTask -> {
                                state = state.copy(status = InstanceStatus.RUNNING)
                                return Transition(state, listOf(Effect.RunService(step.id)), events)
                            }
                            is Step.UserTask -> {
                                val assignment = assign(step.assignee, Vars(state.vars))
                                state = state.copy(status = InstanceStatus.WAITING)
                                return Transition(state, listOf(Effect.CreateUserTask(step.id, assignment)), events)
                            }
                            is Step.EventWait -> {
                                val value = step.correlation?.let { Vars(state.vars).str(it.second) }
                                val expiresAt = step.timeout?.let { now().plusMillis(it.inWholeMilliseconds) }
                                state = state.copy(status = InstanceStatus.WAITING)
                                val park = Effect.ParkForEvent(step.id, step.event, step.correlation?.first, value, expiresAt)
                                return Transition(state, listOf(park), events)
                            }
                        }
                    } catch (e: RuntimeException) {
                        if (e is IllegalTransitionException) throw e
                        state = state.copy(status = InstanceStatus.FAILED)
                        events += EngineEvent("incident", step.id, payload = mapOf("message" to (e.message ?: e.toString())), at = now())
                        return Transition(state, emptyList(), events)
                    }
                }
            }
        }
        state = state.copy(status = InstanceStatus.FAILED)
        events += EngineEvent("incident", state.currentStep, payload = mapOf("message" to "more than $MAX_INLINE_HOPS decisions in a row; loop?"), at = now())
        return Transition(state, emptyList(), events)
    }

    private fun assign(assignee: Assignee, vars: Vars): Assignment = when (assignee) {
        is Assignee.User -> Assignment(Assignment.Type.USER, assignee.name, emptyMap())
        is Assignee.UserFrom -> Assignment(Assignment.Type.USER, vars.str(assignee.path), emptyMap())
        is Assignee.Role -> Assignment(
            Assignment.Type.ROLE,
            assignee.name,
            assignee.scope + assignee.scopeFrom.mapValues { (_, path) -> vars.str(path) },
        )
    }

    /** A step's record in vars: its output plus who chose which outcome when. Reserved keys win. */
    private fun record(output: Map<String, Any?>, outcome: String, actor: String?): Map<String, Any?> =
        output + mapOf("outcome" to outcome, "by" to actor, "at" to now().toString())

    private fun now(): Instant = clock.instant()

    companion object {
        const val ENGINE = "engine"
        private const val MAX_INLINE_HOPS = 1000
    }
}
