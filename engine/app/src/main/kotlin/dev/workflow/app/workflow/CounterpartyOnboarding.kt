package dev.workflow.app.workflow

import dev.workflow.app.workflow.ComplianceDecision.CLEAR
import dev.workflow.app.workflow.ComplianceDecision.ESCALATE
import dev.workflow.app.workflow.MlroDecision.ACCEPT
import dev.workflow.app.workflow.MlroDecision.DECLINE
import dev.workflow.app.workflow.Risk.HIGH
import dev.workflow.app.workflow.Risk.LOW
import dev.workflow.app.workflow.Risk.MEDIUM
import dev.workflow.core.Workflow
import dev.workflow.app.samples.*
import dev.workflow.core.http
import dev.workflow.core.role

enum class Risk { LOW, MEDIUM, HIGH }
enum class ComplianceDecision { CLEAR, ESCALATE }
enum class MlroDecision { ACCEPT, DECLINE }

/**
 * Onboard a new counterparty.
 *
 * Shows: calling a third-party API with the built-in `http { }` task; waiting for its asynchronous
 * reply on a receive endpoint (no timeout, so the only outcome is RECEIVED); a decision on the
 * event's payload; nested paths (enhanced due diligence → MLRO) that rejoin the main flow;
 * typed, required form fields; role scope taken from vars.
 */
object CounterpartyOnboarding : Workflow("counterparty-onboarding", version = 1, {
    sample(
        "counterparty" to mapOf("name" to "Globex Ltd", "region" to "EU"),
        "services" to mapOf("screening" to "http://localhost:47831/demo/screening"),
    )

    serviceTask("request-screening") {
        label = "Request screening"
        http {
            post("{services.screening}")
            bodyFrom("counterparty")
        }
    }

    // The provider calls POST /events/screening-result {correlation:{caseId}, payload:{risk, hits}}.
    eventWait("screening-result") {
        label = "Await screening result"
        correlateBy("caseId" to "request-screening.body.caseId")
    }

    decision("risk-route", Risk::class) {
        label = "Route on risk"
        evaluate { Risk.valueOf(vars.str("screening-result.risk")) }
        describe(MEDIUM, "hits need review")
        describe(HIGH, "auto-decline")
        on {
            when (it) {
                LOW -> next()
                HIGH -> end("declined-high-risk")
                MEDIUM -> path("enhanced-due-diligence") {
                    userTask("compliance-review", ComplianceDecision::class) {
                        label = "Compliance review"
                        assignTo(role("compliance", scopeFrom = mapOf("region" to "counterparty.region")))
                        form {
                            text("findings", required = true)
                            boolean("pep", required = true)
                            number("hitsReviewed")
                        }
                        on {
                            when (it) {
                                CLEAR -> next()
                                ESCALATE -> path("mlro") {
                                    userTask("mlro-decision", MlroDecision::class) {
                                        label = "MLRO decision"
                                        assignTo(role("mlro"))
                                        form { text("rationale", required = true) }
                                        on {
                                            when (it) {
                                                ACCEPT -> next() // rejoins twice: mlro → edd → main flow
                                                DECLINE -> end("declined")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    userTask("set-up-account") {
        label = "Set up account"
        assignTo(role("onboarding-ops", scopeFrom = mapOf("region" to "counterparty.region")))
        form { text("accountId", required = true) }
    }
})
