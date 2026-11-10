package dev.workflow.app.domain

import java.time.Instant

/** `ok`, `denied` (an authz 403) or `failed` (a validation 4xx, logged without its body). */
enum class AuditOutcome { OK, DENIED, FAILED }

/** Who acted and through which fleet app: the actor is never null (callers with no login are `system:<component>`). */
data class AuditContext(val actor: String, val via: String?)

/** One audit row (SECURITY.md sec.6). [changes] holds the names of changed fields, never their values. */
data class AuditEntry(
    val id: Long,
    val at: Instant,
    val actor: String,
    val via: String?,
    val action: String,
    val resource: String,
    val outcome: AuditOutcome,
    val changes: List<String>,
    val traceId: String?,
)

/** Filters of `GET /audit`; every one is optional. */
data class AuditQuery(
    val actor: String? = null,
    val action: String? = null,
    val resource: String? = null,
    val from: Instant? = null,
    val to: Instant? = null,
    val limit: Int = 50,
    val offset: Int = 0,
)
