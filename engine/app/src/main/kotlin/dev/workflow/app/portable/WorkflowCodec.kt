package dev.workflow.app.portable

import dev.workflow.app.domain.DOC_VERSION
import dev.workflow.app.domain.Doc
import dev.workflow.app.domain.DocProblem
import dev.workflow.app.domain.Ref
import dev.workflow.core.Definition
import dev.workflow.core.WorkflowDefinitionException

private val FIELDS = setOf("kind", "version", "key", "name", "workflowVersion", "sample", "steps")

/**
 * A workflow definition as a document: `{kind: "workflow", version: 1, key: "<name>:<workflowVersion>", name, workflowVersion, sample, steps}`.
 * The key carries the workflow's own version because a version never changes once it has run (CLAUDE.md).
 * A decision's code and a handler's class are not data: a decision is written as `evaluator: "code"`, a handler by its class name,
 * which is a reference the importing app must resolve (`missing-ref` when it cannot).
 */
class WorkflowCodec : Codec<Definition> {
    override val kind = "workflow"

    override fun keyOf(item: Definition) = key(item.name, item.version)

    override fun toDoc(item: Definition): Doc = envelope(kind, keyOf(item), WorkflowWriter.fields(item))

    override fun fromDoc(doc: Doc): Definition {
        val n = Node(doc, "").only(FIELDS)
        header(n)
        val (name, version) = n["name"].text() to n["workflowVersion"].int()
        if (n["key"].text() != key(name, version)) n["key"].fail("must be ${key(name, version)}")
        return build(name, version, n)
    }

    override fun refs(doc: Doc): List<Ref> = WorkflowRefs.of(Node(doc, "")["steps"])

    private fun header(n: Node) {
        if (n["kind"].text() != kind) n["kind"].fail("must be \"$kind\"")
        val version = n["version"].int()
        if (version > DOC_VERSION) n["version"].fail("is $version; this app reads up to $DOC_VERSION")
        if (version < 1) n["version"].fail("must be 1 or more")
    }

    private fun build(name: String, version: Int, n: Node): Definition = try {
        Definition.build(name, version, WorkflowReader.steps(n["steps"], null), n["sample"].obj())
    } catch (e: WorkflowDefinitionException) {
        throw DocException(e.errors.map { DocProblem("/steps", it) })
    }

    companion object {
        fun key(name: String, version: Int) = "$name:$version"
    }
}

/** The handler classes a workflow document names. */
internal object WorkflowRefs {
    fun of(steps: Node): List<Ref> = steps.items().flatMap { step(it) }

    private fun step(n: Node): List<Ref> = handler(n) + n["routes"].obj().keys.flatMap { route(n["routes"][it]) }

    private fun handler(n: Node): List<Ref> = n["handler"].let { if (it.value is String) listOf(Ref("handler", it.text(), it.path)) else emptyList() }

    private fun route(n: Node): List<Ref> = if (n["type"].value == "path") of(n["steps"]) else emptyList()
}
