package dev.workflow.app.adapters

import dev.workflow.app.domain.UnknownDefinitionException
import dev.workflow.app.ports.WorkflowCatalog
import dev.workflow.app.ports.WorkflowDefinitions
import dev.workflow.core.Definition
import org.springframework.stereotype.Component

/**
 * All definitions, keyed by name and version. Building them here validates every workflow, so an
 * invalid definition stops the app at startup rather than at first use.
 */
@Component
class WorkflowRegistry(catalogs: List<WorkflowCatalog>) : WorkflowDefinitions {
    private val byName: Map<String, Map<Int, Definition>>

    init {
        val all = catalogs.flatMap { it.workflows }.map { it.definition }
        all.groupBy { it.name to it.version }.filterValues { it.size > 1 }.keys.firstOrNull()?.let { (n, v) ->
            throw IllegalStateException("workflow '$n' v$v is registered twice")
        }
        byName = all.groupBy { it.name }.mapValues { (_, defs) -> defs.associateBy { it.version }.toSortedMap() }
    }

    override fun names(): List<String> = byName.keys.sorted()

    override fun versions(name: String): List<Int> = byName[name]?.keys?.toList() ?: throw unknown(name)

    override fun latest(name: String): Definition = byName[name]?.values?.last() ?: throw unknown(name)

    override fun get(name: String, version: Int): Definition =
        byName[name]?.get(version) ?: throw UnknownDefinitionException("no workflow '$name' v$version")

    private fun unknown(name: String) = UnknownDefinitionException("no workflow '$name'")
}
