package dev.workflow.app.api

import dev.workflow.app.api.dtos.EventView
import dev.workflow.app.api.dtos.InstanceView
import dev.workflow.app.api.dtos.RestartRequest
import dev.workflow.app.api.dtos.StartRequest
import dev.workflow.app.mappers.EventMapper
import dev.workflow.app.mappers.InstanceMapper
import dev.workflow.app.runtime.InstanceService
import dev.workflow.app.runtime.RunQueries
import dev.workflow.app.security.Identity
import dev.workflow.app.security.Permissions
import dev.workflow.app.security.Requires
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class InstanceController(private val instances: InstanceService, private val queries: RunQueries) {

    @Requires(Permissions.INSTANCES_START, label = "Start run")
    @PostMapping("/definitions/{name}/instances")
    @ResponseStatus(HttpStatus.CREATED)
    fun start(
        @PathVariable name: String,
        @RequestBody(required = false) body: StartRequest?,
        @RequestAttribute(Identity.LOGIN) login: String,
    ): InstanceView = view(instances.start(name, body?.vars.orEmpty(), login))

    @Requires(Permissions.INSTANCES_READ, label = "List runs")
    @GetMapping("/instances")
    fun list(@RequestParam(defaultValue = "50") limit: Int): List<InstanceView> =
        queries.recent(limit.coerceIn(1, 500)).map(InstanceMapper::view)

    @Requires(Permissions.INSTANCES_READ, label = "Show run")
    @GetMapping("/instances/{id}")
    fun get(@PathVariable id: UUID): InstanceView = view(instances.get(id))

    @Requires(Permissions.INSTANCES_READ, label = "List run events")
    @GetMapping("/instances/{id}/events")
    fun events(@PathVariable id: UUID): List<EventView> = queries.events(id).map(EventMapper::view)

    /** Support restart at a user task. Needs the workflow-support role. */
    @Requires(Permissions.INSTANCES_RESTART, label = "Restart at step")
    @PostMapping("/instances/{id}/restart")
    fun restart(
        @PathVariable id: UUID,
        @RequestBody body: RestartRequest,
        @RequestAttribute(Identity.LOGIN) login: String,
    ): InstanceView = view(instances.restartAt(id, login, body.step))

    /** Re-run the step an incident stopped at. */
    @Requires(Permissions.INSTANCES_RETRY, label = "Retry step")
    @PostMapping("/instances/{id}/retry")
    fun retry(@PathVariable id: UUID, @RequestAttribute(Identity.LOGIN) login: String): InstanceView =
        view(instances.retry(id, login))

    private fun view(state: dev.workflow.core.InstanceState) = InstanceMapper.view(queries.detail(state))
}
