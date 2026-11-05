package dev.workflow.app.diagrams

import dev.workflow.app.api.GraphView
import dev.workflow.app.workflow.Workflows
import dev.workflow.core.MermaidExporter
import dev.workflow.core.Workflow
import dev.workflow.core.toGraph
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import java.io.File

/** The files `./gradlew diagrams` writes for one workflow: name to content. */
fun diagramFiles(workflow: Workflow): Map<String, String> {
    val def = workflow.definition
    val graph = def.toGraph()
    val json = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build()
    return mapOf(
        "${def.name}.mmd" to MermaidExporter.export(graph),
        "${def.name}.graph.json" to json.writeValueAsString(GraphView.of(graph)) + "\n",
    )
}

fun allWorkflows(): List<Workflow> = Workflows().workflows

fun main(args: Array<String>) {
    val dir = File(args.firstOrNull() ?: "docs/workflows").apply { mkdirs() }
    allWorkflows().flatMap { diagramFiles(it).entries }.forEach { (name, content) ->
        File(dir, name).writeText(content)
        println("wrote ${File(dir, name).path}")
    }
}
