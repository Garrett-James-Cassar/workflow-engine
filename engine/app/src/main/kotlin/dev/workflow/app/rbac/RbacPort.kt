package dev.workflow.app.rbac

/** The answer to "may this person do this, here?", with the reason in words (rbac's wording). */
data class RbacDecision(val allowed: Boolean, val reason: String)

/** A permission a person holds, and where (scope). An empty scope means everywhere. */
data class PermissionGrant(val permission: String, val scope: Map<String, String>)

/** A person in the directory, for the dev persona list. [teams] are the roles they hold. */
data class PersonInfo(val login: String, val name: String, val teams: List<String>)

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

/** rbac itself could not be reached: the request fails closed (503), never allowed. */
class RbacUnavailableException(message: String) : RuntimeException(message)

/** The scope rule (RBAC.md section 1): a grant covers a request when every key it is limited to is asked for with the same value. */
fun scopeCovers(grantScope: Map<String, String>, requested: Map<String, String>): Boolean =
    grantScope.all { (k, v) -> requested[k] == v }
