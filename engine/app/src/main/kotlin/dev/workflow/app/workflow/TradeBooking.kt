package dev.workflow.app.workflow

import dev.workflow.app.workflow.Booking.BOOKED
import dev.workflow.app.workflow.Booking.DUPLICATE
import dev.workflow.app.workflow.Booking.REJECTED
import dev.workflow.app.workflow.Chase.CANCEL
import dev.workflow.app.workflow.Chase.FORCE_MATCH
import dev.workflow.app.workflow.Chase.WAIT_AGAIN
import dev.workflow.app.workflow.Repair.ABANDON
import dev.workflow.app.workflow.Repair.RESUBMIT
import dev.workflow.core.ReceivedOrTimedOut
import dev.workflow.core.ReceivedOrTimedOut.RECEIVED
import dev.workflow.core.ReceivedOrTimedOut.TIMED_OUT
import dev.workflow.core.Workflow
import dev.workflow.app.samples.*
import dev.workflow.core.role
import kotlin.time.Duration.Companion.minutes

enum class Booking { BOOKED, DUPLICATE, REJECTED }
enum class Repair { RESUBMIT, ABANDON }
enum class Chase { WAIT_AGAIN, FORCE_MATCH, CANCEL }

/**
 * Book a trade downstream, then wait for the counterparty's confirmation to match.
 *
 * Shows: a custom handler returning business outcomes (BOOKED / DUPLICATE / REJECTED), each routed
 * differently; technical failure (system down) retried, then raised as an incident you clear with
 * `POST /instances/{id}/retry`; a timeout routed into an escalation path instead of an end; `goto`
 * backwards to re-run a service task or re-arm a wait.
 */
object TradeBooking : Workflow("trade-booking", version = 1, {
    sample("trade" to mapOf("ref" to "T-1001", "quantity" to 5_000_000, "counterparty" to "ACME"))

    serviceTask("book-trade", Booking::class) {
        label = "Book in trading system"
        handler<BookTrade>()
        retries = 2
        on {
            when (it) {
                BOOKED -> next()
                DUPLICATE -> end("duplicate")
                REJECTED -> path("repair") {
                    userTask("repair-trade", Repair::class) {
                        label = "Repair trade"
                        assignTo(role("middle-office"))
                        form {
                            number("quantity", required = true)
                            text("note")
                        }
                        on {
                            when (it) {
                                RESUBMIT -> goto("book-trade")
                                ABANDON -> end("abandoned")
                            }
                        }
                    }
                }
            }
        }
    }

    eventWait("confirmation-matched", ReceivedOrTimedOut::class) {
        label = "Await matched confirmation"
        correlateBy("tradeRef" to "book-trade.tradeRef")
        timeout(1.minutes)
        on {
            when (it) {
                RECEIVED -> next()
                TIMED_OUT -> path("chase") {
                    serviceTask("chase-counterparty") {
                        label = "Chase counterparty"
                        handler<ChaseCounterparty>()
                    }
                    userTask("chase-decision", Chase::class) {
                        label = "Unmatched: decide"
                        assignTo(role("middle-office"))
                        on {
                            when (it) {
                                WAIT_AGAIN -> goto("confirmation-matched")
                                FORCE_MATCH -> next()
                                CANCEL -> end("cancelled")
                            }
                        }
                    }
                }
            }
        }
    }

    serviceTask("release-to-settlement") {
        label = "Release to settlement"
        handler<ReleaseToSettlement>()
    }
})
