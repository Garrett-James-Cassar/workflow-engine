package dev.workflow.app.portable

import dev.workflow.app.domain.Doc
import dev.workflow.app.domain.DocProblem
import dev.workflow.app.domain.ImportRejectedException
import dev.workflow.app.domain.Plan
import dev.workflow.app.domain.PlanAction
import dev.workflow.app.domain.PlanEntry
import org.springframework.stereotype.Component

private class Item(val index: Int, val kind: ConfigKind, val doc: Doc) {
    val key = doc["key"] as String
}

/** One valid document of an import file, in dependency order, with what importing it would do. */
class Planned(val index: Int, val kind: ConfigKind, val doc: Doc, val entry: PlanEntry)

/** Reads an import file, validates every document, and says what each would do. Writes nothing. */
@Component
class ImportPlanner(private val kinds: ConfigKinds) {
    /** [only]: the one kind a per-resource route takes (its plural); null for the bundle. Throws [ImportRejectedException] on any bad document. */
    fun plan(body: Any?, only: String? = null): List<Planned> {
        val problems = mutableListOf<DocProblem>()
        val items = documents(body).mapNotNull { (i, raw) -> read(i, raw, only, problems) }
        duplicates(items, problems)
        if (problems.isNotEmpty()) throw ImportRejectedException(problems)
        val current = kinds.all.associateWith { it.current() }
        return items.sortedBy { kinds.rank(it.kind) }.map { planned(it, current.getValue(it.kind)[it.key]) }
    }

    fun summary(planned: List<Planned>) = Plan(planned.map { it.entry })

    private fun documents(body: Any?): List<Pair<Int, Any?>> = when (body) {
        is Map<*, *> -> listOf(0 to body)
        is List<*> -> body.mapIndexed { i, d -> i to d }
        else -> throw ImportRejectedException(listOf(DocProblem("/", "must be an envelope or an array of envelopes")))
    }

    private fun read(i: Int, raw: Any?, only: String?, problems: MutableList<DocProblem>): Item? = try {
        val node = Node(raw, "/$i")
        val doc = node.obj()
        val kind = kinds.all.firstOrNull { it.codec.isDoc(doc) && (only == null || it.plural == only) } ?: node["kind"].fail("is not a kind this route imports")
        kind.codec.fromDoc(doc).let { Item(i, kind, canonical(doc) as Doc) }
    } catch (e: DocException) {
        problems += e.problems.map { shift(i, it) }
        null
    }

    /** The reader's pointers start at the document; the file's start at the array. */
    private fun shift(i: Int, p: DocProblem) = if (p.path.startsWith("/$i")) p else DocProblem("/$i" + p.path.removeSuffix("/"), p.message)

    private fun duplicates(items: List<Item>, problems: MutableList<DocProblem>) {
        items.groupBy { it.kind.plural to it.key }.values.filter { it.size > 1 }
            .forEach { same -> same.drop(1).forEach { problems += DocProblem("/${it.index}/key", "duplicate of /${same.first().index}") } }
    }

    private fun planned(item: Item, current: Doc?): Planned = Planned(item.index, item.kind, item.doc, entry(item, current))

    private fun entry(item: Item, current: Doc?): PlanEntry {
        val missing = item.kind.codec.refs(item.doc).filterNot(item.kind::exists)
        val base = PlanEntry(item.kind.codec.kind, item.key, PlanAction.CREATE)
        return when {
            missing.isNotEmpty() -> base.copy(action = PlanAction.MISSING_REF, missing = missing)
            current == null -> base
            current == item.doc -> base.copy(action = PlanAction.UNCHANGED)
            item.kind.locked(item.key) -> base.copy(action = PlanAction.CONFLICT, changed = changed(current, item.doc), note = "defined in code; an import cannot change it")
            else -> base.copy(action = PlanAction.OVERWRITE, changed = changed(current, item.doc))
        }
    }

    private fun changed(current: Doc, incoming: Doc): List<String> = (current.keys + incoming.keys).filter { current[it] != incoming[it] }.sorted()
}
