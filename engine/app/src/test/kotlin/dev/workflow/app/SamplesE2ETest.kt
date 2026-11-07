package dev.workflow.app

import dev.workflow.app.samples.DemoBookingSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** One test per feature each sample workflow exists to show. */
class SamplesE2ETest : ApiTest() {
    private fun base() = "http://localhost:${env.getProperty("local.server.port")}"

    private fun status(id: Any?) = call("GET", "/api/v1/instances/$id")

    // ---- counterparty-onboarding ----

    private fun onboard(): Res = start(Mother.counterparty(base = base()), "counterparty-onboarding")

    private fun screeningResult(i: Res, risk: String): Res {
        val caseId = i.vars("request-screening.body.caseId") as String
        val r = call("POST", "/api/v1/events/screening-result", body = mapOf("correlation" to mapOf("caseId" to caseId), "payload" to mapOf("risk" to risk, "hits" to 2)))
        assertEquals(202, r.status, "${r.body}")
        return status(i.map["id"])
    }

    @Test
    fun `onboarding - http call, async reply, low risk goes straight to account set-up`() {
        var i = onboard()
        assertEquals("screening-result", i.map["currentStep"])
        assertTrue((i.vars("request-screening.body.caseId") as String).startsWith("CASE-"))
        i = screeningResult(i, "LOW")
        assertEquals("set-up-account", i.map["currentStep"])
        assertEquals(400, complete(i, "o.ops", "OK").status) // accountId is required
        i = complete(i, "o.ops", "OK", mapOf("accountId" to "ACC-1"))
        assertEquals("completed", i.map["endStatus"])
    }

    @Test
    fun `onboarding - high risk auto-declines`() {
        assertEquals("declined-high-risk", screeningResult(onboard(), "HIGH").map["endStatus"])
    }

    @Test
    fun `onboarding - medium risk, compliance escalates, MLRO accepts, nested paths rejoin`() {
        var i = screeningResult(onboard(), "MEDIUM")
        assertEquals("compliance-review", i.map["currentStep"])
        assertEquals(400, complete(i, "k.comply", "ESCALATE", mapOf("findings" to "x", "pep" to "yes")).status) // pep must be boolean
        i = complete(i, "k.comply", "ESCALATE", mapOf("findings" to "PEP match", "pep" to true))
        assertEquals("mlro-decision", i.map["currentStep"])
        i = complete(i, "m.lro", "ACCEPT", mapOf("rationale" to "historic role, low exposure"))
        assertEquals("set-up-account", i.map["currentStep"])
    }

    // ---- trade-booking ----

    private fun book(ref: String = "T-1", quantity: Long = 5_000_000) = start(Mother.trade(ref, quantity), "trade-booking")

    @Test
    fun `trade-booking - business outcomes route differently`() {
        assertEquals("duplicate", book(ref = "DUP-1").map["endStatus"])

        var rejected = book(ref = "T-2", quantity = 0)
        assertEquals("repair-trade", rejected.map["currentStep"])
        rejected = complete(rejected, "m.office", "RESUBMIT", mapOf("quantity" to 1_000_000))
        assertEquals("confirmation-matched", rejected.map["currentStep"]) // goto re-ran book-trade, now BOOKED
    }

    @Test
    fun `trade-booking - system down raises an incident, retry resumes`() {
        DemoBookingSystem.up.set(false)
        try {
            val i = book(ref = "T-3")
            assertEquals("failed", i.map["status"])
            DemoBookingSystem.up.set(true)
            assertEquals("confirmation-matched", call("POST", "/api/v1/instances/${i.map["id"]}/retry", "i.handler").map["currentStep"])
        } finally {
            DemoBookingSystem.up.set(true)
        }
    }

    @Test
    fun `trade-booking - timeout escalates, wait again re-arms, match completes`() {
        var i = book(ref = "T-4")
        val id = i.map["id"]
        i = call("POST", "/api/v1/timeouts/$id/confirmation-matched")
        i = status(id)
        assertEquals("chase-decision", i.map["currentStep"])
        assertEquals("ACME", i.vars("chase-counterparty.chased"))
        i = complete(i, "m.office", "WAIT_AGAIN")
        assertEquals("confirmation-matched", i.map["currentStep"])
        assertEquals(202, call("POST", "/api/v1/events/confirmation-matched", body = mapOf("correlation" to mapOf("tradeRef" to "T-4"))).status)
        i = status(id)
        assertEquals("completed", i.map["endStatus"])
        assertEquals("S-T-4", i.vars("release-to-settlement.settlementRef"))
    }

    // ---- clause-change-approval ----

    private fun change(templates: Int, regulatory: Boolean = false) =
        start(Mother.change(templates, regulatory, base()), "clause-change-approval")

    @Test
    fun `clause-change - minor change goes straight to release and publishes`() {
        var i = change(templates = 2)
        assertEquals("schedule-release", i.map["currentStep"])
        assertEquals(403, complete(i, "l.jones", "OK", mapOf("releaseWindow" to "Fri")).status)
        i = complete(i, "p.maint", "OK", mapOf("releaseWindow" to "Fri 18:00"))
        assertEquals("published", i.map["endStatus"])
        assertTrue((i.vars("publish.body.version") as Int) >= 1)
    }

    @Test
    fun `clause-change - material change runs legal then product owner, in order`() {
        var i = change(templates = 14)
        assertEquals("legal-approval", i.map["currentStep"])
        i = complete(i, "l.jones", "APPROVE")
        assertEquals("product-owner-sign-off", i.map["currentStep"])
        i = complete(i, "p.owner", "OK")
        assertEquals("schedule-release", i.map["currentStep"])
    }

    @Test
    fun `clause-change - request changes jumps forward to revise, resubmit re-classifies`() {
        var i = change(templates = 1, regulatory = true)
        assertEquals("compliance-approval", i.map["currentStep"])
        i = complete(i, "k.comply", "REQUEST_CHANGES")
        assertEquals("revise", i.map["currentStep"])
        i = complete(i, "a.ops", "RESUBMIT_CHANGE", mapOf("summary" to "narrowed scope"))
        assertEquals("compliance-approval", i.map["currentStep"])
        i = complete(i, "k.comply", "REQUEST_CHANGES")
        i = complete(i, "a.ops", "ABANDON_CHANGE", mapOf("summary" to "dropped"))
        assertEquals("abandoned", i.map["endStatus"])
    }
}
