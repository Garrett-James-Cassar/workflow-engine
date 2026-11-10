package dev.workflow.app.config

import dev.workflow.app.api.DemoController
import dev.workflow.app.api.DemoProviders
import dev.workflow.app.api.HealthController
import dev.workflow.app.api.ReadinessController
import dev.workflow.app.config.web.API_PREFIX
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springdoc.core.customizers.OpenApiCustomizer

/** Controllers that stay at the root: `/health` and the dev/demo helpers. Everything else in `api` is versioned. */
private val UNVERSIONED = setOf(HealthController::class.java, ReadinessController::class.java, DemoController::class.java, DemoProviders::class.java)

/** Operational routes listed in OpenAPI at the root (their own `servers` override the `/api/v1` default). */
private val ROOT_PATHS = setOf("/health", "/ready")

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
        api.paths?.forEach { (path, item) -> if (path.startsWith("$API_PREFIX/")) relative.addPathItem(path.removePrefix(API_PREFIX), item) else if (path in ROOT_PATHS) relative.addPathItem(path, item.servers(listOf(io.swagger.v3.oas.models.servers.Server().url("/")))) }
        api.paths = relative
    }
}
