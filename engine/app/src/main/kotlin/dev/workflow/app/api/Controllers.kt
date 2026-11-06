package dev.workflow.app.api

import dev.workflow.app.persistence.Store
import dev.workflow.app.rbac.RbacPort
import dev.workflow.app.rbac.RbacUnavailableException
import dev.workflow.app.security.Authenticated
import dev.workflow.app.security.Identity
import dev.workflow.app.security.Permissions
import dev.workflow.app.security.Public
import dev.workflow.app.security.Requires
import dev.workflow.app.security.ScopeFrom
import dev.workflow.app.registry.UnknownDefinitionException
import dev.workflow.app.registry.WorkflowRegistry
import dev.workflow.app.runtime.BadRequestException
import dev.workflow.app.runtime.ConflictException
import dev.workflow.app.runtime.ForbiddenException
import dev.workflow.app.runtime.InstanceService
import dev.workflow.app.runtime.NotFoundException
import dev.workflow.app.runtime.UnauthenticatedException
import dev.workflow.core.InstanceState
import dev.workflow.core.MermaidExporter
import dev.workflow.core.toGraph
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID

const val USER_HEADER = "X-User"

@RestController
class HealthController {
    @Public
    @GetMapping("/health")
    fun health() = mapOf("status" to "ok")
}

@RestController
class DefinitionController(private val registry: WorkflowRegistry) {

    @Requires(Permissions.WORKFLOWS_READ)
    @GetMapping("/definitions")
    fun list() = registry.names().map { name ->
        val latest = registry.latest(name)
        DefinitionSummary(name, latest.version, registry.versions(name), latest.allSteps.size)
    }

    @Requires(Permissions.WORKFLOWS_READ)
    @GetMapping("/definitions/{name}")
    fun get(@PathVariable name: String, @RequestParam(required = false) version: Int?): DefinitionView {
        val def = version?.let { registry.get(name, it) } ?: registry.latest(name)
        return DefinitionView(def.name, def.version, def.sample, GraphView.of(def.toGraph()))
    }

    @Requires(Permissions.WORKFLOWS_READ)
    @GetMapping("/definitions/{name}/graph")
    fun graph(@PathVariable name: String, @RequestParam(required = false) version: Int?): GraphView =
        GraphView.of((version?.let { registry.get(name, it) } ?: registry.latest(name)).toGraph())

    /**
     * The workflow's diagram. `format=html` (and the default for browsers) returns a page that draws
     * the Mermaid picture with SVG/PNG downloads; `format=mermaid` (the default otherwise) returns
     * the Mermaid source; `format=graph` the graph JSON.
     */
    @Requires(Permissions.WORKFLOWS_READ)
    @GetMapping("/definitions/{name}/export")
    fun export(
        @PathVariable name: String,
        @RequestParam(required = false) version: Int?,
        @RequestParam(required = false) format: String?,
        @RequestHeader(HttpHeaders.ACCEPT, required = false) accept: String?,
    ): ResponseEntity<Any> {
        val def = version?.let { registry.get(name, it) } ?: registry.latest(name)
        val graph = def.toGraph()
        val chosen = format ?: if (accept?.contains(MediaType.TEXT_HTML_VALUE) == true) "html" else "mermaid"
        return when (chosen) {
            "html" -> ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .body(DiagramPage.render("${def.name} v${def.version}", MermaidExporter.export(graph)))
            "mermaid" -> ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(MermaidExporter.export(graph))
            "graph" -> ResponseEntity.ok(GraphView.of(graph))
            else -> throw BadRequestException("format must be 'html', 'mermaid' or 'graph'")
        }
    }
}

@RestController
class InstanceController(
    private val instances: InstanceService,
    private val registry: WorkflowRegistry,
    private val store: Store,
) {

    @Requires(Permissions.INSTANCES_START)
    @PostMapping("/definitions/{name}/instances")
    @ResponseStatus(HttpStatus.CREATED)
    fun start(
        @PathVariable name: String,
        @RequestBody(required = false) body: StartRequest?,
        @RequestAttribute(Identity.LOGIN) login: String,
    ): InstanceView = view(instances.start(name, body?.vars ?: emptyMap(), login))

    @Requires(Permissions.INSTANCES_READ)
    @GetMapping("/instances")
    fun list(@RequestParam(defaultValue = "50") limit: Int) = store.instances(limit.coerceIn(1, 500)).map { view(it) }

    @Requires(Permissions.INSTANCES_READ)
    @GetMapping("/instances/{id}")
    fun get(@PathVariable id: UUID): InstanceView = view(instances.get(id))

    @Requires(Permissions.INSTANCES_READ)
    @GetMapping("/instances/{id}/events")
    fun events(@PathVariable id: UUID): List<EventView> {
        instances.get(id)
        return store.events(id).map { EventView.of(it) }
    }

    /** Support restart at a user task. Needs the workflow-support role. */
    @Requires(Permissions.INSTANCES_RESTART)
    @PostMapping("/instances/{id}/restart")
    fun restart(
        @PathVariable id: UUID,
        @RequestBody body: RestartRequest,
        @RequestAttribute(Identity.LOGIN) login: String,
    ): InstanceView = view(instances.restartAt(id, login, body.step))

    /** Re-run the step an incident stopped at. */
    @Requires(Permissions.INSTANCES_RETRY)
    @PostMapping("/instances/{id}/retry")
    fun retry(@PathVariable id: UUID, @RequestAttribute(Identity.LOGIN) login: String): InstanceView =
        view(instances.retry(id, login))

    private fun view(state: InstanceState): InstanceView {
        val id = UUID.fromString(state.id)
        val def = registry.get(state.definition, state.version)
        return InstanceView.of(
            state,
            store.openTasks(id).map { TaskView.of(it, def) },
            store.openWaitsFor(id).map { WaitView.of(it) },
        )
    }
}

@RestController
class TaskController(
    private val instances: InstanceService,
    private val registry: WorkflowRegistry,
    private val store: Store,
) {

    /** Open tasks. With `mine=true`, only those the `X-User` may complete (by username or RBAC role). */
    @Requires(Permissions.TASKS_READ)
    @GetMapping("/tasks")
    fun list(
        @RequestParam(defaultValue = "false") mine: Boolean,
        @RequestParam(required = false) instance: UUID?,
        @RequestAttribute(Identity.LOGIN) login: String,
    ): List<TaskView> {
        val tasks = when {
            mine -> instances.tasksFor(login)
            else -> store.openTasks()
        }.filter { instance == null || it.instanceId == instance }
        return tasks.map { task ->
            val state = instances.get(task.instanceId)
            TaskView.of(task, registry.get(state.definition, state.version))
        }
    }

    /** Move an open task to a user or a team. Needs the task's reassign permission (ops by default). */
    @Requires(Permissions.TASKS_REASSIGN, ScopeFrom.TASK)
    @PostMapping("/tasks/{id}/reassign")
    fun reassign(
        @PathVariable id: UUID,
        @RequestBody body: ReassignRequest,
        @RequestAttribute(Identity.LOGIN) login: String,
    ): InstanceView {
        val to = dev.workflow.core.Assignment(
            if (body.toTeam) dev.workflow.core.Assignment.Type.ROLE else dev.workflow.core.Assignment.Type.USER,
            body.to,
            body.scope,
        )
        val state = instances.reassignTask(id, login, to)
        val instanceId = UUID.fromString(state.id)
        val def = registry.get(state.definition, state.version)
        return InstanceView.of(state, store.openTasks(instanceId).map { TaskView.of(it, def) }, store.openWaitsFor(instanceId).map { WaitView.of(it) })
    }

    @Requires(Permissions.TASKS_COMPLETE, ScopeFrom.TASK)
    @PostMapping("/tasks/{id}/complete")
    fun complete(
        @PathVariable id: UUID,
        @RequestBody body: CompleteTaskRequest,
        @RequestAttribute(Identity.LOGIN) login: String,
    ): InstanceView {
        val state = instances.completeTask(id, login, body.outcome, body.form)
        val instanceId = UUID.fromString(state.id)
        val def = registry.get(state.definition, state.version)
        return InstanceView.of(state, store.openTasks(instanceId).map { TaskView.of(it, def) }, store.openWaitsFor(instanceId).map { WaitView.of(it) })
    }
}

@RestController
class EventController(private val instances: InstanceService) {

    /** Receive endpoint for event waits, e.g. an e-signature webhook. 404 when nothing is waiting. */
    @Requires(Permissions.EVENTS_SEND)
    @PostMapping("/events/{name}")
    fun receive(
        @PathVariable name: String,
        @RequestBody(required = false) body: EventRequest?,
        @RequestAttribute(Identity.LOGIN) login: String,
    ): ResponseEntity<Any> {
        val woke = instances.receiveEvent(name, body?.correlation ?: emptyMap(), body?.payload ?: emptyMap(), login)
        return if (woke.isEmpty()) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorView("no instance is waiting for '$name' with that correlation"))
        } else {
            ResponseEntity.accepted().body(EventAccepted(woke))
        }
    }

    /** Timeout callback for an external scheduler; the in-process poller does the same. */
    @Requires(Permissions.INSTANCES_FIRE_TIMEOUT)
    @PostMapping("/timeouts/{instanceId}/{step}")
    fun timeout(
        @PathVariable instanceId: UUID,
        @PathVariable step: String,
        @RequestAttribute(Identity.LOGIN) login: String,
    ) = mapOf("status" to instances.fireTimeout(instanceId, step, login).status.name.lowercase())
}

/** The caller's own permissions for this app, for the UI's `can()`. Any signed-in person may ask. */
@RestController
class MeController(private val rbac: RbacPort) {
    @Authenticated
    @GetMapping("/me")
    fun me(@RequestAttribute(Identity.LOGIN) login: String): Map<String, Any?> = mapOf(
        "login" to login,
        "displayName" to (rbac.people().firstOrNull { it.login == login }?.name ?: login),
        "permissions" to rbac.permissionsOf(login).map { mapOf("permission" to it.permission, "scope" to it.scope) },
    )
}

/** Dev convenience for the console and the persona switch: who the console can act as. Dev only, in practice. */
@RestController
class RbacController(private val rbac: RbacPort) {
    @Requires(Permissions.PEOPLE_READ)
    @GetMapping("/rbac/people")
    fun people(): List<Map<String, Any>> = rbac.people().map { mapOf("user" to it.login, "name" to it.name, "teams" to it.teams) }
}

@RestControllerAdvice
class ErrorHandler {
    @ExceptionHandler(NotFoundException::class, UnknownDefinitionException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: RuntimeException) = problem(404, "Not found", e.message ?: "not found")

    /** A denial: 403 as problem+json, with rbac's reason in words. */
    @ExceptionHandler(ForbiddenException::class)
    fun forbidden(e: ForbiddenException): ResponseEntity<Any> = problemResponse(403, "Not allowed", e.message ?: "forbidden", e.reason)

    @ExceptionHandler(UnauthenticatedException::class)
    fun unauthenticated(e: UnauthenticatedException): ResponseEntity<Any> = problemResponse(401, "Not signed in", e.message ?: "unauthenticated")

    /** rbac is down: the request fails closed. */
    @ExceptionHandler(RbacUnavailableException::class)
    fun rbacDown(e: RbacUnavailableException): ResponseEntity<Any> = problemResponse(503, "Access control unavailable", e.message ?: "rbac unavailable")

    @ExceptionHandler(BadRequestException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun badRequest(e: BadRequestException) = problem(400, "Invalid request", e.message ?: "bad request")

    @ExceptionHandler(ConflictException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun conflict(e: ConflictException) = problem(409, "Conflict", e.message ?: "conflict")

    private fun problem(status: Int, title: String, detail: String, reason: String? = null) = ErrorView(detail, reason, title, status, detail)

    private fun problemResponse(status: Int, title: String, detail: String, reason: String? = null): ResponseEntity<Any> =
        ResponseEntity.status(status).contentType(MediaType("application", "problem+json")).body(problem(status, title, detail, reason))
}
