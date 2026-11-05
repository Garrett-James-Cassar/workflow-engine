package dev.workflow.core

/**
 * Renders a [WorkflowGraph] as a left-to-right Mermaid flowchart styled after BPMN:
 * - start: thin circle; end: thick circle carrying the end status;
 * - event wait: a task box with an envelope (BPMN receive task);
 * - decision: exclusive gateway, a diamond marked ✕, named underneath;
 * - user task: rounded, squarish box in faded pink; service task: the same box in white;
 * - paths: dashed groups.
 *
 * Shapes and colours come from [NodeKind] only, so a new kind needs one entry in [shape] and [STYLES].
 */
object MermaidExporter {
    /** Fixed spacing and straight edges keep the layout regular. */
    private val CONFIG = """
        ---
        config:
          flowchart:
            curve: linear
            nodeSpacing: 36
            rankSpacing: 56
            padding: 14
            htmlLabels: false
        ---
    """.trimIndent()

    private val STYLES = mapOf(
        "userTask" to "fill:#fbe7ee,stroke:#b9768f,stroke-width:1.5px,color:#2b1a21",
        "serviceTask" to "fill:#ffffff,stroke:#5f6b66,stroke-width:1.5px,color:#1c2320",
        "eventWait" to "fill:#ffffff,stroke:#5f6b66,stroke-width:1.5px,color:#1c2320",
        "gateway" to "fill:#fff8e6,stroke:#a8842f,stroke-width:1.5px,color:#3a2e10,font-size:12px",
        "startEvent" to "fill:#ffffff,stroke:#4a7d5c,stroke-width:1.5px,color:#4a7d5c",
        "endEvent" to "fill:#ffffff,stroke:#2b2b2b,stroke-width:4px,color:#2b2b2b,font-size:11px",
    )

    /** Task labels wrap at this many characters so boxes stay close to square. */
    private const val WRAP = 14

    /** Non-breaking spaces either side of a task's icon; sets the common task width. */
    private const val TASK_PAD = 14

    fun export(graph: WorkflowGraph): String = buildString {
        appendLine(CONFIG)
        appendLine("%% ${graph.name} v${graph.version}: generated from the Kotlin DSL; do not edit")
        appendLine("flowchart LR")

        val byPath = graph.nodes.groupBy { it.path }
        val childPaths = graph.paths.groupBy { it.parent }

        fun declare(path: String?, indent: String) {
            byPath[path].orEmpty().forEach { appendLine("$indent${shape(it)}") }
            childPaths[path].orEmpty().forEach { p ->
                appendLine("${indent}subgraph ${pathId(p.name)} [\"${escape(p.name)}\"]")
                appendLine("$indent  direction LR")
                declare(p.name, "$indent  ")
                appendLine("${indent}end")
            }
        }
        declare(null, "  ")

        graph.edges.forEach { e ->
            val arrow = if (e.label == null) "-->" else "-->|\"${escape(e.label)}\"|"
            appendLine("  ${nodeId(e.from)} $arrow ${nodeId(e.to)}")
        }

        STYLES.forEach { (cls, style) -> appendLine("  classDef $cls $style") }
        graph.nodes.groupBy { cssClass(it.kind) }.forEach { (cls, nodes) ->
            appendLine("  class ${nodes.joinToString(",") { nodeId(it.id) }} $cls")
        }
        graph.paths.forEach {
            appendLine("  style ${pathId(it.name)} fill:none,stroke:#9aa29e,stroke-width:1px,stroke-dasharray:6 4,color:#5f6b66")
        }
    }

    private fun shape(n: GraphNode): String {
        val id = nodeId(n.id)
        return when (n.kind) {
            NodeKind.START -> "$id((\" \"))"
            NodeKind.END -> "$id((\"${wrap(n.label.replace('-', ' '), 9)}\"))"
            NodeKind.USER_TASK -> "$id(\"${task(scopeIcon(n), n.label, scopeLine(n))}\")"
            NodeKind.SERVICE_TASK -> "$id(\"${task("⚙", n.label)}\")"
            NodeKind.EVENT_WAIT -> "$id(\"${task("✉", n.label)}\")"
            NodeKind.DECISION -> "$id{\"✕<br/>${wrap(n.label, 10)}\"}"
        }
    }

    /**
     * A task label: icon line, then the name on exactly two lines. The icon line is padded with
     * non-breaking spaces to a fixed width so every task box is the same squarish size.
     */
    private fun task(icon: String, label: String, footer: String? = null): String {
        val lines = wrap(label).split("<br/>").toMutableList()
        while (lines.size < 2) lines += "#nbsp;"
        val pad = "#nbsp;".repeat(TASK_PAD)
        return (listOf("$pad$icon$pad") + lines + listOfNotNull(footer)).joinToString("<br/>")
    }

    /** 👤 for a task held by one person, 👥 for a task held by a team (role). */
    private fun scopeIcon(n: GraphNode) = if (n.meta["scopedTo"] == "team") "👥" else "👤"

    /** Who holds the task, as a footer line: "user · j.smith", "user · from doc.requester" or "team · legal". */
    private fun scopeLine(n: GraphNode): String = when {
        n.meta["scopedTo"] == "team" -> "team · ${n.meta["role"]}" + (n.meta["scope"]?.let { " ${scopeText(it)}" } ?: "")
        n.meta["user"] != null -> "user · ${n.meta["user"]}"
        n.meta["userFrom"] != null -> "user · from ${n.meta["userFrom"]}"
        else -> "user"
    }.let { "<i>${escape(it)}</i>" }

    private fun scopeText(scope: Any?) = (scope as? Map<*, *>)?.entries?.joinToString(", ") { "${it.key}=${it.value}" }?.let { "($it)" } ?: ""

    private fun cssClass(kind: NodeKind) = when (kind) {
        NodeKind.START -> "startEvent"
        NodeKind.END -> "endEvent"
        NodeKind.USER_TASK -> "userTask"
        NodeKind.SERVICE_TASK -> "serviceTask"
        NodeKind.EVENT_WAIT -> "eventWait"
        NodeKind.DECISION -> "gateway"
    }

    /** Break a label onto lines of at most [width] characters, on word boundaries. */
    private fun wrap(label: String, width: Int = WRAP): String {
        val lines = mutableListOf<String>()
        var line = ""
        for (word in label.split(' ').filter { it.isNotEmpty() }) {
            line = when {
                line.isEmpty() -> word
                line.length + 1 + word.length <= width -> "$line $word"
                else -> { lines += line; word }
            }
        }
        if (line.isNotEmpty()) lines += line
        return lines.joinToString("<br/>") { escape(it) }
    }

    /** Mermaid ids must avoid keywords and punctuation, so prefix and squash. */
    private fun nodeId(id: String) = "n_" + id.replace(Regex("[^A-Za-z0-9]"), "_")

    private fun pathId(name: String) = "p_" + name.replace(Regex("[^A-Za-z0-9]"), "_")

    private fun escape(s: String) = s.replace("\"", "#quot;").replace("<", "#lt;").replace(">", "#gt;")
}
