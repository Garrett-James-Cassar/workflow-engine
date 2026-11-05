package dev.workflow.app.workflow

import dev.workflow.app.workflow.Impact.MATERIAL
import dev.workflow.app.workflow.Impact.MINOR
import dev.workflow.app.workflow.Impact.REGULATORY
import dev.workflow.app.workflow.Review.APPROVE
import dev.workflow.app.workflow.Review.REQUEST_CHANGES
import dev.workflow.app.workflow.Revision.ABANDON_CHANGE
import dev.workflow.app.workflow.Revision.RESUBMIT_CHANGE
import dev.workflow.core.Done
import dev.workflow.core.Workflow
import dev.workflow.app.samples.*
import dev.workflow.core.http
import dev.workflow.core.role
import dev.workflow.core.user
import dev.workflow.core.userFrom

enum class Impact { MINOR, MATERIAL, REGULATORY }
enum class Review { APPROVE, REQUEST_CHANGES }
enum class Revision { RESUBMIT_CHANGE, ABANDON_CHANGE }

/**
 * Govern a change to a clause in the library.
 *
 * Shows: a three-way decision whose paths are sequences of approvals (each runs in order, then
 * rejoins); assignment by role, by fixed username and by a username held in vars; a forward
 * `goto` into a step that sits after an explicit `end` (reachable only by jumping there); an
 * explicit end on a single-outcome step; a loop that re-classifies after a revision.
 */
object ClauseChangeApproval : Workflow("clause-change-approval", version = 1, {
    sample(
        "change" to mapOf(
            "clauseId" to "governing-law",
            "author" to "a.ops",
            "productOwner" to "p.owner",
            "affectedTemplates" to 14,
            "regulatory" to false,
        ),
        "services" to mapOf("publish" to "http://localhost:47831/demo/clause-store/publish"),
    )

    decision("classify", Impact::class) {
        label = "Classify impact"
        evaluate {
            when {
                vars.bool("change.regulatory") -> REGULATORY
                vars.num("change.affectedTemplates") > 10 -> MATERIAL
                else -> MINOR
            }
        }
        describe(MATERIAL, "> 10 templates")
        describe(REGULATORY, "regulatory flag")
        on {
            when (it) {
                MINOR -> next()
                MATERIAL -> path("material-review") {
                    userTask("legal-approval", Review::class) {
                        label = "Legal approval"
                        assignTo(role("legal"))
                        form { text("comment") }
                        on {
                            when (it) {
                                APPROVE -> next()
                                REQUEST_CHANGES -> goto("revise")
                            }
                        }
                    }
                    userTask("product-owner-sign-off") {
                        label = "Product owner sign-off"
                        assignTo(userFrom("change.productOwner"))
                    }
                }
                REGULATORY -> path("regulatory-review") {
                    userTask("compliance-approval", Review::class) {
                        label = "Compliance approval"
                        assignTo(role("compliance", scope = mapOf("region" to "EU")))
                        on {
                            when (it) {
                                APPROVE -> next()
                                REQUEST_CHANGES -> goto("revise")
                            }
                        }
                    }
                    userTask("legal-approval-regulatory", Review::class) {
                        label = "Legal approval (regulatory)"
                        assignTo(role("legal"))
                        on {
                            when (it) {
                                APPROVE -> next()
                                REQUEST_CHANGES -> goto("revise")
                            }
                        }
                    }
                }
            }
        }
    }

    userTask("schedule-release") {
        label = "Schedule release"
        assignTo(user("p.maint"))
        form { text("releaseWindow", required = true) }
    }

    serviceTask("publish", Done::class) {
        label = "Publish to clause library"
        http {
            post("{services.publish}")
            bodyFrom("change")
        }
        on { when (it) { Done.OK -> end("published") } }
    }

    // Only reachable via goto("revise"): the main flow ends at "publish" above.
    userTask("revise", Revision::class) {
        label = "Revise change"
        assignTo(userFrom("change.author"))
        form { text("summary", required = true) }
        on {
            when (it) {
                RESUBMIT_CHANGE -> goto("classify")
                ABANDON_CHANGE -> end("abandoned")
            }
        }
    }
})
