package dev.workflow.app.portable

import dev.workflow.app.domain.Doc
import dev.workflow.app.domain.Ref
import dev.workflow.app.ports.PortableStore
import dev.workflow.app.ports.WorkflowDefinitions
import dev.workflow.core.Definition
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.Instant

/** A Config kind: what the app holds of it, how it is stored when imported, and how its references are found. Listed in dependency order. */
interface ConfigKind {
    /** The route segment and file name: `workflows`. */
    val plural: String

    val codec: Codec<*>

    /** Every document of this kind the app has, canonical, by key. */
    fun current(): Map<String, Doc>

    /** Defined in code, so an import may not change it. */
    fun locked(key: String): Boolean

    fun exists(ref: Ref): Boolean

    fun save(doc: Doc, at: Instant)
}

/** Workflow definitions: the code-defined ones, plus documents imported from another environment. */
@Component
class WorkflowKind(private val defs: WorkflowDefinitions, private val store: PortableStore, private val json: JsonMapper) : ConfigKind {
    override val plural = "workflows"
    override val codec = WorkflowCodec()

    override fun current(): Map<String, Doc> = imported() + builtIn()

    override fun locked(key: String) = key in builtIn()

    override fun exists(ref: Ref) = ref.kind != "handler" || HandlerNames.find(ref.key) != null

    override fun save(doc: Doc, at: Instant) = store.put(codec.kind, doc["key"] as String, json.writeValueAsString(canonical(doc)), at)

    private fun builtIn(): Map<String, Doc> = defs.names().flatMap { name -> defs.versions(name).map { defs.get(name, it) } }.associate(::entry)

    /** Through JSON, so a number is the same type here as in a document read from a file. */
    @Suppress("UNCHECKED_CAST")
    private fun entry(d: Definition) = codec.keyOf(d) to (canonical(json.readValue<Map<String, Any?>>(json.writeValueAsString(codec.toDoc(d)))) as Doc)

    @Suppress("UNCHECKED_CAST")
    private fun imported(): Map<String, Doc> = store.docs(codec.kind).mapValues { canonical(json.readValue<Map<String, Any?>>(it.value)) as Doc }
}
