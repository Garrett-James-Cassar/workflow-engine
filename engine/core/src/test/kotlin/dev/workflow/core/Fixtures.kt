package dev.workflow.core

import dev.workflow.core.Amend.RESUBMIT
import dev.workflow.core.Amend.WITHDRAW
import dev.workflow.core.LegalReview.APPROVE
import dev.workflow.core.LegalReview.COUNTER
import dev.workflow.core.LegalReview.REJECT
import dev.workflow.core.Negotiation.AGREED
import dev.workflow.core.Negotiation.WALK_AWAY
import dev.workflow.core.ReceivedOrTimedOut.RECEIVED
import dev.workflow.core.ReceivedOrTimedOut.TIMED_OUT
import dev.workflow.core.Signing.REJECTED_BY_PROVIDER
import dev.workflow.core.Signing.SENT
import dev.workflow.core.Size.LARGE
import dev.workflow.core.Size.NORMAL
import kotlin.time.Duration.Companion.days

enum class LegalReview { APPROVE, REJECT, COUNTER }
enum class Amend { RESUBMIT, WITHDRAW }
enum class Negotiation { AGREED, WALK_AWAY }
enum class Size { LARGE, NORMAL }
enum class Signing { SENT, REJECTED_BY_PROVIDER }

class NotifyRequester : ServiceHandler<Done> {
    override fun execute(ctx: ServiceContext) = ServiceResult.done()
}

class SendForSignature : ServiceHandler<Signing> {
    override fun execute(ctx: ServiceContext) = ServiceResult(SENT, mapOf("envelopeId" to "ENV-1"))
}

class Stamp : ServiceHandler<Done> {
    override fun execute(ctx: ServiceContext) = ServiceResult.done()
}

/** The plan's example, used across core tests. */
object ConfirmationApproval : Workflow("confirmation-approval", version = 1, {
    sample("doc" to mapOf("id" to "D-1", "requester" to "a.ops", "counterparty" to "ACME", "notional" to 75_000_000))

    userTask("legal-review", LegalReview::class) {
        label = "Legal review"
        assignTo(role("legal"))
        form { text("comment") }
        on {
            when (it) {
                APPROVE -> next()
                COUNTER -> path("negotiation") {
                    userTask("negotiate", Negotiation::class) {
                        assignTo(role("counterparty-signatory", scopeFrom = mapOf("counterparty" to "doc.counterparty")))
                        on {
                            when (it) {
                                AGREED -> goto("legal-review")
                                WALK_AWAY -> end("abandoned")
                            }
                        }
                    }
                }
                REJECT -> path("rework") {
                    serviceTask("notify-requester") { handler<NotifyRequester>() }
                    userTask("amend-draft", Amend::class) {
                        assignTo(userFrom("doc.requester"))
                        on {
                            when (it) {
                                RESUBMIT -> goto("legal-review")
                                WITHDRAW -> end("withdrawn")
                            }
                        }
                    }
                }
            }
        }
    }

    decision("size-check", Size::class) {
        evaluate { if (vars.num("doc.notional") > 50_000_000) LARGE else NORMAL }
        describe(LARGE, "notional > 50m")
        on {
            when (it) {
                LARGE -> path("senior") { userTask("desk-head-sign-off") { assignTo(role("desk-head")) } }
                NORMAL -> next()
            }
        }
    }

    serviceTask("send-for-signature", Signing::class) {
        handler<SendForSignature>()
        on {
            when (it) {
                SENT -> next()
                REJECTED_BY_PROVIDER -> end("signature-failed")
            }
        }
    }

    eventWait("signed", ReceivedOrTimedOut::class) {
        correlateBy("envelopeId" to "send-for-signature.envelopeId")
        timeout(7.days)
        on {
            when (it) {
                RECEIVED -> next()
                TIMED_OUT -> end("expired")
            }
        }
    }

    serviceTask("stamp") { handler<Stamp>() }
})
