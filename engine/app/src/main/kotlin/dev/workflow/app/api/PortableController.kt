package dev.workflow.app.api

import dev.workflow.app.api.dtos.ImportPlanView
import dev.workflow.app.api.dtos.ImportResultView
import dev.workflow.app.mappers.PortableMapper
import dev.workflow.app.portable.ConfigPortability
import dev.workflow.app.security.Identity
import dev.workflow.app.security.Permissions
import dev.workflow.app.security.Requires
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import dev.workflow.app.api.dtos.Name

private const val WORKFLOWS = "workflows"

/** The Config kind `workflows`: export all, export one, import preview, import (docs/RESOURCES.md). */
@RestController
class WorkflowController(private val portable: ConfigPortability) {
    @Requires(Permissions.WORKFLOWS_EXPORT, label = "Export workflow documents")
    @GetMapping("/workflows/export")
    fun export(): ResponseEntity<Any> = download(portable.exportAll(WORKFLOWS))

    @Requires(Permissions.WORKFLOWS_EXPORT, label = "Export workflow document")
    @GetMapping("/workflows/{key}/export")
    fun exportOne(@PathVariable @Name key: String): ResponseEntity<Any> = download(portable.exportOne(WORKFLOWS, key))

    @Requires(Permissions.WORKFLOWS_IMPORT, label = "Preview workflow import", heavy = true)
    @PostMapping("/workflows/import/preview")
    fun preview(@Valid @RequestBody body: Any?): ImportPlanView = PortableMapper.plan(portable.preview(body, WORKFLOWS))

    @Requires(Permissions.WORKFLOWS_IMPORT, label = "Import workflows", heavy = true)
    @PostMapping("/workflows/import")
    fun import(@Valid @RequestBody body: Any?, @RequestAttribute(Identity.LOGIN) login: String): ImportResultView =
        PortableMapper.result(portable.import(body, login, WORKFLOWS))
}

/** The promotion bundle: every Config kind in dependency order, to move a configuration between environments. */
@RestController
class PortableController(private val portable: ConfigPortability) {
    @Requires(Permissions.WORKFLOWS_EXPORT, label = "Export configuration")
    @GetMapping("/export")
    fun export(): ResponseEntity<Any> = download(portable.bundle())

    @Requires(Permissions.WORKFLOWS_IMPORT, label = "Preview configuration import", heavy = true)
    @PostMapping("/import/preview")
    fun preview(@Valid @RequestBody body: Any?): ImportPlanView = PortableMapper.plan(portable.preview(body))

    @Requires(Permissions.WORKFLOWS_IMPORT, label = "Import configuration", heavy = true)
    @PostMapping("/import")
    fun import(@Valid @RequestBody body: Any?, @RequestAttribute(Identity.LOGIN) login: String): ImportResultView =
        PortableMapper.result(portable.import(body, login))
}
