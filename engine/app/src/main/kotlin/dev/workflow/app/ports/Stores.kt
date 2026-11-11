package dev.workflow.app.ports

import dev.workflow.app.domain.EventRow
import dev.workflow.app.domain.NewWait
import dev.workflow.app.domain.TaskDecision
import dev.workflow.app.domain.TaskRow
import dev.workflow.app.domain.WaitRow
import dev.workflow.core.Assignment
import dev.workflow.core.EngineEvent
import dev.workflow.core.InstanceState
import java.time.Instant
import java.util.UUID

/** Runs and their event history. Callers own the transaction. */
interface InstanceOps {
    fun insertInstance(state: InstanceState)

    fun saveInstance(state: InstanceState)

    fun instance(id: UUID): InstanceState?

    /** Load and lock the instance row for the rest of the transaction, so steps advance one at a time. */
    fun lockInstance(id: UUID): InstanceState?

    fun instances(limit: Int): List<InstanceState>

    /** Several runs in one query (a list screen must not ask once per row). */
    fun instancesByIds(ids: Collection<UUID>): List<InstanceState>

    fun appendEvents(instanceId: UUID, events: List<EngineEvent>)

    fun events(instanceId: UUID): List<EventRow>

    /** Clears every run, task, wait and event (demo reset). */
    fun resetAll()
}

interface TaskOps {
    fun insertTask(instanceId: UUID, step: String, assignment: Assignment): UUID

    fun task(id: UUID): TaskRow?

    /** Open tasks, oldest first; [limit] and [offset] page through them in the database (NFR P1: never load them all). */
    /** The open tasks of several runs in one query. */
    fun openTasksOf(instanceIds: Collection<UUID>): List<TaskRow>

    fun openTasks(instanceId: UUID? = null, limit: Int = Int.MAX_VALUE, offset: Int = 0): List<TaskRow>

    /** Move an open task to a new user or team. The history of who held it lives in the events. */
    fun reassignTask(id: UUID, assignment: Assignment)

    fun closeTask(id: UUID, decision: TaskDecision)

    fun cancelOpenTasks(instanceId: UUID)
}

interface WaitOps {
    fun insertWait(wait: NewWait)

    fun openWaits(event: String): List<WaitRow>

    fun openWaitsFor(instanceId: UUID): List<WaitRow>

    /** The open waits of several runs in one query. */
    fun openWaitsOf(instanceIds: Collection<UUID>): List<WaitRow>

    fun expiredWaits(now: Instant): List<WaitRow>

    fun closeWait(id: UUID)

    fun cancelOpenWaits(instanceId: UUID)
}

/** All persistence of the engine: runs, events, tasks and waits. */
interface WorkflowStore : InstanceOps, TaskOps, WaitOps {
    /** Cancel every open user task and wait of an instance. The rows stay as history. */
    fun cancelOpenWork(instanceId: UUID)
}
