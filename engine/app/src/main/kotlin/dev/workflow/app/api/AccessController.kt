package dev.workflow.app.api

import dev.workflow.app.api.dtos.MeView
import dev.workflow.app.api.dtos.PersonView
import dev.workflow.app.mappers.AccessMapper
import dev.workflow.app.portable.RecordExports
import dev.workflow.app.runtime.AccessService
import dev.workflow.app.security.Authenticated
import dev.workflow.app.security.Identity
import dev.workflow.app.security.Permissions
import dev.workflow.app.security.Requires
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RestController

/** The caller's own permissions for this app, for the UI's `can()`. Any signed-in person may ask. */
@RestController
class MeController(private val access: AccessService) {
    @Authenticated(label = "Show my access", action = "me.read")
    @GetMapping("/me")
    fun me(@RequestAttribute(Identity.LOGIN) login: String): MeView = AccessMapper.me(access.me(login))
}

/** Dev convenience for the console and the persona switch: who the console can act as. Dev only, in practice. */
@RestController
class RbacController(private val access: AccessService, private val records: RecordExports) {
    @Requires(Permissions.PEOPLE_READ, label = "List people")
    @GetMapping("/rbac/people")
    fun people(): List<PersonView> = access.people().map(AccessMapper::person)

    /** The people the app can act as, as portable documents (Records kind: export only). */
    @Requires(Permissions.PEOPLE_EXPORT, label = "Export people")
    @GetMapping("/people/export")
    fun export(): ResponseEntity<Any> = download(records.people())
}
