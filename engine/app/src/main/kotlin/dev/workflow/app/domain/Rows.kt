package dev.workflow.app.domain

import dev.workflow.core.Assignment
import dev.workflow.core.EngineEvent
import java.time.Instant
import java.util.UUID

data class TaskRow(
    val id: UUID,
    val instanceId: UUID,
    val step: String,
    val assignment: Assignment,
    val status: String,
    val outcome: String?,
    val form: Map<String, Any?>?,
    val decidedBy: String?,
    val decidedAt: Instant?,
    val createdAt: Instant,
)

data class WaitRow(
    val id: UUID,
    val instanceId: UUID,
    val step: String,
    val event: String,
    val correlationKey: String?,
    val correlationValue: String?,
    val expiresAt: Instant?,
    val status: String,
)

data class EventRow(val id: Long, val event: EngineEvent)

/** A wait to park an instance on: an external event, optionally correlated and optionally timed. */
data class NewWait(
    val instanceId: UUID,
    val step: String,
    val event: String,
    val correlationKey: String?,
    val correlationValue: String?,
    val expiresAt: Instant?,
)

/** How a user task was decided. */
data class TaskDecision(val outcome: String, val form: Map<String, Any?>, val by: String, val at: Instant)

/** What a definition looks like in a list. */
data class DefinitionInfo(val name: String, val latestVersion: Int, val versions: List<Int>, val steps: Int)

/** An open task together with the definition it belongs to. */
data class TaskDetail(val task: TaskRow, val definition: dev.workflow.core.Definition)

/** A run with its open tasks and waits: what a caller sees after reading or changing it. */
data class InstanceDetail(val state: dev.workflow.core.InstanceState, val tasks: List<TaskDetail>, val waits: List<WaitRow>)

/** What running a service task came to: the outcome its handler chose, or the failure after retries. */
sealed interface ServiceOutcome {
    data class Ok(val outcome: String, val output: Map<String, Any?>, val actor: String) : ServiceOutcome

    data class Failed(val message: String) : ServiceOutcome
}
