package dev.workflow.app.config.web

import dev.workflow.app.security.AuthMode
import dev.workflow.app.security.AuthProperties
import dev.workflow.app.security.Hs256Verifier
import dev.workflow.app.security.Identity
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.time.Clock

/** Identity: who is calling. Every path that is not public needs a login, or the caller gets 401. */
@Component
class IdentityInterceptor(
    private val auth: AuthProperties,
    private val json: JsonMapper,
    clock: Clock,
) : org.springframework.web.servlet.HandlerInterceptor {
    private val verifier: Hs256Verifier? = if (auth.authMode == AuthMode.LIVE) Hs256Verifier(auth.jwtSecret, json, clock) else null

    override fun preHandle(
        request: jakarta.servlet.http.HttpServletRequest,
        response: jakarta.servlet.http.HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (isPublic(request.requestURI)) return true
        val login = when (auth.authMode) {
            AuthMode.DEV -> request.getHeader(Identity.DEV_HEADER)?.trim()?.takeIf { it.isNotEmpty() }
            AuthMode.LIVE -> bearer(request)?.let { verifier?.loginOf(it) } // X-User is never read in live mode
        }
        if (login == null) {
            val message = if (auth.authMode == AuthMode.DEV) "The X-User header names who is acting." else "Sign in: send a valid Bearer token."
            response.status = 401
            response.contentType = "application/problem+json"
            response.writer.write(json.writeValueAsString(mapOf("type" to "about:blank", "title" to "Not signed in", "status" to 401, "detail" to message)))
            return false
        }
        request.setAttribute(Identity.LOGIN, login)
        return true
    }

    private fun bearer(request: jakarta.servlet.http.HttpServletRequest): String? =
        request.getHeader("Authorization")?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)?.trim()

    private fun isPublic(path: String): Boolean = path in PUBLIC_PATHS || PUBLIC_PREFIXES.any { path.startsWith(it) }
}

private val PUBLIC_PATHS = setOf("/", "/index.html", "/health", "/app.js", "/app.css", "/demo/status", "/openapi.json")

private val PUBLIC_PREFIXES = listOf(
    "/vendor/", "/console-demo/", "/docs", "/swagger-ui", "/v3/api-docs",
    // The demo providers stand in for third parties the engine calls (its own http tasks); live, they are real services.
    "/demo/screening", "/demo/clause-store/", "/demo/booking-system",
)
