package dev.workflow.app.runtime

import dev.workflow.app.domain.AuditContext
import dev.workflow.app.domain.AuditEntry
import dev.workflow.app.domain.AuditOutcome
import dev.workflow.app.domain.AuditQuery
import dev.workflow.app.domain.BadRequestException
import dev.workflow.app.domain.ConflictException
import dev.workflow.app.domain.ForbiddenException
import dev.workflow.app.domain.NotFoundException
import dev.workflow.app.ports.AuditStore
import dev.workflow.app.security.CallContext
import io.opentelemetry.api.trace.Span
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/**
 * The one write path of the audit log (sec.6): `record(ctx, action, resource, outcome, changes)`, called from the service layer
 * (and from the RBAC guard for route-level 403s). `changes` is the names of changed fields only, never their values.
 * [run] wraps a use case: the `ok` row is written in the same transaction as the change, so a rolled-back change leaves no row;
 * a denial or a 4xx writes its own row afterwards.
 */
@Service
class Audit(private val store: AuditStore, private val tx: TransactionTemplate, private val clock: Clock) {
    /** Who is acting: [actor] when known, else the request's login, else `system:<component>` (ticks and other caller-less jobs). */
    fun context(actor: String? = null, component: String = "app"): AuditContext {
        val caller = CallContext.get()
        return AuditContext(actor ?: caller?.actor ?: "system:$component", caller?.via)
    }

    /** Writes one row, in the caller's transaction when there is one. */
    fun record(ctx: AuditContext, action: String, resource: String, outcome: AuditOutcome, changes: Collection<String> = emptyList()) {
        val trace = Span.current().spanContext.takeIf { it.isValid }?.traceId
        store.insert(AuditEntry(0, clock.instant(), ctx.actor, ctx.via, action, resource, outcome, changes.toList(), trace))
    }

    fun list(query: AuditQuery): List<AuditEntry> = store.list(query)

    /**
     * Runs [block] in one transaction with its `ok` row; [precheck] (the permission check) runs first, outside the transaction.
     * A [ForbiddenException] writes `denied`, a validation, conflict or not-found error writes `failed`; the error is rethrown.
     * Any other exception writes nothing (the change rolled back with it).
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> run(ctx: AuditContext, action: String, resource: String, changes: Collection<String> = emptyList(), precheck: () -> Unit = {}, block: () -> T): T =
        try {
            precheck()
            tx.execute {
                block().also { record(ctx, action, resource, AuditOutcome.OK, changes) }
            } as T
        } catch (e: ForbiddenException) {
            record(ctx, action, resource, AuditOutcome.DENIED)
            throw e
        } catch (e: RuntimeException) {
            if (e is BadRequestException || e is ConflictException || e is NotFoundException) record(ctx, action, resource, AuditOutcome.FAILED)
            throw e
        }
}
