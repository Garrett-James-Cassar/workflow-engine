package dev.workflow.app.rbac

import dev.workflow.app.security.AuthMode
import dev.workflow.app.security.AuthProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper

/**
 * The composition root for identity and access. `RBAC_URL` picks [HttpRbac]; otherwise [LocalRbac] over
 * the persona file. Live mode refuses to start without `RBAC_URL` or a JWT secret: no silent fallback.
 */
@Configuration
class RbacConfiguration {

    @Bean
    fun rbacPort(
        @Value("\${workflow.rbac.url:}") url: String,
        auth: AuthProperties,
        json: JsonMapper,
        http: RestClient.Builder,
    ): RbacPort {
        val mode = auth.authMode
        if (mode == AuthMode.LIVE && url.isBlank()) throw IllegalStateException("AUTH_MODE=live needs RBAC_URL: there is no local fallback in live mode")
        if (url.isNotBlank()) return HttpRbac(url, http)
        val text = checkNotNull(javaClass.classLoader.getResource(AppModel.RESOURCE)) { "missing ${AppModel.RESOURCE}" }.readText()
        return LocalRbac(AppModel.load(json, text))
    }
}
