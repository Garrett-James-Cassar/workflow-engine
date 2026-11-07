package dev.workflow.app.clients

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import dev.workflow.app.adapters.LocalRbac
import dev.workflow.app.domain.PermissionGrant
import dev.workflow.app.domain.PersonInfo
import dev.workflow.app.domain.RbacDecision
import dev.workflow.app.domain.RbacUnavailableException
import dev.workflow.app.ports.RbacPort

import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriBuilder
import java.net.URI

/**
 * The rbac service over HTTP (`POST /check`, `GET /users/{login}/permissions`). Used when `RBAC_URL` is
 * set. An unreachable rbac fails closed: the caller gets [RbacUnavailableException], never an allow.
 */
class HttpRbac(baseUrl: String, builder: RestClient.Builder, private val app: String = APP) : RbacPort {
    // The builder is Boot's, so calls are observed: a client span, http.client.request.duration, and
    // the W3C traceparent on every request to rbac.
    private val client = builder.baseUrl(baseUrl.trimEnd('/')).build()

    private data class CheckBody(val login: String, val app: String? = null, val permission: String? = null, val role: String? = null, val scope: Map<String, String>)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class CheckReply(val allowed: Boolean, val reason: String)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class PermissionRow(val permission: String, val scope: Map<String, String>)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class PermissionsReply(val permissions: List<PermissionRow>)

    private fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: RestClientException) {
        throw RbacUnavailableException("the rbac service did not answer: ${e.message}")
    }

    private fun <T> answered(value: T?): T = value ?: throw RbacUnavailableException("the rbac service sent no answer")

    private fun decision(body: CheckBody): RbacDecision =
        answered(call { client.post().uri("/check").body(body).retrieve().body(CheckReply::class.java) }).let { RbacDecision(it.allowed, it.reason) }

    override fun check(login: String, permission: String, scope: Map<String, String>): RbacDecision =
        decision(CheckBody(login, app = app, permission = permission, scope = scope))

    override fun holdsRole(login: String, role: String, scope: Map<String, String>): RbacDecision =
        decision(CheckBody(login, role = role, scope = scope))

    override fun permissionsOf(login: String, scope: Map<String, String>?): List<PermissionGrant> =
        answered(call { fetch(login, scope) }).permissions.map { PermissionGrant(it.permission, it.scope) }

    private fun fetch(login: String, scope: Map<String, String>?) =
        client.get().uri { permissionsUri(it, login, scope) }.retrieve().body(PermissionsReply::class.java)

    private fun permissionsUri(b: UriBuilder, login: String, scope: Map<String, String>?): URI {
        b.path("/users/{login}/permissions").queryParam("app", app)
        scope?.forEach { (k, v) -> b.queryParam("scope", "$k:$v") }
        return b.build(login)
    }

    /** The directory lives in rbac; the dev persona list is served only by [LocalRbac]. */
    override fun people(): List<PersonInfo> = emptyList()

    companion object {
        const val APP = "workflow-engine"
    }
}
