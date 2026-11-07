package dev.workflow.app.api

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** The one place the major version is written (API.md): business routes live under it, operational ones stay at the root. */
const val API_PREFIX = "/api/v1"
private const val SUNSET_DAYS = 90L

/** Controllers that stay at the root: `/health` and the dev/demo helpers. Everything else in `api` is versioned. */
private val UNVERSIONED = setOf(HealthController::class.java, DemoController::class.java)

/** Mounts every business controller under [API_PREFIX], once, at the path-matching root. */
@Configuration
class ApiVersionConfig : WebMvcConfigurer {
    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix(API_PREFIX) { type -> type.packageName == "dev.workflow.app.api" && type !in UNVERSIONED && type.isAnnotationPresent(org.springframework.web.bind.annotation.RestController::class.java) }
    }

    /** OpenAPI lists `servers: [{url: /api/v1}]` and paths relative to it (API.md). */
    @Bean
    fun versionedServers() = OpenApiCustomizer { api ->
        api.servers = listOf(io.swagger.v3.oas.models.servers.Server().url(API_PREFIX))
        val relative = io.swagger.v3.oas.models.Paths()
        api.paths?.forEach { (path, item) -> if (path.startsWith("$API_PREFIX/")) relative.addPathItem(path.removePrefix(API_PREFIX), item) }
        api.paths = relative
    }
}

/**
 * The old unversioned business paths keep working as deprecated aliases of the same handlers: the request is
 * dispatched as its `/api/v1` twin, and the response says so with `Deprecation`, `Sunset` (90 days out) and a
 * `successor-version` link. Removed once every fleet caller has moved (API.md, migration).
 */
@Component
class DeprecatedAliasFilter(private val clock: Clock) : OncePerRequestFilter(), Ordered {
    override fun getOrder() = Ordered.HIGHEST_PRECEDENCE + 20

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val path = request.requestURI
        if (!isAlias(path)) return chain.doFilter(request, response)
        val successor = API_PREFIX + path
        response.setHeader("Deprecation", "true")
        response.setHeader("Sunset", DateTimeFormatter.RFC_1123_DATE_TIME.format(clock.instant().plus(SUNSET_DAYS, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC)))
        response.setHeader("Link", "<$successor>; rel=\"successor-version\"")
        chain.doFilter(Versioned(request, successor), response)
    }

    private fun isAlias(path: String) = ALIASES.any { path == it || path.startsWith("$it/") }

    private class Versioned(request: HttpServletRequest, private val target: String) : HttpServletRequestWrapper(request) {
        override fun getRequestURI() = target
        override fun getServletPath() = target
        override fun getRequestURL(): StringBuffer = StringBuffer(super.getRequestURL().toString().removeSuffix(super.getRequestURI()) + target)
    }

    private companion object {
        val ALIASES = listOf("/definitions", "/instances", "/tasks", "/events", "/timeouts", "/me", "/rbac")
    }
}
