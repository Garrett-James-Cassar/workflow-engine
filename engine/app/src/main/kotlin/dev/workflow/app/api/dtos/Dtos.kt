package dev.workflow.app.api.dtos

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

}

data class WaitView(val step: String, val event: String, val correlation: Map<String, String?>, val expiresAt: Instant?)

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
)

data class EventView(
    val id: Long,
    val type: String,
    val step: String?,
    val outcome: String?,
    val actor: String?,
    val payload: Map<String, Any?>,
    val at: Instant,
)

/** An error as problem+json (RFC 7807): `error` and `detail` both carry the message (the console reads `error`). */
data class ErrorView(
    val error: String,
    val reason: String? = null,
    val title: String = error,
    val status: Int = 0,
    val detail: String = error,
    val type: String = "about:blank",
)

data class ExportQuery(val version: Int? = null, val format: String? = null)

/** A rendered export: its media type and the body to send. */
data class Rendered(val mediaType: String, val body: Any)

data class MeView(val login: String, val displayName: String, val permissions: List<GrantView>) {
    data class GrantView(val permission: String, val scope: Map<String, String>)
}

data class PersonView(val user: String, val name: String, val teams: List<String>, val grants: List<RoleGrantView>) {
    data class RoleGrantView(val role: String, val scope: Map<String, String>)
}
