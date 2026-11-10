package dev.workflow.app.portable

import dev.workflow.app.domain.AuditOutcome
import dev.workflow.app.domain.AuditQuery
import dev.workflow.app.domain.ExportFile
import dev.workflow.app.domain.NotFoundException
import dev.workflow.app.ports.RbacPort
import dev.workflow.app.ports.WorkflowStore
import dev.workflow.app.runtime.Audit
import dev.workflow.app.runtime.TaskService
import dev.workflow.core.InstanceState
import dev.workflow.core.InstanceStatus
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/** The newest this many runs are exported; a run's tasks, waits and events go with them. */
const val RECORDS_LIMIT = 10_000

/** Export of Records kinds: instances, tasks, waits, incidents, events and people. There is no import for them. */
@Service
class RecordExports(
    private val store: WorkflowStore,
    private val tasks: TaskService,
    private val rbac: RbacPort,
    private val audit: Audit,
    private val clock: Clock,
) {
    fun instances(): ExportFile = file("instances", runs().map(InstanceExporter::toDoc))

    fun instance(id: UUID): ExportFile {
        val run = store.instance(id) ?: throw NotFoundException("no instance $id")
        recorded("instances")
        return ExportFile("instance-$id.json", InstanceExporter.toDoc(run))
    }

    fun incidents(): ExportFile = file("incidents", runs().filter { it.status == InstanceStatus.FAILED }.map(IncidentExporter::toDoc))

    /** Open tasks the caller may read. Closed ones are in the events. */
    fun tasks(login: String): ExportFile = file("tasks", tasks.readableTasks(login).map(TaskExporter::toDoc))

    fun waits(): ExportFile = file("waits", runs().flatMap { store.openWaitsFor(UUID.fromString(it.id)) }.map(WaitExporter::toDoc))

    fun events(): ExportFile =
        file("events", runs().flatMap { run -> store.events(UUID.fromString(run.id)).map { EventExporter.toDoc(RunEvent(run.id, it)) } })

    /** The audit log, newest first (the newest [RECORDS_LIMIT] rows). Exporting it is itself audited as `audit.export`. */
    fun audit(): ExportFile = file("audit", audit.list(AuditQuery(limit = RECORDS_LIMIT)).map(AuditExporter::toDoc))

    fun people(): ExportFile = file("people", rbac.people().sortedBy { it.login }.map(PersonExporter::toDoc))

    private fun runs(): List<InstanceState> = store.instances(RECORDS_LIMIT)

    private fun file(plural: String, docs: List<Any?>): ExportFile {
        recorded(plural)
        return ExportFile("$plural-${LocalDate.now(clock)}.json", docs)
    }

    /** Every export is audited as `<resource>.export` (sec.6): who took the data out. */
    private fun recorded(plural: String) = audit.record(audit.context(), "$plural.export", plural, AuditOutcome.OK)
}
