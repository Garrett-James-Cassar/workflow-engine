package dev.workflow.core

/**
 * The drawable form of a definition, and the source of truth for every diagram. Exporters
 * ([MermaidExporter], later BPMN or the studio's renderer) only read this.
 *
 * One node per step plus `start` and one `end:<status>` per end status. One edge per outcome:
 * labelled with the outcome when a step has several, unlabelled when it has one.
 */
data class WorkflowGraph(
    val name: String,
    val version: Int,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val paths: List<GraphPath>,
)

enum class NodeKind(val key: String) {
    START("start"),
    END("end"),
    USER_TASK("user-task"),
    SERVICE_TASK("service-task"),
    EVENT_WAIT("event-wait"),
    DECISION("decision"),
}

data class GraphNode(
    val id: String,
    val kind: NodeKind,
    val label: String,
    /** Innermost path the node sits in; null on the main flow. */
    val path: String?,
    val meta: Map<String, Any?> = emptyMap(),
)

data class GraphEdge(val from: String, val to: String, val outcome: String?, val label: String?)

/** A named branch, drawn as a group. [parent] is the enclosing path for nested branches. */
data class GraphPath(val name: String, val parent: String?, val branchedFrom: String, val nodes: List<String>)

fun Definition.toGraph(): WorkflowGraph {
    val nodes = mutableListOf(GraphNode(START, NodeKind.START, "start", null))
    val edges = mutableListOf(GraphEdge(START, first.id, null, null))
    val ends = linkedSetOf<String>()
    val paths = mutableListOf<GraphPath>()

    fun targetId(t: Target) = when (t) {
        is Target.ToStep -> t.id
        is Target.ToEnd -> endId(t.status).also { ends += t.status }
    }

    for (step in allSteps) {
        nodes += GraphNode(step.id, step.kind(), step.label, step.path, step.meta())
        val single = step.outcomes.size == 1
        for (outcome in step.outcomes) {
            val description = (step as? Step.Decision)?.descriptions?.get(outcome)
            val label = when {
                single -> null
                description != null -> "$outcome: $description"
                else -> outcome
            }
            edges += GraphEdge(step.id, targetId(resolve(step.id, outcome)), if (single) null else outcome, label)
            val route = step.routes.getValue(outcome)
            if (route is Route.Path) paths += GraphPath(route.name, step.path, step.id, route.steps.map { it.id })
        }
    }
    ends.forEach { nodes += GraphNode(endId(it), NodeKind.END, it, null) }
    return WorkflowGraph(name, version, nodes, edges, paths)
}

const val START = "start"

fun endId(status: String) = "end:$status"

private fun Step.kind() = when (this) {
    is Step.UserTask -> NodeKind.USER_TASK
    is Step.ServiceTask -> NodeKind.SERVICE_TASK
    is Step.EventWait -> NodeKind.EVENT_WAIT
    is Step.Decision -> NodeKind.DECISION
}

private fun Step.meta(): Map<String, Any?> = when (this) {
    is Step.UserTask -> buildMap {
        put("scopedTo", scope.name.lowercase())
        reassignableBy?.let { put("reassignableBy", it.name) }
        when (val a = assignee) {
            is Assignee.Role -> {
                put("role", a.name)
                if (a.scope.isNotEmpty()) put("scope", a.scope)
                if (a.scopeFrom.isNotEmpty()) put("scopeFrom", a.scopeFrom)
            }
            is Assignee.User -> put("user", a.name)
            is Assignee.UserFrom -> put("userFrom", a.path)
        }
        if (form.isNotEmpty()) put("form", form.map { mapOf("name" to it.name, "type" to it.type.name, "required" to it.required) })
    }
    is Step.ServiceTask -> buildMap {
        handler?.let { put("handler", it.simpleName) }
        http?.let { put("http", "${it.method} ${it.url}") }
        put("retries", retries)
    }
    is Step.EventWait -> buildMap {
        put("event", event)
        correlation?.let { put("correlateBy", mapOf(it.first to it.second)) }
        timeout?.let { put("timeout", it.toIsoString()) }
    }
    is Step.Decision -> if (descriptions.isEmpty()) emptyMap() else mapOf("describe" to descriptions)
}
