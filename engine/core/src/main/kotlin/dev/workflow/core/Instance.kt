package dev.workflow.core

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
