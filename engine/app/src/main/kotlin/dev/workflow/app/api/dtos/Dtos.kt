package dev.workflow.app.api.dtos

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/** Request schemas (SECURITY.md): unknown fields are rejected (Jackson), lengths and counts are capped here. */
data class StartRequest(@field:Size(max = Limits.MAP_ITEMS) val vars: Map<String, Any?> = emptyMap())

/** Reassign an open task to a user (`toTeam` false) or a team (`toTeam` true, e.g. "legal"). */
data class ReassignRequest(
    @field:Name val to: String,
    val toTeam: Boolean = false,
    @field:Size(max = Limits.SCOPE_ITEMS) val scope: Map<String, String> = emptyMap(),
)

data class RestartRequest(@field:Name val step: String)

data class CompleteTaskRequest(
    @field:Name val outcome: String,
    @field:Size(max = Limits.MAP_ITEMS) val form: Map<String, Any?> = emptyMap(),
)

data class EventRequest(
    @field:Size(max = Limits.SCOPE_ITEMS) val correlation: Map<String, String> = emptyMap(),
    @field:Size(max = Limits.MAP_ITEMS) val payload: Map<String, Any?> = emptyMap(),
)

/** Schema limits shared by the request DTOs and the controller parameters. */
object Limits {
    const val NAME = 100
    const val ID = "^[A-Za-z0-9._:-]+$"
    const val MAP_ITEMS = 200
    const val SCOPE_ITEMS = 20
    const val MAX_PAGE = 500L
    const val FILTER = 200
    const val MAX_VERSION = 100_000L
}

data class EventAccepted(@field:Size(max = 10_000) val woke: List<UUID>)

data class DefinitionSummary(
    @field:NotBlank val name: String,
    @field:Min(1) val latestVersion: Int,
    val versions: List<Int>,
    @field:Min(0) val steps: Int,
)

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
    @field:JsonInclude(JsonInclude.Include.NON_NULL) val errors: List<FieldProblem>? = null,
)

/** One rejected input: where (a path such as `payload.name`) and why, never the value. */
data class FieldProblem(val path: String, val message: String)

data class ExportQuery(
    @field:Min(1) @field:Max(Limits.MAX_VERSION) val version: Int? = null,
    @field:Pattern(regexp = "^(html|mermaid|graph)$") val format: String? = null,
)

/** A rendered export: its media type and the body to send. */
data class Rendered(val mediaType: String, val body: Any)

data class MeView(val login: String, val displayName: String, val permissions: List<GrantView>) {
    data class GrantView(val permission: String, val scope: Map<String, String>)
}

data class AuditView(
    val id: Long,
    val at: java.time.Instant,
    val actor: String,
    val via: String?,
    val action: String,
    val resource: String,
    val outcome: String,
    val changes: List<String>,
    val traceId: String?,
)

data class PersonView(val user: String, val name: String, val teams: List<String>, val grants: List<RoleGrantView>) {
    data class RoleGrantView(val role: String, val scope: Map<String, String>)
}
