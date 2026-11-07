package dev.workflow.app.security

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val labels: dev.workflow.app.observability.EndpointLabelInterceptor,
    private val identity: IdentityInterceptor,
    private val permissions: PermissionInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(labels)
        registry.addInterceptor(identity)
        registry.addInterceptor(permissions)
    }
}
