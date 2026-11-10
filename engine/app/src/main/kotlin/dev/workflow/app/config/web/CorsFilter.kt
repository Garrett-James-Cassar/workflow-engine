package dev.workflow.app.config.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Strict CORS (SECURITY.md sec.2) from `CORS_ORIGINS`: an allowed origin is echoed back (never `*`), any other origin gets
 * no `Access-Control-Allow-Origin`. An allowed preflight answers 204 here, before identity and rate limits; a refused one 403.
 */
@Component
class CorsFilter(@Value("\${workflow.cors.origins}") origins: List<String>) : OncePerRequestFilter(), Ordered {
    private val allowed = origins.map { it.trim() }.filter { it.isNotEmpty() && it != "*" }.toSet()

    override fun getOrder() = Ordered.HIGHEST_PRECEDENCE + 6

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val origin = request.getHeader("Origin")
        val preflight = request.method == "OPTIONS" && request.getHeader("Access-Control-Request-Method") != null
        if (origin != null && origin in allowed) response.allow(origin)
        when {
            !preflight -> chain.doFilter(request, response)
            origin in allowed -> response.status = HttpServletResponse.SC_NO_CONTENT
            else -> response.status = HttpServletResponse.SC_FORBIDDEN
        }
    }

    private fun HttpServletResponse.allow(origin: String) {
        setHeader("Access-Control-Allow-Origin", origin)
        addHeader("Vary", "Origin")
        setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")
        setHeader("Access-Control-Allow-Headers", "content-type, authorization, x-user, traceparent")
        setHeader("Access-Control-Max-Age", "600")
    }
}
