package dev.workflow.app.portable

import dev.workflow.app.domain.DOC_VERSION
import dev.workflow.app.domain.Doc
import dev.workflow.app.domain.DocProblem
import dev.workflow.app.domain.Ref

/** Writes one kind of thing as a portable document. Records kinds have only this half (PORTABLE.md section 0). */
interface Exporter<T> {
    /** The singular envelope kind, e.g. `workflow`. */
    val kind: String

    /** The natural key of [item], or its id when it has none. */
    fun keyOf(item: T): String

    /** The whole envelope, deterministic: no storage bookkeeping, keys sorted (see [canonical]). */
    fun toDoc(item: T): Doc
}

/** A Config kind: it also reads documents back. `toDoc(fromDoc(doc))` is `doc` for any document `toDoc` wrote. */
interface Codec<T> : Exporter<T> {
    fun fromDoc(doc: Doc): T

    fun isDoc(doc: Doc): Boolean = doc["kind"] == kind

    /** Things the document points at by key and does not carry. */
    fun refs(doc: Doc): List<Ref>
}

/** The envelope around a kind's own fields. */
fun envelope(kind: String, key: String, fields: Map<String, Any?>): Doc =
    canonical(mapOf("kind" to kind, "version" to DOC_VERSION, "key" to key) + fields) as Doc

/** Maps and lists rebuilt with keys sorted, so equal documents serialise to equal bytes. */
fun canonical(value: Any?): Any? = when (value) {
    is Map<*, *> -> value.entries.sortedBy { it.key.toString() }.associate { it.key.toString() to canonical(it.value) }
    is List<*> -> value.map(::canonical)
    else -> value
}

/** Problems in a document, thrown by the reader at the spot it found the first one (or all of them, for a definition check). */
class DocException(val problems: List<DocProblem>) : RuntimeException(problems.joinToString("; ") { "${it.path}: ${it.message}" }) {
    constructor(problem: DocProblem) : this(listOf(problem))
}

/** A position in a document being read: the value there and its JSON pointer, with typed accessors that fail with the pointer. */
class Node(val value: Any?, val path: String) {
    fun fail(message: String): Nothing = throw DocException(DocProblem(path.ifEmpty { "/" }, message))

    fun obj(): Map<String, Any?> = (value as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: fail("must be an object")

    fun items(): List<Node> = (value as? List<*>)?.mapIndexed { i, v -> Node(v, "$path/$i") } ?: fail("must be an array")

    operator fun get(key: String): Node = Node(obj()[key], "$path/$key")

    fun isNull(): Boolean = value == null

    fun text(): String = value as? String ?: fail("must be a string")

    fun int(): Int = (value as? Number)?.toInt() ?: fail("must be a number")

    fun bool(): Boolean = value as? Boolean ?: fail("must be true or false")

    fun strings(): Map<String, String> = obj().mapValues { (k, _) -> this[k].text() }

    /** The only fields this object may carry; any other is rejected at its own pointer. */
    fun only(allowed: Set<String>): Node {
        obj().keys.firstOrNull { it !in allowed }?.let { this[it].fail("unknown field") }
        return this
    }
}
