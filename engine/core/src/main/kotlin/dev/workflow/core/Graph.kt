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

fun Definition.toGraph(): WorkflowGraph = GraphBuilder(this).build()

private class GraphBuilder(private val def: Definition) {
    private val nodes = mutableListOf(GraphNode(START, NodeKind.START, "start", null))
    private val edges = mutableListOf(GraphEdge(START, def.first.id, null, null))
    private val ends = linkedSetOf<String>()
    private val paths = mutableListOf<GraphPath>()

    fun build(): WorkflowGraph {
        def.allSteps.forEach { add(it) }
        ends.forEach { nodes += GraphNode(endId(it), NodeKind.END, it, null) }
        return WorkflowGraph(def.name, def.version, nodes, edges, paths)
    }

    private fun add(step: Step) {
        nodes += GraphNode(step.id, step.kind(), step.label, step.path, step.meta())
        step.outcomes.forEach { addOutcome(step, it) }
    }

    private fun addOutcome(step: Step, outcome: String) {
        val single = step.outcomes.size == 1
        edges += GraphEdge(step.id, targetId(def.resolve(step.id, outcome)), if (single) null else outcome, label(step, outcome, single))
        (step.routes.getValue(outcome) as? Route.Path)?.let { paths += GraphPath(it.name, step.path, step.id, it.steps.map { s -> s.id }) }
    }

    private fun label(step: Step, outcome: String, single: Boolean): String? {
        val description = (step as? Step.Decision)?.descriptions?.get(outcome)
        return when {
            single -> null
            description != null -> "$outcome: $description"
            else -> outcome
        }
    }

    private fun targetId(t: Target) = when (t) {
        is Target.ToStep -> t.id
        is Target.ToEnd -> endId(t.status).also { ends += t.status }
    }
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
    is Step.UserTask -> userMeta()
    is Step.ServiceTask -> serviceMeta()
    is Step.EventWait -> eventMeta()
    is Step.Decision -> if (descriptions.isEmpty()) emptyMap() else mapOf("describe" to descriptions)
}

private fun Step.UserTask.userMeta(): Map<String, Any?> = buildMap {
    put("scopedTo", scope.name.lowercase())
    reassignableBy?.let { put("reassignableBy", it.name) }
    putAll(assigneeMeta(assignee))
    if (form.isNotEmpty()) put("form", form.map { mapOf("name" to it.name, "type" to it.type.name, "required" to it.required) })
}

private fun assigneeMeta(a: Assignee): Map<String, Any?> = when (a) {
    is Assignee.Role -> buildMap {
        put("role", a.name)
        if (a.scope.isNotEmpty()) put("scope", a.scope)
        if (a.scopeFrom.isNotEmpty()) put("scopeFrom", a.scopeFrom)
    }
    is Assignee.User -> mapOf("user" to a.name)
    is Assignee.UserFrom -> mapOf("userFrom" to a.path)
}

private fun Step.ServiceTask.serviceMeta(): Map<String, Any?> = buildMap {
    handler?.let { put("handler", it.simpleName) }
    http?.let { put("http", "${it.method} ${it.url}") }
    put("retries", retries)
}

private fun Step.EventWait.eventMeta(): Map<String, Any?> = buildMap {
    put("event", event)
    correlation?.let { put("correlateBy", mapOf(it.first to it.second)) }
    timeout?.let { put("timeout", it.toIsoString()) }
}
