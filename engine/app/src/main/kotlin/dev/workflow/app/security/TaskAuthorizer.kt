package dev.workflow.app.security

import dev.workflow.app.domain.ForbiddenException
import dev.workflow.app.domain.TaskRow
import dev.workflow.app.ports.RbacPort
import dev.workflow.app.ports.WorkflowStore
import dev.workflow.core.Assignment
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Authorisation for a route on one task. A person's own user task needs no permission to complete (the
 * engine's rule); a team task needs the team's role in the task's scope and then the permission in that
 * scope. An unknown or unreadable task id still needs a decision: the permission must hold globally.
 */
@Component
class TaskAuthorizer(private val authorizer: Authorizer, private val rbac: RbacPort, private val store: WorkflowStore) {
    fun require(taskId: String?, login: String, permission: String) {
        val task = taskId?.let { runCatching { UUID.fromString(it) }.getOrNull() }?.let { store.task(it) }
        if (task == null) authorizer.require(login, permission) else requireOn(task, login, permission)
    }

    private fun requireOn(task: TaskRow, login: String, permission: String) {
        val a = task.assignment
        if (permission == Permissions.TASKS_COMPLETE) mayComplete(a, login)
        if (!(permission == Permissions.TASKS_COMPLETE && a.type == Assignment.Type.USER)) authorizer.require(login, permission, a.scope)
    }

    private fun mayComplete(a: Assignment, login: String) {
        val decision = when (a.type) {
            Assignment.Type.USER -> dev.workflow.app.domain.RbacDecision(a.name == login, "task is assigned to user '${a.name}', not '$login'")
            Assignment.Type.ROLE -> rbac.holdsRole(login, a.name, a.scope)
        }
        if (!decision.allowed) throw ForbiddenException("'$login' may not complete this task", decision.reason)
    }
}
