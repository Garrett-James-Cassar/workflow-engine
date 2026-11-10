package dev.workflow.app.api

import dev.workflow.app.api.dtos.AuditView
import dev.workflow.app.api.dtos.Limits
import dev.workflow.app.domain.AuditQuery
import dev.workflow.app.mappers.AuditMapper
import dev.workflow.app.portable.RecordExports
import dev.workflow.app.runtime.Audit
import dev.workflow.app.security.Permissions
import dev.workflow.app.security.Requires
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/** The audit log (sec.6): read and export only. There is no route that changes or deletes a row. Records kind in docs/RESOURCES.md. */
@RestController
class AuditController(private val audit: Audit, private val records: RecordExports) {
    @Requires(Permissions.AUDIT_READ, label = "List audit log")
    @GetMapping("/audit")
    fun list(
        @RequestParam(required = false) @Size(max = Limits.FILTER) actor: String?,
        @RequestParam(required = false) @Size(max = Limits.FILTER) action: String?,
        @RequestParam(required = false) @Size(max = Limits.FILTER) resource: String?,
        @RequestParam(required = false) @Pattern(regexp = INSTANT) from: String?,
        @RequestParam(required = false) @Pattern(regexp = INSTANT) to: String?,
        @RequestParam(defaultValue = "50") @Min(1) @Max(Limits.MAX_PAGE) limit: Int,
        @RequestParam(defaultValue = "0") @Min(0) offset: Int,
    ): List<AuditView> = audit.list(AuditQuery(actor, action, resource, from?.let(Instant::parse), to?.let(Instant::parse), limit, offset)).map(AuditMapper::view)

    @Requires(Permissions.AUDIT_EXPORT, label = "Export audit log")
    @GetMapping("/audit/export")
    fun export(): ResponseEntity<Any> = download(records.audit())

    private companion object {
        /** An ISO-8601 instant in UTC, e.g. `2026-11-10T08:00:00Z` (what the studio and `audit.export` write). */
        const val INSTANT = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d{1,9})?)?Z$"
    }
}
