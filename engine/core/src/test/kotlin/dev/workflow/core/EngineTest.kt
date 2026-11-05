package dev.workflow.core

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EngineTest {
    private val t0 = Instant.parse("2026-11-04T10:00:00Z")
    private val engine = Engine(Clock.fixed(t0, ZoneOffset.UTC))
    private val def = ConfirmationApproval.definition
    private val doc = mapOf("id" to "D-1", "requester" to "a.ops", "counterparty" to "ACME", "notional" to 75_000_000)

    private fun start(vars: Map<String, Any?> = mapOf("doc" to doc)) = engine.start(def, "i-1", vars, "tester")

    private fun Transition.complete(step: String, outcome: String, output: Map<String, Any?> = emptyMap(), actor: String = "u") =
        engine.complete(def, state, step, outcome, output, actor)

    @Test
    fun `E5 start parks on the first user task, assigned by role`() {
        val t = start()
        assertEquals(InstanceStatus.WAITING, t.state.status)
        assertEquals("legal-review", t.state.currentStep)
        assertEquals(listOf(Effect.CreateUserTask("legal-review", Assignment(Assignment.Type.ROLE, "legal", emptyMap()))), t.effects)
        assertEquals(listOf("instance-started", "step-entered"), t.events.map { it.type })
    }

    @Test
    fun `E5 reject goes down the rework path, then resubmit loops back to legal`() {
        val rejected = start().complete("legal-review", "REJECT", mapOf("comment" to "fix clause 4"), "l.jones")
        assertEquals(listOf(Effect.RunService("notify-requester")), rejected.effects)
        assertEquals("fix clause 4", Vars(rejected.state.vars).str("legal-review.comment"))
        assertEquals("l.jones", Vars(rejected.state.vars).str("legal-review.by"))

        val notified = rejected.complete("notify-requester", "OK", actor = Engine.ENGINE)
        assertEquals(listOf(Effect.CreateUserTask("amend-draft", Assignment(Assignment.Type.USER, "a.ops", emptyMap()))), notified.effects)

        val resubmitted = notified.complete("amend-draft", "RESUBMIT", actor = "a.ops")
        assertEquals("legal-review", resubmitted.state.currentStep)
        assertEquals(InstanceStatus.WAITING, resubmitted.state.status)
    }

    @Test
    fun `E5 withdraw ends with its own status`() {
        val t = start().complete("legal-review", "REJECT").complete("notify-requester", "OK").complete("amend-draft", "WITHDRAW")
        assertEquals(InstanceStatus.COMPLETED, t.state.status)
        assertEquals("withdrawn", t.state.endStatus)
        assertEquals(listOf(Effect.Ended("withdrawn")), t.effects)
    }

    @Test
    fun `E5 role scope is filled from vars`() {
        val t = start().complete("legal-review", "COUNTER")
        val task = t.effects.single() as Effect.CreateUserTask
        assertEquals(Assignment(Assignment.Type.ROLE, "counterparty-signatory", mapOf("counterparty" to "ACME")), task.assignment)
    }

    @Test
    fun `E4 decisions evaluate inline - large notional takes the senior path and rejoins`() {
        val approved = start().complete("legal-review", "APPROVE")
        assertEquals("desk-head-sign-off", approved.state.currentStep)
        assertEquals("LARGE", Vars(approved.state.vars).str("size-check.outcome"))

        val signedOff = approved.complete("desk-head-sign-off", "OK")
        assertEquals(listOf(Effect.RunService("send-for-signature")), signedOff.effects)
    }

    @Test
    fun `E4 normal notional skips the senior path`() {
        val t = start(mapOf("doc" to doc + ("notional" to 1_000))).complete("legal-review", "APPROVE")
        assertEquals("send-for-signature", t.state.currentStep)
        assertEquals("NORMAL", Vars(t.state.vars).str("size-check.outcome"))
    }

    @Test
    fun `E6 event wait parks with correlation and expiry, then completes`() {
        val sent = start(mapOf("doc" to doc + ("notional" to 1)))
            .complete("legal-review", "APPROVE")
            .complete("send-for-signature", "SENT", mapOf("envelopeId" to "ENV-9"))
        assertEquals(
            listOf(Effect.ParkForEvent("signed", "signed", "envelopeId", "ENV-9", t0.plusSeconds(7 * 24 * 3600))),
            sent.effects,
        )
        val received = sent.complete("signed", "RECEIVED", actor = "event:signed")
        assertEquals(listOf(Effect.RunService("stamp")), received.effects)
        val done = received.complete("stamp", "OK")
        assertEquals("completed", done.state.endStatus)
    }

    @Test
    fun `E6 timeout is just an outcome`() {
        val t = start(mapOf("doc" to doc + ("notional" to 1)))
            .complete("legal-review", "APPROVE")
            .complete("send-for-signature", "SENT", mapOf("envelopeId" to "ENV-9"))
            .complete("signed", "TIMED_OUT", actor = "timer")
        assertEquals("expired", t.state.endStatus)
    }

    @Test
    fun `E4 a missing var raises an incident, and retry re-enters the step`() {
        val t = start(mapOf("doc" to doc - "requester")).complete("legal-review", "REJECT").complete("notify-requester", "OK")
        assertEquals(InstanceStatus.FAILED, t.state.status)
        assertEquals("amend-draft", t.state.currentStep)
        assertEquals("incident", t.events.last().type)

        val fixed = t.state.copy(vars = t.state.vars + ("doc" to doc))
        val retried = engine.retry(def, fixed, "ops")
        assertEquals(InstanceStatus.WAITING, retried.state.status)
        assertEquals(Assignment(Assignment.Type.USER, "a.ops", emptyMap()), (retried.effects.single() as Effect.CreateUserTask).assignment)
    }

    @Test
    fun `E4 rejects outcomes for the wrong step or an unknown outcome`() {
        val t = start()
        assertFailsWith<IllegalTransitionException> { t.complete("amend-draft", "RESUBMIT") }
        assertFailsWith<IllegalTransitionException> { t.complete("legal-review", "MAYBE") }
    }
}
