package dev.workflow.app.runtime

import dev.workflow.app.domain.DefinitionInfo
import dev.workflow.app.domain.EventRow
import dev.workflow.app.domain.InstanceDetail
import dev.workflow.app.domain.MeInfo
import dev.workflow.app.domain.PersonInfo
import dev.workflow.app.domain.TaskDetail
import dev.workflow.app.domain.TaskRow
import dev.workflow.app.ports.RbacPort
import dev.workflow.app.ports.WorkflowDefinitions
import dev.workflow.app.ports.WorkflowStore
import dev.workflow.core.Definition
import dev.workflow.core.InstanceState
import org.springframework.stereotype.Service
import java.util.UUID

/** What the catalogue holds: names, versions and a definition by name (the latest unless a version is asked for). */
@Service
class DefinitionService(private val defs: WorkflowDefinitions) {
    fun list(): List<DefinitionInfo> = defs.names().map { info(it) }

    fun definition(name: String, version: Int?): Definition = version?.let { defs.get(name, it) } ?: defs.latest(name)

    private fun info(name: String) = defs.latest(name).let { DefinitionInfo(name, it.version, defs.versions(name), it.allSteps.size) }
}

/** Reads of runs, their events and open tasks, each with the definition and the open work around it. */
@Service
class RunQueries(
    private val store: WorkflowStore,
    private val defs: WorkflowDefinitions,
    private val instances: InstanceService,
    private val tasks: TaskService,
) {
    fun detail(state: InstanceState): InstanceDetail {
        val id = UUID.fromString(state.id)
        val def = defs.get(state.definition, state.version)
        return InstanceDetail(state, store.openTasks(id).map { TaskDetail(it, def) }, store.openWaitsFor(id))
    }

    fun recent(limit: Int): List<InstanceDetail> = store.instances(limit).map { detail(it) }

    fun events(id: UUID): List<EventRow> {
        instances.get(id)
        return store.events(id)
    }

    /** Open tasks: with [mine], those [login] may complete; otherwise those inside the scopes [login] may read. */
    fun tasks(login: String, mine: Boolean, instance: UUID?): List<TaskDetail> =
        (if (mine) tasks.tasksFor(login) else tasks.readableTasks(login)).filter { instance == null || it.instanceId == instance }.map { detail(it) }

    private fun detail(task: TaskRow): TaskDetail {
        val state = instances.get(task.instanceId)
        return TaskDetail(task, defs.get(state.definition, state.version))
    }
}

/** The caller's own access, and who the console can act as. */
@Service
class AccessService(private val rbac: RbacPort) {
    fun me(login: String) = MeInfo(login, rbac.people().firstOrNull { it.login == login }?.name ?: login, rbac.permissionsOf(login))

    fun people(): List<PersonInfo> = rbac.people()
}
