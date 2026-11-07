package dev.workflow.app.rbac

import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * The rbac service over HTTP (`POST /check`, `GET /users/{login}/permissions`). Used when `RBAC_URL` is
 * set. An unreachable rbac fails closed: the caller gets [RbacUnavailableException], never an allow.
 */
class HttpRbac(baseUrl: String, builder: RestClient.Builder, private val app: String = APP) : RbacPort {
    // The builder is Boot's, so calls are observed: a client span, http.client.request.duration, and
    // the W3C traceparent on every request to rbac.
    private val client = builder.baseUrl(baseUrl.trimEnd('/')).build()

    private data class CheckBody(val login: String, val app: String? = null, val permission: String? = null, val role: String? = null, val scope: Map<String, String>)
    private data class CheckReply(val allowed: Boolean, val reason: String)
    private data class PermissionRow(val permission: String, val scope: Map<String, String>)
    private data class PermissionsReply(val permissions: List<PermissionRow>)

    private fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: RestClientException) {
        throw RbacUnavailableException("the rbac service did not answer: ${e.message}")
    }

    override fun check(login: String, permission: String, scope: Map<String, String>): RbacDecision {
        val reply = call {
            client.post().uri("/check").body(CheckBody(login, app = app, permission = permission, scope = scope))
                .retrieve().body(CheckReply::class.java)
        } ?: throw RbacUnavailableException("the rbac service sent no answer")
        return RbacDecision(reply.allowed, reply.reason)
    }

    override fun holdsRole(login: String, role: String, scope: Map<String, String>): RbacDecision {
        val reply = call {
            client.post().uri("/check").body(CheckBody(login, role = role, scope = scope))
                .retrieve().body(CheckReply::class.java)
        } ?: throw RbacUnavailableException("the rbac service sent no answer")
        return RbacDecision(reply.allowed, reply.reason)
    }

    override fun permissionsOf(login: String, scope: Map<String, String>?): List<PermissionGrant> {
        val reply = call {
            client.get().uri { b ->
                b.path("/users/{login}/permissions").queryParam("app", app)
                    .also { u -> scope?.forEach { (k, v) -> u.queryParam("scope", "$k:$v") } }
                    .build(login)
            }.retrieve().body(PermissionsReply::class.java)
        } ?: throw RbacUnavailableException("the rbac service sent no answer")
        return reply.permissions.map { PermissionGrant(it.permission, it.scope) }
    }

    /** The directory lives in rbac; the dev persona list is served only by [LocalRbac]. */
    override fun people(): List<PersonInfo> = emptyList()

    companion object {
        const val APP = "workflow-engine"
    }
}
