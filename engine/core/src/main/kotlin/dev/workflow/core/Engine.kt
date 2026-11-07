package dev.workflow.core

import java.time.Clock
import java.time.Instant

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

    /** The instance and its events while a run of inline steps (decisions) is followed to the next park or end. */
    private class Run(var state: InstanceState, val events: MutableList<EngineEvent>) {
        fun done(effects: List<Effect>): Hop = Hop.Done(Transition(state, effects, events))
    }

    private sealed interface Hop {
        data class Next(val target: Target) : Hop

        data class Done(val transition: Transition) : Hop
    }

    private fun enter(def: Definition, initial: InstanceState, initialTarget: Target, initialEvents: List<EngineEvent>): Transition {
        val run = Run(initial, initialEvents.toMutableList())
        var target = initialTarget
        // Decisions are pure, so they are evaluated inline; anything else parks or hands off to the adapter.
        repeat(MAX_INLINE_HOPS) {
            when (val hop = hop(def, run, target)) {
                is Hop.Done -> return hop.transition
                is Hop.Next -> target = hop.target
            }
        }
        return tooManyHops(run)
    }

    private fun hop(def: Definition, run: Run, target: Target): Hop = when (target) {
        is Target.ToEnd -> end(run, target)
        is Target.ToStep -> enterStep(def, run, def.step(target.id))
    }

    private fun end(run: Run, t: Target.ToEnd): Hop {
        run.state = run.state.copy(status = InstanceStatus.COMPLETED, currentStep = null, endStatus = t.status)
        run.events += EngineEvent("instance-ended", null, payload = mapOf("status" to t.status), at = now())
        return run.done(listOf(Effect.Ended(t.status)))
    }

    private fun enterStep(def: Definition, run: Run, step: Step): Hop {
        run.state = run.state.copy(currentStep = step.id)
        run.events += EngineEvent("step-entered", step.id, at = now())
        return try {
            handle(def, run, step)
        } catch (e: RuntimeException) {
            incident(run, step, e)
        }
    }

    private fun handle(def: Definition, run: Run, step: Step): Hop = when (step) {
        is Step.Decision -> decide(def, run, step)
        is Step.ServiceTask -> park(run, InstanceStatus.RUNNING, Effect.RunService(step.id))
        is Step.UserTask -> park(run, InstanceStatus.WAITING, Effect.CreateUserTask(step.id, assign(step.assignee, Vars(run.state.vars))))
        is Step.EventWait -> park(run, InstanceStatus.WAITING, parkForEvent(run.state, step))
    }

    private fun park(run: Run, status: InstanceStatus, effect: Effect): Hop {
        run.state = run.state.copy(status = status)
        return run.done(listOf(effect))
    }

    private fun parkForEvent(state: InstanceState, step: Step.EventWait): Effect {
        val value = step.correlation?.let { Vars(state.vars).str(it.second) }
        val expiresAt = step.timeout?.let { now().plusMillis(it.inWholeMilliseconds) }
        return Effect.ParkForEvent(step.id, step.event, step.correlation?.first, value, expiresAt)
    }

    private fun decide(def: Definition, run: Run, step: Step.Decision): Hop {
        val outcome = step.evaluate(Vars(run.state.vars))
        run.state = run.state.copy(vars = run.state.vars + (step.id to record(emptyMap(), outcome, ENGINE)))
        run.events += EngineEvent("step-completed", step.id, outcome, ENGINE, at = now())
        return Hop.Next(def.resolve(step.id, outcome))
    }

    private fun incident(run: Run, step: Step, e: RuntimeException): Hop {
        if (e is IllegalTransitionException) throw e
        run.state = run.state.copy(status = InstanceStatus.FAILED)
        run.events += EngineEvent("incident", step.id, payload = mapOf("message" to (e.message ?: e.toString())), at = now())
        return run.done(emptyList())
    }

    private fun tooManyHops(run: Run): Transition {
        run.state = run.state.copy(status = InstanceStatus.FAILED)
        run.events += EngineEvent("incident", run.state.currentStep, payload = mapOf("message" to "more than $MAX_INLINE_HOPS decisions in a row; loop?"), at = now())
        return Transition(run.state, emptyList(), run.events)
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
