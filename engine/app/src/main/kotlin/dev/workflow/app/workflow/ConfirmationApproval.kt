package dev.workflow.app.workflow

import dev.workflow.app.samples.NotifyRequester
import dev.workflow.app.samples.SendForSignature
import dev.workflow.app.samples.StampDocument

import dev.workflow.app.workflow.Amend.RESUBMIT
import dev.workflow.app.workflow.Amend.WITHDRAW
import dev.workflow.app.workflow.LegalReview.APPROVE
import dev.workflow.app.workflow.LegalReview.COUNTER
import dev.workflow.app.workflow.LegalReview.REJECT
import dev.workflow.app.workflow.Negotiation.AGREED
import dev.workflow.app.workflow.Negotiation.WALK_AWAY
import dev.workflow.app.workflow.Signing.REJECTED_BY_PROVIDER
import dev.workflow.app.workflow.Signing.SENT
import dev.workflow.app.workflow.Size.LARGE
import dev.workflow.app.workflow.Size.NORMAL
import dev.workflow.core.ReceivedOrTimedOut
import dev.workflow.core.ReceivedOrTimedOut.RECEIVED
import dev.workflow.core.ReceivedOrTimedOut.TIMED_OUT
import dev.workflow.core.Workflow
import dev.workflow.app.samples.*
import dev.workflow.core.role
import dev.workflow.core.userFrom
import kotlin.time.Duration.Companion.days

enum class LegalReview { APPROVE, REJECT, COUNTER }
enum class Amend { RESUBMIT, WITHDRAW }
enum class Negotiation { AGREED, WALK_AWAY }
enum class Size { LARGE, NORMAL }
enum class Signing { SENT, REJECTED_BY_PROVIDER }

/**
 * Sample: approve a trade confirmation, then send it for signature.
 * Legal can approve, reject (rework by the requester) or counter (negotiate with the counterparty).
 */
object ConfirmationApproval : Workflow("confirmation-approval", version = 1, {
    sample("doc" to mapOf("id" to "D-1", "requester" to "a.ops", "counterparty" to "ACME", "notional" to 75_000_000))

    userTask("legal-review", LegalReview::class) {
        label = "Legal review"
        assignTo(role("legal"))
        form { text("comment") }
        on {
            when (it) {
                APPROVE -> next()
                REJECT -> path("rework") {
                    serviceTask("notify-requester") {
                        label = "Notify requester"
                        handler<NotifyRequester>()
                    }
                    userTask("amend-draft", Amend::class) {
                        label = "Amend draft"
                        assignTo(userFrom("doc.requester"))
                        on {
                            when (it) {
                                RESUBMIT -> goto("legal-review")
                                WITHDRAW -> end("withdrawn")
                            }
                        }
                    }
                }
                COUNTER -> path("negotiation") {
                    userTask("negotiate", Negotiation::class) {
                        label = "Counterparty negotiation"
                        assignTo(role("counterparty-signatory", scopeFrom = mapOf("counterparty" to "doc.counterparty")))
                        form { text("proposal") }
                        on {
                            when (it) {
                                AGREED -> goto("legal-review")
                                WALK_AWAY -> end("abandoned")
                            }
                        }
                    }
                }
            }
        }
    }

    decision("size-check", Size::class) {
        label = "Size check"
        evaluate { if (vars.num("doc.notional") > 50_000_000) LARGE else NORMAL }
        describe(LARGE, "notional > 50m")
        on {
            when (it) {
                LARGE -> path("senior") {
                    userTask("desk-head-sign-off") {
                        label = "Desk head sign-off"
                        assignTo(role("desk-head"))
                    }
                }
                NORMAL -> next()
            }
        }
    }

    serviceTask("send-for-signature", Signing::class) {
        label = "Send for signature"
        handler<SendForSignature>()
        on {
            when (it) {
                SENT -> next()
                REJECTED_BY_PROVIDER -> end("signature-failed")
            }
        }
    }

    eventWait("signed", ReceivedOrTimedOut::class) {
        label = "Await signature"
        correlateBy("envelopeId" to "send-for-signature.envelopeId")
        timeout(7.days)
        on {
            when (it) {
                RECEIVED -> next()
                TIMED_OUT -> end("expired")
            }
        }
    }

    serviceTask("stamp") {
        label = "Stamp executed copy"
        handler<StampDocument>()
    }
})
