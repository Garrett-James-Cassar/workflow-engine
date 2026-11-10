package dev.workflow.app.mappers

import dev.workflow.app.api.dtos.DefinitionSummary
import dev.workflow.app.api.dtos.DefinitionView
import dev.workflow.app.api.dtos.AuditView
import dev.workflow.app.api.dtos.EventView
import dev.workflow.app.api.dtos.GraphView
import dev.workflow.app.api.dtos.InstanceView
import dev.workflow.app.api.dtos.MeView
import dev.workflow.app.api.dtos.PersonView
import dev.workflow.app.api.dtos.ReassignRequest
import dev.workflow.app.api.dtos.Rendered
import dev.workflow.app.api.dtos.TaskView
import dev.workflow.app.api.dtos.WaitView
import dev.workflow.app.domain.BadRequestException
import dev.workflow.app.domain.DefinitionInfo
import dev.workflow.app.domain.EventRow
import dev.workflow.app.domain.InstanceDetail
import dev.workflow.app.domain.MeInfo
import dev.workflow.app.domain.PersonInfo
import dev.workflow.app.domain.TaskDetail
import dev.workflow.app.domain.WaitRow
import dev.workflow.core.Assignment
import dev.workflow.core.Definition
import dev.workflow.core.MermaidExporter
import dev.workflow.core.Step
import dev.workflow.core.WorkflowGraph
import dev.workflow.core.toGraph

object GraphMapper {
    fun view(g: WorkflowGraph) = GraphView(g.name, g.version, g.nodes.map(::node), g.edges.map(::edge), g.paths.map(::path))

    private fun node(n: dev.workflow.core.GraphNode) = GraphView.Node(n.id, n.kind.key, n.label, n.path, n.meta)

    private fun edge(e: dev.workflow.core.GraphEdge) = GraphView.Edge(e.from, e.to, e.outcome, e.label)

    private fun path(p: dev.workflow.core.GraphPath) = GraphView.Path(p.name, p.parent, p.branchedFrom, p.nodes)
}

object DefinitionMapper {
    fun summary(i: DefinitionInfo) = DefinitionSummary(i.name, i.latestVersion, i.versions, i.steps)

    fun view(d: Definition) = DefinitionView(d.name, d.version, d.sample, GraphMapper.view(d.toGraph()))
}

object TaskMapper {
    fun view(d: TaskDetail): TaskView = view(d.task, d.definition, d.definition.step(d.task.step) as Step.UserTask)

    private fun view(t: dev.workflow.app.domain.TaskRow, def: Definition, step: Step.UserTask) = TaskView(
        t.id, t.instanceId, def.name, t.step, step.label, assignee(t.assignment), step.outcomes, fields(step),
        t.status, t.outcome, t.decidedBy, t.createdAt, scopedTo(t.assignment), step.reassignableBy?.name,
    )

    private fun assignee(a: Assignment) = TaskView.Assignee(a.type.name.lowercase(), a.name, a.scope)

    private fun fields(step: Step.UserTask) = step.form.map { TaskView.Field(it.name, it.type.name.lowercase(), it.required) }

    private fun scopedTo(a: Assignment) = if (a.type == Assignment.Type.ROLE) "team" else "user"

    /** The new holder a reassign request names: a team (a role) or a person. */
    fun assignment(body: ReassignRequest) =
        Assignment(if (body.toTeam) Assignment.Type.ROLE else Assignment.Type.USER, body.to, body.scope)
}

object InstanceMapper {
    fun view(d: InstanceDetail): InstanceView = d.state.let {
        InstanceView(it.id, it.definition, it.version, it.status.name.lowercase(), it.currentStep, it.endStatus, it.vars, d.tasks.map(TaskMapper::view), d.waits.map(::wait))
    }

    private fun wait(w: WaitRow) =
        WaitView(w.step, w.event, w.correlationKey?.let { mapOf(it to w.correlationValue) } ?: emptyMap(), w.expiresAt)
}

object EventMapper {
    fun view(r: EventRow) = EventView(r.id, r.event.type, r.event.step, r.event.outcome, r.event.actor, r.event.payload, r.event.at)
}

object AuditMapper {
    fun view(e: dev.workflow.app.domain.AuditEntry) =
        AuditView(e.id, e.at, e.actor, e.via, e.action, e.resource, e.outcome.name.lowercase(), e.changes, e.traceId)
}

object AccessMapper {
    fun me(m: MeInfo) = MeView(m.login, m.displayName, m.permissions.map { MeView.GrantView(it.permission, it.scope) })

    fun person(p: PersonInfo) = PersonView(p.login, p.name, p.teams, p.grants.map { PersonView.RoleGrantView(it.role, it.scope) })
}

/** Draws a definition in the format the caller asked for: `html` (a page), `mermaid` (source) or `graph` (JSON). */
object ExportMapper {
    private const val HTML = "text/html"
    private const val TEXT = "text/plain"
    private const val JSON = "application/json"
    private val renderers: Map<String, (Definition) -> Rendered> = mapOf(
        "html" to { d -> Rendered(HTML, DiagramPage.render("${d.name} v${d.version}", mermaid(d))) },
        "mermaid" to { d -> Rendered(TEXT, mermaid(d)) },
        "graph" to { d -> Rendered(JSON, GraphMapper.view(d.toGraph())) },
    )

    fun render(def: Definition, format: String?, accept: String?): Rendered =
        renderers[format ?: preferred(accept)]?.invoke(def) ?: throw BadRequestException("format must be 'html', 'mermaid' or 'graph'")

    private fun preferred(accept: String?) = if (accept?.contains(HTML) == true) "html" else "mermaid"

    private fun mermaid(d: Definition) = MermaidExporter.export(d.toGraph())
}
