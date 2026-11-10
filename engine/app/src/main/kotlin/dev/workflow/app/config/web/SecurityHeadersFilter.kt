package dev.workflow.app.config.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/** Pages that need scripts and styles of their own: API docs, the console, the player and the diagram page. */
private val CSP_EXEMPT_PREFIXES = listOf("/docs", "/swagger-ui", "/app.js", "/app.css", "/index.html", "/console-demo", "/vendor")
private const val API_CSP = "default-src 'none'; frame-ancestors 'none'"

/**
 * Security headers on every response (SECURITY.md sec.2), set before the chain so errors carry them too.
 * No Spring Security: it would change authentication. The CSP skips `/docs` and the HTML pages; HSTS only with `TLS=true`.
 */
@Component
class SecurityHeadersFilter(@Value("\${workflow.tls:false}") private val tls: Boolean) : OncePerRequestFilter(), Ordered {
    override fun getOrder() = Ordered.HIGHEST_PRECEDENCE + 5

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        response.setHeader("X-Content-Type-Options", "nosniff")
        response.setHeader("X-Frame-Options", "DENY")
        response.setHeader("Referrer-Policy", "no-referrer")
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin")
        if (tls) response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        if (strictCsp(request.requestURI)) response.setHeader("Content-Security-Policy", API_CSP)
        chain.doFilter(request, response)
    }

    private fun strictCsp(path: String) = path != "/" && !path.endsWith("/export") && CSP_EXEMPT_PREFIXES.none { path.startsWith(it) }
}
