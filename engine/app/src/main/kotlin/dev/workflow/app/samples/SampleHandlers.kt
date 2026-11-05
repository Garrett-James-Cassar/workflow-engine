package dev.workflow.app.samples

import dev.workflow.app.workflow.Signing
import dev.workflow.core.Done
import dev.workflow.core.ServiceContext
import dev.workflow.core.ServiceHandler
import dev.workflow.core.ServiceResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock

/** Demo: logs instead of calling notifications-hub. */
@Component
class NotifyRequester : ServiceHandler<Done> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(ctx: ServiceContext): ServiceResult<Done> {
        val requester = ctx.vars.str("doc.requester")
        log.info("notify {}: legal rejected {} ({})", requester, ctx.vars.str("doc.id"), ctx.vars["legal-review.comment"])
        return ServiceResult.done("notified" to requester)
    }
}

/**
 * Demo e-signature provider: issues an envelope id. Set `doc.providerRejects=true` to get
 * REJECTED_BY_PROVIDER. A real one would call DocuSign or Adobe Sign here.
 */
@Component
class SendForSignature : ServiceHandler<Signing> {
    override fun execute(ctx: ServiceContext): ServiceResult<Signing> {
        if (ctx.vars["doc.providerRejects"] == true) return ServiceResult(Signing.REJECTED_BY_PROVIDER, mapOf("reason" to "demo"))
        return ServiceResult(Signing.SENT, mapOf("envelopeId" to "ENV-${ctx.instanceId.take(8)}"))
    }
}

@Component
class StampDocument(private val clock: Clock) : ServiceHandler<Done> {
    override fun execute(ctx: ServiceContext): ServiceResult<Done> = ServiceResult.done("stampedAt" to clock.instant().toString())
}
