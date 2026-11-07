package dev.workflow.app.api

import dev.workflow.app.api.dtos.DefinitionSummary
import dev.workflow.app.api.dtos.DefinitionView
import dev.workflow.app.api.dtos.ExportQuery
import dev.workflow.app.api.dtos.GraphView
import dev.workflow.app.mappers.DefinitionMapper
import dev.workflow.app.mappers.ExportMapper
import dev.workflow.app.mappers.GraphMapper
import dev.workflow.app.runtime.DefinitionService
import dev.workflow.app.security.Permissions
import dev.workflow.app.security.Public
import dev.workflow.app.security.Requires
import dev.workflow.core.toGraph
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import dev.workflow.app.api.dtos.Limits
import dev.workflow.app.api.dtos.Name
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {
    @Public
    @GetMapping("/health")
    fun health() = mapOf("status" to "ok")
}

@RestController
class DefinitionController(private val definitions: DefinitionService) {

    @Requires(Permissions.WORKFLOWS_READ, label = "List workflows")
    @GetMapping("/definitions")
    fun list(): List<DefinitionSummary> = definitions.list().map(DefinitionMapper::summary)

    @Requires(Permissions.WORKFLOWS_READ, label = "Show workflow")
    @GetMapping("/definitions/{name}")
    fun get(@PathVariable @Name name: String, @RequestParam(required = false) @Min(1) @Max(Limits.MAX_VERSION) version: Int?): DefinitionView =
        DefinitionMapper.view(definitions.definition(name, version))

    @Requires(Permissions.WORKFLOWS_READ, label = "Show workflow graph")
    @GetMapping("/definitions/{name}/graph")
    fun graph(@PathVariable @Name name: String, @RequestParam(required = false) @Min(1) @Max(Limits.MAX_VERSION) version: Int?): GraphView =
        GraphMapper.view(definitions.definition(name, version).toGraph())

    /**
     * The workflow's diagram. `format=html` (and the default for browsers) returns a page that draws
     * the Mermaid picture with SVG/PNG downloads; `format=mermaid` (the default otherwise) returns
     * the Mermaid source; `format=graph` the graph JSON.
     */
    @Requires(Permissions.WORKFLOWS_READ, label = "Export workflow")
    @GetMapping("/definitions/{name}/export")
    fun export(
        @PathVariable @Name name: String,
        @Valid query: ExportQuery,
        @RequestHeader(HttpHeaders.ACCEPT, required = false) accept: String?,
    ): ResponseEntity<Any> =
        ExportMapper.render(definitions.definition(name, query.version), query.format, accept)
            .let { ResponseEntity.ok().contentType(MediaType.parseMediaType(it.mediaType)).body(it.body) }
}
