package dev.workflow.app.api

import dev.workflow.app.portable.RecordExports
import dev.workflow.app.security.Permissions
import dev.workflow.app.security.Requires
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** Records kinds that have no other routes of their own: export only (docs/RESOURCES.md). */
@RestController
class IncidentController(private val records: RecordExports) {
    @Requires(Permissions.INCIDENTS_EXPORT, label = "Export incidents")
    @GetMapping("/incidents/export")
    fun export(): ResponseEntity<Any> = download(records.incidents())
}

@RestController
class WaitController(private val records: RecordExports) {
    @Requires(Permissions.WAITS_EXPORT, label = "Export waits")
    @GetMapping("/waits/export")
    fun export(): ResponseEntity<Any> = download(records.waits())
}
