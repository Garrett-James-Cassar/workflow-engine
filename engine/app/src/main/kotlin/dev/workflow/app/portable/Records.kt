package dev.workflow.app.portable

import dev.workflow.app.domain.Doc
import dev.workflow.app.domain.EventRow
import dev.workflow.app.domain.PersonInfo
import dev.workflow.app.domain.TaskRow
import dev.workflow.app.domain.WaitRow
import dev.workflow.core.InstanceState

/** Records kinds (PORTABLE.md section 0): history, written out and never read back. Storage bookkeeping (creation times) is left out. */
object InstanceExporter : Exporter<InstanceState> {
    override val kind = "instance"

    override fun keyOf(item: InstanceState) = item.id

    override fun toDoc(item: InstanceState): Doc = envelope(
        kind, item.id,
        mapOf(
            "definition" to item.definition, "definitionVersion" to item.version, "status" to item.status.name.lowercase(),
            "currentStep" to item.currentStep, "endStatus" to item.endStatus, "vars" to item.vars,
        ),
    )
}

/** A run that stopped on a failed service task, waiting for `retry`. */
object IncidentExporter : Exporter<InstanceState> {
    override val kind = "incident"

    override fun keyOf(item: InstanceState) = item.id

    override fun toDoc(item: InstanceState): Doc =
        envelope(kind, item.id, mapOf("definition" to item.definition, "definitionVersion" to item.version, "failedStep" to item.currentStep))
}

object TaskExporter : Exporter<TaskRow> {
    override val kind = "task"

    override fun keyOf(item: TaskRow) = item.id.toString()

    override fun toDoc(item: TaskRow): Doc = envelope(
        kind, keyOf(item),
        mapOf(
            "instance" to item.instanceId.toString(), "step" to item.step, "status" to item.status, "outcome" to item.outcome,
            "assignee" to mapOf("type" to item.assignment.type.name.lowercase(), "name" to item.assignment.name, "scope" to item.assignment.scope),
            "form" to item.form, "decidedBy" to item.decidedBy, "decidedAt" to item.decidedAt?.toString(),
        ),
    )
}

object WaitExporter : Exporter<WaitRow> {
    override val kind = "wait"

    override fun keyOf(item: WaitRow) = item.id.toString()

    override fun toDoc(item: WaitRow): Doc = envelope(
        kind, keyOf(item),
        mapOf(
            "instance" to item.instanceId.toString(), "step" to item.step, "event" to item.event, "status" to item.status,
            "correlationKey" to item.correlationKey, "correlationValue" to item.correlationValue, "expiresAt" to item.expiresAt?.toString(),
        ),
    )
}

/** An entry of a run's history: [instance] is the run it belongs to. */
data class RunEvent(val instance: String, val row: EventRow)

object EventExporter : Exporter<RunEvent> {
    override val kind = "event"

    override fun keyOf(item: RunEvent) = item.row.id.toString()

    override fun toDoc(item: RunEvent): Doc = item.row.event.let {
        envelope(
            kind, keyOf(item),
            mapOf("instance" to item.instance, "type" to it.type, "step" to it.step, "outcome" to it.outcome, "actor" to it.actor, "payload" to it.payload, "at" to it.at.toString()),
        )
    }
}

object PersonExporter : Exporter<PersonInfo> {
    override val kind = "person"

    override fun keyOf(item: PersonInfo) = item.login

    override fun toDoc(item: PersonInfo): Doc = envelope(
        kind, item.login,
        mapOf("name" to item.name, "teams" to item.teams, "grants" to item.grants.map { mapOf("role" to it.role, "scope" to it.scope) }),
    )
}
