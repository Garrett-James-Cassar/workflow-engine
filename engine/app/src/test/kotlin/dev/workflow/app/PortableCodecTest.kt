package dev.workflow.app

import dev.workflow.app.domain.Doc
import dev.workflow.app.portable.DocException
import dev.workflow.app.portable.WorkflowCodec
import dev.workflow.app.workflow.ClauseChangeApproval
import dev.workflow.app.workflow.ConfirmationApproval
import dev.workflow.app.workflow.CounterpartyOnboarding
import dev.workflow.app.workflow.TradeBooking
import dev.workflow.core.Workflow
import dev.workflow.core.http
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The workflow codec on its own: no Spring, no database. */
class PortableCodecTest {
    private val codec = WorkflowCodec()
    private val samples = listOf(ClauseChangeApproval, ConfirmationApproval, CounterpartyOnboarding, TradeBooking)

    @Test
    fun `E15 every sample workflow reads back as the document it was written as`() {
        samples.forEach {
            val doc = codec.toDoc(it.definition)
            assertEquals(doc, codec.toDoc(codec.fromDoc(doc)), it.name)
            assertEquals("${it.name}:${it.version}", doc["key"])
        }
    }

    @Test
    fun `E15 keys are sorted, so a document serialises the same way every time`() {
        val doc = codec.toDoc(TradeBooking.definition)
        assertEquals(doc.keys.sorted(), doc.keys.toList())
        assertEquals(listOf("kind", "key", "name", "sample", "steps", "version", "workflowVersion").sorted(), doc.keys.toList())
    }

    @Test
    fun `E15 a header that is not filled from vars leaves as a placeholder, never as itself`() {
        val flow = object : Workflow("with-header", 1, {
            serviceTask("call") {
                http {
                    post("{target.url}")
                    header("Authorization", "Bearer s3cret")
                    header("X-Doc", "{doc.id}")
                }
            }
        }) {}
        val text = codec.toDoc(flow.definition).toString()
        assertFalse(text.contains("s3cret"), text)
        assertTrue(text.contains("Authorization=\${CALL_AUTHORIZATION}"), text)
        assertTrue(text.contains("X-Doc={doc.id}"), text)
        val doc: Doc = codec.toDoc(flow.definition)
        assertEquals(doc, codec.toDoc(codec.fromDoc(doc)))
    }

    @Test
    fun `E15 handlers are references by class name`() {
        val refs = codec.refs(codec.toDoc(TradeBooking.definition))
        assertEquals(setOf("handler"), refs.map { it.kind }.toSet())
        assertTrue(refs.any { it.key == "dev.workflow.app.samples.BookTrade" && it.path == "/steps/0/handler" }, "$refs")
    }

    @Test
    fun `E15 a document with a wrong field is refused at its pointer`() {
        val doc = codec.toDoc(TradeBooking.definition).toMutableMap()
        doc["bogus"] = 1
        assertEquals("/bogus", assertFailsWith<DocException> { codec.fromDoc(doc) }.problems.single().path)
        val newer = codec.toDoc(TradeBooking.definition) + ("version" to 2)
        assertEquals("/version", assertFailsWith<DocException> { codec.fromDoc(newer) }.problems.single().path)
    }
}
