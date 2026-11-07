package dev.workflow.app.security

import dev.workflow.app.domain.ForbiddenException
import dev.workflow.app.ports.RbacPort
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.time.Clock
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** The request attribute holding the signed-in login, set by the identity interceptor. */
object Identity {
    const val LOGIN = "workflow.login"
    const val DEV_HEADER = "X-User"
}

enum class AuthMode { DEV, LIVE }

/**
 * `workflow.auth.*`: `mode` is `dev` (the `X-User` header, for dev, demo and mock) or `live` (a Supabase
 * Bearer JWT, whose login is `app_metadata.login`; `X-User` is never honoured). `jwtSecret` verifies
 * HS256; `jwksUrl` is reserved and refused at startup until JWKS verification exists.
 */
@ConfigurationProperties("workflow.auth")
data class AuthProperties(val mode: String = "dev", val jwtSecret: String = "", val jwksUrl: String = "") {
    val authMode: AuthMode
        get() = when (mode.lowercase()) {
            "dev" -> AuthMode.DEV
            "live" -> AuthMode.LIVE
            else -> throw IllegalStateException("AUTH_MODE must be 'dev' or 'live', not '$mode'")
        }
}

/**
 * Verifies a Supabase HS256 access token and returns its login (`app_metadata.login`), or null when it is
 * not valid, not signed with the secret, or expired.
 */
class Hs256Verifier(secret: String, private val json: JsonMapper, private val clock: Clock) {
    private val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
    private val decoder = Base64.getUrlDecoder()

    fun loginOf(token: String): String? {
        val parts = token.split('.')
        if (parts.size != 3) return null
        val header = json.readTree(decoder.decode(parts[0]))
        if (header.path("alg").asText() != "HS256") return null
        val expected = Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal("${parts[0]}.${parts[1]}".toByteArray(Charsets.UTF_8))
        val given = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return null
        if (!MessageDigest.isEqual(expected, given)) return null
        val claims = json.readTree(decoder.decode(parts[1]))
        if (claims.path("exp").asLong() <= clock.instant().epochSecond) return null
        return claims.path("app_metadata").path("login").asText().takeIf { it.isNotBlank() }
    }
}

/** Checks a permission before work is done. A denial is a 403 that carries rbac's reason. */
@Component
class Authorizer(private val rbac: RbacPort) {
    fun require(login: String, permission: String, scope: Map<String, String> = emptyMap()) {
        val decision = rbac.check(login, permission, scope)
        if (!decision.allowed) throw ForbiddenException("'$login' may not do this", decision.reason)
    }

    /** Held in some scope (global or scoped): the caller may list, and the listing is filtered per item. */
    fun requireAnywhere(login: String, permission: String) {
        if (rbac.permissionsOf(login).any { it.permission == permission }) return
        throw ForbiddenException("'$login' may not do this", rbac.check(login, permission).reason)
    }
}
