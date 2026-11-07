package dev.workflow.app.runtime

import dev.workflow.app.domain.ConflictException
import dev.workflow.app.domain.NewWait
import dev.workflow.app.domain.NotFoundException
import dev.workflow.app.domain.ServiceOutcome
import dev.workflow.app.ports.WorkflowDefinitions
import dev.workflow.app.ports.WorkflowStore
import dev.workflow.core.Definition
import dev.workflow.core.Effect
import dev.workflow.core.Engine
import dev.workflow.core.IllegalTransitionException
import dev.workflow.core.InstanceState
import dev.workflow.core.ServiceContext
import dev.workflow.core.Step
import dev.workflow.core.Transition
import dev.workflow.core.Vars
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * The mechanics every operation shares: lock the instance row in one transaction, apply the engine's
 * transition and carry out its effects (persist state and events, open tasks and waits, run service
 * tasks until the instance parks or ends). v1 runs service handlers inside the transaction.
 */
@Component
class Transitions(
    private val defs: WorkflowDefinitions,
    private val store: WorkflowStore,
    private val engine: Engine,
    private val services: ServiceRunner,
    private val tx: TransactionTemplate,
) {
    fun lock(id: UUID): InstanceState = store.lockInstance(id) ?: throw NotFoundException("no instance $id")

    fun definitionOf(state: InstanceState): Definition = defs.get(state.definition, state.version)

    @Suppress("UNCHECKED_CAST")
    fun <T> inTx(block: () -> T): T = try {
        tx.execute { block() } as T
    } catch (e: IllegalTransitionException) {
        throw ConflictException(e.message ?: "illegal transition")
    }

    /** Persist [transition] and follow it until the instance parks (task or wait) or ends. */
    fun apply(def: Definition, transition: Transition): InstanceState {
        var t = transition
        while (true) {
            persist(t)
            t = next(def, t) ?: return t.state
        }
    }

    private fun persist(t: Transition) {
        store.saveInstance(t.state)
        store.appendEvents(UUID.fromString(t.state.id), t.events)
    }

    /** The transition after running a service task; null when the instance parked or ended. */
    private fun next(def: Definition, t: Transition): Transition? = when (val effect = t.effects.singleOrNull()) {
        null, is Effect.Ended -> null
        is Effect.CreateUserTask -> park { store.insertTask(UUID.fromString(t.state.id), effect.stepId, effect.assignment) }
        is Effect.ParkForEvent -> park { store.insertWait(wait(t, effect)) }
        is Effect.RunService -> runService(def, t, def.step(effect.stepId) as Step.ServiceTask)
    }

    private fun park(work: () -> Unit): Transition? {
        work()
        return null
    }

    private fun wait(t: Transition, e: Effect.ParkForEvent) =
        NewWait(UUID.fromString(t.state.id), e.stepId, e.event, e.correlationKey, e.correlationValue, e.expiresAt)

    private fun runService(def: Definition, t: Transition, step: Step.ServiceTask): Transition =
        when (val result = services.run(step, ServiceContext(t.state.id, step.id, Vars(t.state.vars)))) {
            is ServiceOutcome.Ok -> afterHandler(def, t, step, result)
            is ServiceOutcome.Failed -> engine.fail(t.state, step.id, result.message)
        }

    private fun afterHandler(def: Definition, t: Transition, step: Step.ServiceTask, result: ServiceOutcome.Ok): Transition =
        if (result.outcome in step.outcomes) {
            engine.complete(def, t.state, step.id, result.outcome, result.output, result.actor)
        } else {
            engine.fail(t.state, step.id, "handler returned unknown outcome '${result.outcome}'")
        }
}
