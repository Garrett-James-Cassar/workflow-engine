package dev.workflow.app.ports

import dev.workflow.app.adapters.LocalRbac
import dev.workflow.app.clients.HttpRbac

import dev.workflow.app.domain.PermissionGrant
import dev.workflow.app.domain.PersonInfo
import dev.workflow.app.domain.RbacDecision

/**
 * The fleet RBAC port (RBAC.md section 4). Two adapters: [HttpRbac] when `RBAC_URL` is set, and
 * [LocalRbac], the same model evaluated over the app's persona file, when it is not.
 */
interface RbacPort {
    /** Does [login] hold [permission] for this app in [scope]? */
    fun check(login: String, permission: String, scope: Map<String, String> = emptyMap()): RbacDecision

    /** Does [login] hold the role [role] (a team or a role) in [scope]? Used to route tasks to teams. */
    fun holdsRole(login: String, role: String, scope: Map<String, String> = emptyMap()): RbacDecision

    /** The permissions [login] holds for this app, optionally only those covering [scope]. For `GET /me`. */
    fun permissionsOf(login: String, scope: Map<String, String>? = null): List<PermissionGrant>

    /** Everyone the dev persona list can act as. Empty when the directory is remote. */
    fun people(): List<PersonInfo>
}
