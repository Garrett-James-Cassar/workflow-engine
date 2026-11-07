package dev.workflow.app.api

import dev.workflow.app.api.dtos.EventAccepted
import dev.workflow.app.api.dtos.EventRequest
import dev.workflow.app.runtime.InstanceService
import dev.workflow.app.security.Identity
import dev.workflow.app.security.Permissions
import dev.workflow.app.security.Requires
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class EventController(private val instances: InstanceService) {

    /** Receive endpoint for event waits, e.g. an e-signature webhook. 404 when nothing is waiting. */
    @Requires(Permissions.EVENTS_SEND, label = "Send event")
    @PostMapping("/events/{name}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun receive(
        @PathVariable name: String,
        @RequestBody(required = false) body: EventRequest?,
        @RequestAttribute(Identity.LOGIN) login: String,
    ) = EventAccepted(instances.deliverEvent(name, body?.correlation.orEmpty(), body?.payload.orEmpty(), login))

    /** Timeout callback for an external scheduler; the in-process poller does the same. */
    @Requires(Permissions.INSTANCES_FIRE_TIMEOUT, label = "Fire timeout")
    @PostMapping("/timeouts/{instanceId}/{step}")
    fun timeout(
        @PathVariable instanceId: UUID,
        @PathVariable step: String,
        @RequestAttribute(Identity.LOGIN) login: String,
    ) = mapOf("status" to instances.fireTimeout(instanceId, step, login).status.name.lowercase())
}
