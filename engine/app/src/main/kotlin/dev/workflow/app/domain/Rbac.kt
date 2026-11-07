package dev.workflow.app.domain

/** The answer to "may this person do this, here?", with the reason in words (rbac's wording). */
data class RbacDecision(val allowed: Boolean, val reason: String)

/** A permission a person holds, and where (scope). An empty scope means everywhere. */
data class PermissionGrant(val permission: String, val scope: Map<String, String>)

/** A person in the directory, for the dev persona list. [teams] are the roles they hold. */
data class PersonInfo(val login: String, val name: String, val teams: List<String>, val grants: List<RoleGrant> = emptyList())

/** A role a person holds, and where (scope). An empty scope means everywhere. */
data class RoleGrant(val role: String, val scope: Map<String, String>)

/** rbac itself could not be reached: the request fails closed (503), never allowed. */
class RbacUnavailableException(message: String) : RuntimeException(message)

/** The scope rule (RBAC.md section 1): a grant covers a request when every key it is limited to is asked for with the same value. */
fun scopeCovers(grantScope: Map<String, String>, requested: Map<String, String>): Boolean =
    grantScope.all { (k, v) -> requested[k] == v }

/** The caller's own access: who they are and every permission they hold. */
data class MeInfo(val login: String, val displayName: String, val permissions: List<PermissionGrant>)
