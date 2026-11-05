package dev.workflow.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class DefinitionTest {
    private val def = ConfirmationApproval.definition

    @Test
    fun `E2 indexes main flow and paths`() {
        assertEquals(listOf("legal-review", "size-check", "send-for-signature", "signed", "stamp"), def.steps.map { it.id })
        assertEquals(
            listOf(
                "legal-review", "notify-requester", "amend-draft", "negotiate",
                "size-check", "desk-head-sign-off", "send-for-signature", "signed", "stamp",
            ),
            def.allSteps.map { it.id },
        )
        assertEquals("rework", def.step("amend-draft").path)
    }

    @Test
    fun `E2 resolves next, goto, end and path routes`() {
        assertEquals(Target.ToStep("size-check"), def.resolve("legal-review", "APPROVE"))
        assertEquals(Target.ToStep("notify-requester"), def.resolve("legal-review", "REJECT"))
        assertEquals(Target.ToStep("amend-draft"), def.resolve("notify-requester", "OK"))
        assertEquals(Target.ToStep("legal-review"), def.resolve("amend-draft", "RESUBMIT"))
        assertEquals(Target.ToEnd("withdrawn"), def.resolve("amend-draft", "WITHDRAW"))
        assertEquals(Target.ToEnd("completed"), def.resolve("stamp", "OK"))
    }

    @Test
    fun `E2 a path that runs off its end rejoins after the branching step`() {
        assertEquals(Target.ToStep("send-for-signature"), def.resolve("desk-head-sign-off", "OK"))
    }

    @Test
    fun `E2 single-outcome steps default to next`() {
        assertEquals(listOf("OK"), def.step("stamp").outcomes)
        assertEquals(Route.Next, def.step("stamp").routes["OK"])
    }

    private fun invalid(block: WorkflowBuilder.() -> Unit): List<String> {
        val wf = object : Workflow("bad", 1, block) {}
        return assertFailsWith<WorkflowDefinitionException> { wf.definition }.errors
    }

    @Test
    fun `E2 rejects goto to an unknown step`() {
        val errors = invalid {
            userTask("a", LegalReview::class) {
                assignTo(user("x"))
                on {
                    when (it) {
                        LegalReview.APPROVE -> next()
                        LegalReview.REJECT -> goto("nope")
                        LegalReview.COUNTER -> end("countered")
                    }
                }
            }
        }
        assertEquals(listOf("step 'a' outcome REJECT: goto unknown step 'nope'"), errors)
    }

    @Test
    fun `E2 rejects duplicate step ids, including inside paths`() {
        val errors = invalid {
            userTask("a", Amend::class) {
                assignTo(user("x"))
                on {
                    when (it) {
                        Amend.RESUBMIT -> path("p") { userTask("a") { assignTo(user("y")) } }
                        Amend.WITHDRAW -> end("withdrawn")
                    }
                }
            }
        }
        assertEquals(listOf("duplicate step id 'a'"), errors)
    }

    @Test
    fun `E2 rejects unreachable steps`() {
        val errors = invalid {
            userTask("a", Amend::class) {
                assignTo(user("x"))
                on {
                    when (it) {
                        Amend.RESUBMIT -> end("done")
                        Amend.WITHDRAW -> end("withdrawn")
                    }
                }
            }
            userTask("orphan") { assignTo(user("x")) }
        }
        assertEquals(listOf("step 'orphan' is unreachable"), errors)
    }

    @Test
    fun `E2 rejects a timeout without a TIMED_OUT route and vice versa`() {
        assertTrue(
            invalid { eventWait("w") { timeout(1.hours) } }.single().contains("ReceivedOrTimedOut"),
        )
        val errors = invalid {
            eventWait("w", ReceivedOrTimedOut::class) {
                on {
                    when (it) {
                        ReceivedOrTimedOut.RECEIVED -> next()
                        ReceivedOrTimedOut.TIMED_OUT -> end("expired")
                    }
                }
            }
        }
        assertEquals(listOf("event wait 'w' routes TIMED_OUT but has no timeout(…)"), errors)
    }

    @Test
    fun `E2 rejects a service task with nothing linked, and empty paths`() {
        assertEquals(listOf("service task 's' has no handler<…>() or http { }"), invalid { serviceTask("s") {} })
        val errors = invalid {
            userTask("a", Amend::class) {
                assignTo(user("x"))
                on {
                    when (it) {
                        Amend.RESUBMIT -> path("empty") {}
                        Amend.WITHDRAW -> next()
                    }
                }
            }
        }
        assertEquals(listOf("path 'empty' (from 'a') has no steps"), errors)
    }

    @Test
    fun `E2 a user task needs an assignee`() {
        assertFailsWith<WorkflowDefinitionException> {
            object : Workflow("bad", 1, { userTask("a") {} }) {}.definition
        }
    }
}
