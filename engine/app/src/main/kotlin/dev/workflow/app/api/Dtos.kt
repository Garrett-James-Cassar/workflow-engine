package dev.workflow.app.api

import dev.workflow.app.persistence.EventRow
import dev.workflow.app.persistence.TaskRow
import dev.workflow.app.persistence.WaitRow
import dev.workflow.core.Assignment
import dev.workflow.core.Definition
import dev.workflow.core.InstanceState
import dev.workflow.core.Step
import dev.workflow.core.WorkflowGraph
import java.time.Instant
import java.util.UUID

data class StartRequest(val vars: Map<String, Any?> = emptyMap())

/** Reassign an open task to a user (`toTeam` false) or a team (`toTeam` true, e.g. "legal"). */
data class ReassignRequest(val to: String, val toTeam: Boolean = false, val scope: Map<String, String> = emptyMap())

data class RestartRequest(val step: String)

data class CompleteTaskRequest(val outcome: String, val form: Map<String, Any?> = emptyMap())

data class EventRequest(val correlation: Map<String, String> = emptyMap(), val payload: Map<String, Any?> = emptyMap())

data class EventAccepted(val woke: List<UUID>)

data class DefinitionSummary(val name: String, val latestVersion: Int, val versions: List<Int>, val steps: Int)

data class DefinitionView(
    val name: String,
    val version: Int,
    val sample: Map<String, Any?>,
    val graph: GraphView,
)

data class GraphView(
    val name: String,
    val version: Int,
    val nodes: List<Node>,
    val edges: List<Edge>,
    val paths: List<Path>,
) {
    data class Node(val id: String, val kind: String, val label: String, val path: String?, val meta: Map<String, Any?>)

    data class Edge(val from: String, val to: String, val outcome: String?, val label: String?)

    data class Path(val name: String, val parent: String?, val branchedFrom: String, val nodes: List<String>)

    companion object {
        fun of(g: WorkflowGraph) = GraphView(
            g.name,
            g.version,
            g.nodes.map { Node(it.id, it.kind.key, it.label, it.path, it.meta) },
            g.edges.map { Edge(it.from, it.to, it.outcome, it.label) },
            g.paths.map { Path(it.name, it.parent, it.branchedFrom, it.nodes) },
        )
    }
}

data class TaskView(
    val id: UUID,
    val instanceId: UUID,
    val definition: String,
    val step: String,
    val label: String,
    val assignee: Assignee,
    val outcomes: List<String>,
    val form: List<Field>,
    val status: String,
    val outcome: String?,
    val decidedBy: String?,
    val createdAt: Instant,
    /** "user" when one person holds the task, "team" when a role does. */
    val scopedTo: String,
    /** The role allowed to reassign this task, or null when it cannot be reassigned. */
    val reassignableBy: String?,
) {
    data class Assignee(val type: String, val name: String, val scope: Map<String, String>)

    data class Field(val name: String, val type: String, val required: Boolean)

    companion object {
        fun of(task: TaskRow, def: Definition): TaskView {
            val step = def.step(task.step) as Step.UserTask
            return TaskView(
                task.id, task.instanceId, def.name, task.step, step.label,
                Assignee(task.assignment.type.name.lowercase(), task.assignment.name, task.assignment.scope),
                step.outcomes,
                step.form.map { Field(it.name, it.type.name.lowercase(), it.required) },
                task.status, task.outcome, task.decidedBy, task.createdAt,
                scopedTo = if (task.assignment.type == Assignment.Type.ROLE) "team" else "user",
                reassignableBy = step.reassignableBy?.name,
            )
        }
    }
}

data class WaitView(val step: String, val event: String, val correlation: Map<String, String?>, val expiresAt: Instant?) {
    companion object {
        fun of(w: WaitRow) = WaitView(w.step, w.event, w.correlationKey?.let { mapOf(it to w.correlationValue) } ?: emptyMap(), w.expiresAt)
    }
}

data class InstanceView(
    val id: String,
    val definition: String,
    val version: Int,
    val status: String,
    val currentStep: String?,
    val endStatus: String?,
    val vars: Map<String, Any?>,
    val openTasks: List<TaskView>,
    val openWaits: List<WaitView>,
) {
    companion object {
        fun of(s: InstanceState, tasks: List<TaskView>, waits: List<WaitView>) =
            InstanceView(s.id, s.definition, s.version, s.status.name.lowercase(), s.currentStep, s.endStatus, s.vars, tasks, waits)
    }
}

data class EventView(
    val id: Long,
    val type: String,
    val step: String?,
    val outcome: String?,
    val actor: String?,
    val payload: Map<String, Any?>,
    val at: Instant,
) {
    companion object {
        fun of(r: EventRow) = EventView(r.id, r.event.type, r.event.step, r.event.outcome, r.event.actor, r.event.payload, r.event.at)
    }
}

/** An error as problem+json (RFC 7807): `error` and `detail` both carry the message (the console reads `error`). */
data class ErrorView(
    val error: String,
    val reason: String? = null,
    val title: String = error,
    val status: Int = 0,
    val detail: String = error,
    val type: String = "about:blank",
)
