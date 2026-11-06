package dev.workflow.app.rbac

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

    private fun describe(scope: Map<String, String>) = if (scope.isEmpty()) "everywhere" else scope.entries.joinToString { "${it.key}=${it.value}" }.let { "{$it}" }

    override fun check(login: String, permission: String, scope: Map<String, String>): RbacDecision {
        val grants = grantsOf(login)
        val holding = grants.filter { g -> model.role(g.role)?.permissions?.contains(permission) == true }
        val covering = holding.firstOrNull { scopeCovers(it.scope, scope) }
        if (covering != null) return RbacDecision(true, "'$login' holds '$permission' via '${covering.role}'")
        if (holding.isEmpty()) return RbacDecision(false, "'$login' does not hold '$permission'; ${grantedBy(permission)}")
        return RbacDecision(false, "'$login' holds '$permission' only in ${holding.joinToString { describe(it.scope) }}; this needs ${describe(scope)}")
    }

    override fun holdsRole(login: String, role: String, scope: Map<String, String>): RbacDecision {
        val held = grantsOf(login).filter { it.role == role }
        if (held.any { scopeCovers(it.scope, scope) }) return RbacDecision(true, "'$login' holds '$role'")
        if (held.isEmpty()) return RbacDecision(false, "'$login' does not hold role '$role'")
        return RbacDecision(false, "'$login' holds '$role' only in ${held.joinToString { describe(it.scope) }}; this needs ${describe(scope)}")
    }

    override fun permissionsOf(login: String, scope: Map<String, String>?): List<PermissionGrant> =
        grantsOf(login)
            .filter { g -> scope == null || scopeCovers(g.scope, scope) }
            .flatMap { g -> model.role(g.role)?.permissions.orEmpty().map { PermissionGrant(it, g.scope) } }
            .distinct()

    override fun people(): List<PersonInfo> = model.personas.map { p ->
        PersonInfo(p.login, p.name, p.grants.map { it.role }.distinct().sorted(), p.grants.map { RoleGrant(it.role, it.scope) })
    }
}
