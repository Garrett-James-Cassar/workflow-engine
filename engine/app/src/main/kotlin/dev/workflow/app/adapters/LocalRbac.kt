package dev.workflow.app.adapters

import dev.workflow.app.domain.AppModel
import dev.workflow.app.domain.PermissionGrant
import dev.workflow.app.domain.PersonInfo
import dev.workflow.app.domain.RbacDecision
import dev.workflow.app.domain.RoleGrant
import dev.workflow.app.domain.scopeCovers
import dev.workflow.app.ports.RbacPort

/**
 * The same model as rbac, evaluated over the app's persona file. Used when `RBAC_URL` is unset, so the
 * app starts alone (INDEPENDENCE.md). Never used in live mode.
 */
class LocalRbac(private val model: AppModel) : RbacPort {

    private fun grantsOf(login: String) = model.personas.firstOrNull { it.login == login }?.grants.orEmpty()

    /** The roles that bundle [permission], so a denial says what would allow it. */
    private fun grantedBy(permission: String): String {
        val roles = model.roles.filter { permission in it.permissions }.map { it.name }
        return if (roles.isEmpty()) "no role grants it" else "it needs role ${roles.joinToString(" or ") { "'$it'" }}"
    }

    private fun describe(scope: Map<String, String>) = if (scope.isEmpty()) "everywhere" else pairs(scope)

    private fun pairs(scope: Map<String, String>) = scope.entries.joinToString(prefix = "{", postfix = "}") { "${it.key}=${it.value}" }

    private fun holds(grant: AppModel.GrantDef, permission: String) = model.role(grant.role)?.permissions?.contains(permission) == true

    /** Allowed when a held grant covers [scope]; otherwise the reason says what is missing. */
    private fun decide(held: List<AppModel.GrantDef>, scope: Map<String, String>, words: Words): RbacDecision =
        held.firstOrNull { scopeCovers(it.scope, scope) }?.let { RbacDecision(true, words.allowed(it)) }
            ?: denied(held, scope, words)

    private fun denied(held: List<AppModel.GrantDef>, scope: Map<String, String>, words: Words) =
        RbacDecision(false, if (held.isEmpty()) words.none else "${words.partial} only in ${wheres(held)}; this needs ${describe(scope)}")

    private fun wheres(held: List<AppModel.GrantDef>) = held.joinToString { describe(it.scope) }

    override fun check(login: String, permission: String, scope: Map<String, String>): RbacDecision = decide(
        grantsOf(login).filter { holds(it, permission) }, scope,
        Words({ "'$login' holds '$permission' via '${it.role}'" }, "'$login' does not hold '$permission'; ${grantedBy(permission)}", "'$login' holds '$permission'"),
    )

    override fun holdsRole(login: String, role: String, scope: Map<String, String>): RbacDecision = decide(
        grantsOf(login).filter { it.role == role }, scope,
        Words({ "'$login' holds '$role'" }, "'$login' does not hold role '$role'", "'$login' holds '$role'"),
    )

    /** The words of a decision: when allowed, when nothing is held, and the start of "held, but not here". */
    private class Words(val allowed: (AppModel.GrantDef) -> String, val none: String, val partial: String)

    override fun permissionsOf(login: String, scope: Map<String, String>?): List<PermissionGrant> =
        grantsOf(login)
            .filter { g -> scope == null || scopeCovers(g.scope, scope) }
            .flatMap { g -> model.role(g.role)?.permissions.orEmpty().map { PermissionGrant(it, g.scope) } }
            .distinct()

    override fun people(): List<PersonInfo> = model.personas.map { p ->
        PersonInfo(p.login, p.name, p.grants.map { it.role }.distinct().sorted(), p.grants.map { RoleGrant(it.role, it.scope) })
    }
}
