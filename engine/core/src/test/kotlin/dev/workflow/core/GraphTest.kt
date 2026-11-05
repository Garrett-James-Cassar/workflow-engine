package dev.workflow.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphTest {
    private val graph = ConfirmationApproval.definition.toGraph()

    private fun edge(from: String, to: String, outcome: String?) = GraphEdge(from, to, outcome, outcome)

    @Test
    fun `E3 one edge per outcome, labelled only when a step has several`() {
        val expected = listOf(
            GraphEdge("start", "legal-review", null, null),
            edge("legal-review", "size-check", "APPROVE"),
            edge("legal-review", "notify-requester", "REJECT"),
            edge("legal-review", "negotiate", "COUNTER"),
            GraphEdge("notify-requester", "amend-draft", null, null),
            edge("amend-draft", "legal-review", "RESUBMIT"),
            edge("amend-draft", "end:withdrawn", "WITHDRAW"),
            edge("negotiate", "legal-review", "AGREED"),
            edge("negotiate", "end:abandoned", "WALK_AWAY"),
            GraphEdge("size-check", "desk-head-sign-off", "LARGE", "LARGE: notional > 50m"),
            edge("size-check", "send-for-signature", "NORMAL"),
            GraphEdge("desk-head-sign-off", "send-for-signature", null, null),
            edge("send-for-signature", "signed", "SENT"),
            edge("send-for-signature", "end:signature-failed", "REJECTED_BY_PROVIDER"),
            edge("signed", "stamp", "RECEIVED"),
            edge("signed", "end:expired", "TIMED_OUT"),
            GraphEdge("stamp", "end:completed", null, null),
        )
        assertEquals(expected, graph.edges)
    }

    @Test
    fun `E3 nodes carry kind, path and assignment metadata`() {
        val byId = graph.nodes.associateBy { it.id }
        assertEquals(NodeKind.USER_TASK, byId.getValue("legal-review").kind)
        assertEquals("Legal review", byId.getValue("legal-review").label)
        assertEquals("legal", byId.getValue("legal-review").meta["role"])
        assertEquals("doc.requester", byId.getValue("amend-draft").meta["userFrom"])
        assertEquals("rework", byId.getValue("amend-draft").path)
        assertEquals(NodeKind.DECISION, byId.getValue("size-check").kind)
        assertEquals("PT168H", byId.getValue("signed").meta["timeout"])
        assertEquals(
            listOf("end:withdrawn", "end:abandoned", "end:signature-failed", "end:expired", "end:completed"),
            graph.nodes.filter { it.kind == NodeKind.END }.map { it.id },
        )
        assertEquals(listOf("rework", "negotiation", "senior"), graph.paths.map { it.name })
    }

    @Test
    fun `E3 mermaid export draws shapes, subgraphs and labelled arrows`() {
        val mmd = MermaidExporter.export(graph)
        assertTrue(mmd.startsWith("---\nconfig:"))
        listOf(
            "%% confirmation-approval v1",
            "flowchart LR",
            "curve: linear",
            "n_legal_review(\"#nbsp;",
            "👥#nbsp;",
            "<br/>Legal review<br/>#nbsp;<br/><i>team · legal</i>\")",
            "⚙#nbsp;",
            "✉#nbsp;",
            "<br/>signed<br/>#nbsp;\")",
            "n_size_check{\"✕<br/>size-check\"}",
            "n_start((\" \"))",
            "n_end_signature_failed((\"signature<br/>failed\"))",
            "subgraph p_rework [\"rework\"]",
            "n_legal_review -->|\"REJECT\"| n_notify_requester",
            "n_size_check -->|\"LARGE: notional #gt; 50m\"| n_desk_head_sign_off",
            "n_desk_head_sign_off --> n_send_for_signature",
            "classDef userTask fill:#fbe7ee",
            "classDef serviceTask fill:#ffffff",
            "class n_size_check gateway",
            "style p_rework fill:none,stroke:#9aa29e,stroke-width:1px,stroke-dasharray:6 4",
        ).forEach { assertTrue(it in mmd, "missing `$it` in:\n$mmd") }
    }
}
