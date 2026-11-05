package dev.workflow.app.rbac

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

data class RbacDecision(val allowed: Boolean, val reason: String)

/** The engine's view of central RBAC. v1 reads grants from config; the rbac service client replaces it in E7. */
interface Rbac {
    /** Does [username] hold [role] in [scope]? A grant matches when each of its scope keys equals the request's value. */
    fun check(username: String, role: String, scope: Map<String, String>): RbacDecision
}

@ConfigurationProperties("workflow.rbac")
data class RbacProperties(
    val grants: List<Grant> = emptyList(),
    /** Display names by login id. The login id is what `X-User` carries; the name is for people to read. */
    val people: Map<String, String> = emptyMap(),
) {
    data class Grant(val user: String, val role: String, val scope: Map<String, String> = emptyMap())
}

@Component
class ConfigRbac(private val props: RbacProperties) : Rbac {
    override fun check(username: String, role: String, scope: Map<String, String>): RbacDecision {
        val held = props.grants.filter { it.user == username && it.role == role }
        if (held.isEmpty()) return RbacDecision(false, "'$username' does not hold role '$role'")
        val match = held.firstOrNull { grant -> grant.scope.all { (k, v) -> scope[k] == v } }
            ?: return RbacDecision(
                false,
                "'$username' holds '$role' only in ${held.joinToString { it.scope.toString() }}; this task needs $scope",
            )
        return RbacDecision(true, "'$username' holds '$role'" + if (match.scope.isEmpty()) "" else " in ${match.scope}")
    }
}
