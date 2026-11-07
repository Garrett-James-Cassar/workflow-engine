package dev.workflow.app.config

import dev.workflow.app.config.web.EndpointLabelInterceptor
import dev.workflow.app.config.web.IdentityInterceptor
import dev.workflow.app.config.web.PermissionInterceptor
import dev.workflow.app.config.web.RateLimitInterceptor

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val labels: EndpointLabelInterceptor,
    private val identity: IdentityInterceptor,
    private val rateLimit: RateLimitInterceptor,
    private val permissions: PermissionInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(labels)
        registry.addInterceptor(identity)
        registry.addInterceptor(rateLimit)
        registry.addInterceptor(permissions)
    }
}
