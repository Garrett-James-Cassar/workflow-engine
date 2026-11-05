package dev.workflow.app.api

import dev.workflow.app.persistence.Store
import dev.workflow.app.rbac.RbacProperties
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
    @GetMapping("/health")
    fun health() = mapOf("status" to "ok")
}

@RestController
class DefinitionController(private val registry: WorkflowRegistry) {

    @GetMapping("/definitions")
    fun list() = registry.names().map { name ->
        val latest = registry.latest(name)
        DefinitionSummary(name, latest.version, registry.versions(name), latest.allSteps.size)
    }

    @GetMapping("/definitions/{name}")
    fun get(@PathVariable name: String, @RequestParam(required = false) version: Int?): DefinitionView {
        val def = version?.let { registry.get(name, it) } ?: registry.latest(name)
        return DefinitionView(def.name, def.version, def.sample, GraphView.of(def.toGraph()))
    }

    @GetMapping("/definitions/{name}/graph")
    fun graph(@PathVariable name: String, @RequestParam(required = false) version: Int?): GraphView =
        GraphView.of((version?.let { registry.get(name, it) } ?: registry.latest(name)).toGraph())

    /**
     * The workflow's diagram. `format=html` (and the default for browsers) returns a page that draws
     * the Mermaid picture with SVG/PNG downloads; `format=mermaid` (the default otherwise) returns
     * the Mermaid source; `format=graph` the graph JSON.
     */
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

    @PostMapping("/definitions/{name}/instances")
    @ResponseStatus(HttpStatus.CREATED)
    fun start(
        @PathVariable name: String,
        @RequestBody(required = false) body: StartRequest?,
        @RequestHeader(USER_HEADER, required = false) user: String?,
    ): InstanceView = view(instances.start(name, body?.vars ?: emptyMap(), user))

    @GetMapping("/instances")
    fun list(@RequestParam(defaultValue = "50") limit: Int) = store.instances(limit.coerceIn(1, 500)).map { view(it) }

    @GetMapping("/instances/{id}")
    fun get(@PathVariable id: UUID): InstanceView = view(instances.get(id))

    @GetMapping("/instances/{id}/events")
    fun events(@PathVariable id: UUID): List<EventView> {
        instances.get(id)
        return store.events(id).map { EventView.of(it) }
    }

    /** Support restart at a user task. Needs the workflow-support role. */
    @PostMapping("/instances/{id}/restart")
    fun restart(
        @PathVariable id: UUID,
        @RequestBody body: RestartRequest,
        @RequestHeader(USER_HEADER, required = false) user: String?,
    ): InstanceView = view(instances.restartAt(id, user ?: throw UnauthenticatedException(), body.step))

    /** Re-run the step an incident stopped at. */
    @PostMapping("/instances/{id}/retry")
    fun retry(@PathVariable id: UUID, @RequestHeader(USER_HEADER, required = false) user: String?): InstanceView =
        view(instances.retry(id, user))

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
    @GetMapping("/tasks")
    fun list(
        @RequestParam(defaultValue = "false") mine: Boolean,
        @RequestParam(required = false) instance: UUID?,
        @RequestHeader(USER_HEADER, required = false) user: String?,
    ): List<TaskView> {
        val tasks = when {
            mine -> instances.tasksFor(user ?: throw UnauthenticatedException())
            else -> store.openTasks()
        }.filter { instance == null || it.instanceId == instance }
        return tasks.map { task ->
            val state = instances.get(task.instanceId)
            TaskView.of(task, registry.get(state.definition, state.version))
        }
    }

    /** Move an open task to a user or a team. Needs the task's reassign permission (ops by default). */
    @PostMapping("/tasks/{id}/reassign")
    fun reassign(
        @PathVariable id: UUID,
        @RequestBody body: ReassignRequest,
        @RequestHeader(USER_HEADER, required = false) user: String?,
    ): InstanceView {
        val to = dev.workflow.core.Assignment(
            if (body.toTeam) dev.workflow.core.Assignment.Type.ROLE else dev.workflow.core.Assignment.Type.USER,
            body.to,
            body.scope,
        )
        val state = instances.reassignTask(id, user ?: throw UnauthenticatedException(), to)
        val instanceId = UUID.fromString(state.id)
        val def = registry.get(state.definition, state.version)
        return InstanceView.of(state, store.openTasks(instanceId).map { TaskView.of(it, def) }, store.openWaitsFor(instanceId).map { WaitView.of(it) })
    }

    @PostMapping("/tasks/{id}/complete")
    fun complete(
        @PathVariable id: UUID,
        @RequestBody body: CompleteTaskRequest,
        @RequestHeader(USER_HEADER, required = false) user: String?,
    ): InstanceView {
        val state = instances.completeTask(id, user ?: throw UnauthenticatedException(), body.outcome, body.form)
        val instanceId = UUID.fromString(state.id)
        val def = registry.get(state.definition, state.version)
        return InstanceView.of(state, store.openTasks(instanceId).map { TaskView.of(it, def) }, store.openWaitsFor(instanceId).map { WaitView.of(it) })
    }
}

@RestController
class EventController(private val instances: InstanceService) {

    /** Receive endpoint for event waits, e.g. an e-signature webhook. 404 when nothing is waiting. */
    @PostMapping("/events/{name}")
    fun receive(@PathVariable name: String, @RequestBody(required = false) body: EventRequest?): ResponseEntity<Any> {
        val woke = instances.receiveEvent(name, body?.correlation ?: emptyMap(), body?.payload ?: emptyMap())
        return if (woke.isEmpty()) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorView("no instance is waiting for '$name' with that correlation"))
        } else {
            ResponseEntity.accepted().body(EventAccepted(woke))
        }
    }

    /** Timeout callback for an external scheduler; the in-process poller does the same. */
    @PostMapping("/timeouts/{instanceId}/{step}")
    fun timeout(@PathVariable instanceId: UUID, @PathVariable step: String) = mapOf("status" to instances.fireTimeout(instanceId, step).status.name.lowercase())
}

/** Dev convenience for the console: the configured grants, so it can offer users to act as. */
@RestController
class RbacController(private val props: RbacProperties) {
    @GetMapping("/rbac/grants")
    fun grants() = props.grants

    /** Every person the console can act as: the login id, a human name, and the teams they hold. */
    @GetMapping("/rbac/people")
    fun people(): List<Map<String, Any>> {
        val logins = (props.people.keys + props.grants.map { it.user }).toSortedSet()
        return logins.map { login ->
            mapOf(
                "user" to login,
                "name" to (props.people[login] ?: login),
                "teams" to props.grants.filter { it.user == login }.map { it.role }.distinct().sorted(),
            )
        }
    }
}

@RestControllerAdvice
class ErrorHandler {
    @ExceptionHandler(NotFoundException::class, UnknownDefinitionException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: RuntimeException) = ErrorView(e.message ?: "not found")

    @ExceptionHandler(ForbiddenException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun forbidden(e: ForbiddenException) = ErrorView(e.message ?: "forbidden", e.reason)

    @ExceptionHandler(UnauthenticatedException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun unauthenticated(e: UnauthenticatedException) = ErrorView(e.message ?: "unauthenticated")

    @ExceptionHandler(BadRequestException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun badRequest(e: BadRequestException) = ErrorView(e.message ?: "bad request")

    @ExceptionHandler(ConflictException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun conflict(e: ConflictException) = ErrorView(e.message ?: "conflict")
}
