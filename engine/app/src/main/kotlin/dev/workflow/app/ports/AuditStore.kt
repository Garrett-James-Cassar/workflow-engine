package dev.workflow.app.ports

import dev.workflow.app.domain.AuditEntry
import dev.workflow.app.domain.AuditQuery

/** The audit log is append-only: insert and list, nothing else. `insert` joins the caller's transaction. */
interface AuditStore {
    fun insert(entry: AuditEntry)

    fun list(query: AuditQuery): List<AuditEntry>
}
