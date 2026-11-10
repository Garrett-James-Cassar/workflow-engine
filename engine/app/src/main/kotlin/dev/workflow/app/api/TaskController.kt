package dev.workflow.app.api

import dev.workflow.app.api.dtos.CompleteTaskRequest
import jakarta.validation.Valid
import dev.workflow.app.api.dtos.InstanceView
import dev.workflow.app.api.dtos.ReassignRequest
import dev.workflow.app.api.dtos.TaskView
import dev.workflow.app.mappers.InstanceMapper
import dev.workflow.app.mappers.TaskMapper
import dev.workflow.app.portable.RecordExports
import dev.workflow.app.runtime.RunQueries
import dev.workflow.app.runtime.TaskService
import dev.workflow.app.security.Identity
import dev.workflow.app.security.Permissions
import dev.workflow.app.security.Requires
import dev.workflow.app.security.ScopeFrom
import dev.workflow.core.InstanceState
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class TaskController(private val tasks: TaskService, private val queries: RunQueries, private val records: RecordExports) {

    /** Open tasks. With `mine=true`, only those the `X-User` may complete (by username or RBAC role). */
    @Requires(Permissions.TASKS_READ, ScopeFrom.LIST, label = "List tasks")
    @GetMapping("/tasks")
    fun list(
        @RequestParam(defaultValue = "false") mine: Boolean,
        @RequestParam(required = false) instance: UUID?,
        @RequestAttribute(Identity.LOGIN) login: String,
    ): List<TaskView> = queries.tasks(login, mine, instance).map(TaskMapper::view)

    /** Open tasks the caller may read, as portable documents (Records kind: export only). */
    @Requires(Permissions.TASKS_EXPORT, ScopeFrom.LIST, label = "Export tasks")
    @GetMapping("/tasks/export")
    fun export(@RequestAttribute(Identity.LOGIN) login: String): ResponseEntity<Any> = download(records.tasks(login))

    /** Move an open task to a user or a team. Needs the task's reassign permission (ops by default). */
    @Requires(Permissions.TASKS_REASSIGN, ScopeFrom.TASK, label = "Reassign task")
    @PostMapping("/tasks/{id}/reassign")
    fun reassign(
        @PathVariable id: UUID,
        @Valid @RequestBody body: ReassignRequest,
        @RequestAttribute(Identity.LOGIN) login: String,
    ): InstanceView = view(tasks.reassignTask(id, login, TaskMapper.assignment(body)))

    @Requires(Permissions.TASKS_COMPLETE, ScopeFrom.TASK, label = "Complete task")
    @PostMapping("/tasks/{id}/complete")
    fun complete(
        @PathVariable id: UUID,
        @Valid @RequestBody body: CompleteTaskRequest,
        @RequestAttribute(Identity.LOGIN) login: String,
    ): InstanceView = view(tasks.completeTask(id, login, body.outcome, body.form))

    private fun view(state: InstanceState) = InstanceMapper.view(queries.detail(state))
}
