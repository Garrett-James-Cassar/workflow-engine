package dev.workflow.app.config.web

import dev.workflow.app.security.Authenticated
import dev.workflow.app.security.Authorizer
import dev.workflow.app.security.Identity
import dev.workflow.app.security.Permissions
import dev.workflow.app.security.Public
import dev.workflow.app.security.Requires
import dev.workflow.app.security.TaskAuthorizer
import dev.workflow.app.security.ScopeFrom

import dev.workflow.app.domain.ForbiddenException
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import tools.jackson.databind.json.JsonMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * The central gate for every route of this app (fleet RBAC.md section 5). It runs after [IdentityInterceptor]
 * and reads the handler's annotation: `@Public` and `@Authenticated` pass; `@Requires` checks the permission
 * with the scope it declares; a route with none of the three is denied. A denial is problem+json with
 * rbac's reason in words.
 */
@Component
class PermissionInterceptor(
    private val authorizer: Authorizer,
    private val tasks: TaskAuthorizer,
    private val json: JsonMapper,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod || !gated(handler)) return true
        val requires = handler.method.getAnnotation(Requires::class.java)
            ?: return deny(response, 403, "Route not declared", "${handler.beanType.simpleName}.${handler.method.name} declares no permission, so it is denied")
        val login = request.getAttribute(Identity.LOGIN) as String?
            ?: return deny(response, 401, "Not signed in", "The identity is missing.")
        return permitted(request, response, login, requires)
    }

    // Only this app's routes are gated here; framework routes (docs, error page) are open by path, see IdentityInterceptor.
    private fun gated(handler: HandlerMethod) =
        handler.beanType.name.startsWith(APP_PACKAGE) && !handler.method.isAnnotationPresent(Public::class.java) && !handler.method.isAnnotationPresent(Authenticated::class.java)

    private fun permitted(request: HttpServletRequest, response: HttpServletResponse, login: String, requires: Requires): Boolean = try {
        check(request, login, requires)
        true
    } catch (e: ForbiddenException) {
        deny(response, 403, "Not allowed", e.reason, e.message)
    }

    private fun check(request: HttpServletRequest, login: String, requires: Requires) = when (requires.scope) {
        ScopeFrom.NONE -> authorizer.require(login, requires.permission)
        ScopeFrom.LIST -> authorizer.requireAnywhere(login, requires.permission)
        ScopeFrom.TASK -> tasks.require(pathVariable(request, "id"), login, requires.permission)
    }

    @Suppress("UNCHECKED_CAST")
    private fun pathVariable(request: HttpServletRequest, name: String): String? =
        (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as Map<String, String>)[name]

    private fun deny(response: HttpServletResponse, status: Int, title: String, reason: String, detail: String? = null): Boolean {
        response.status = status
        response.contentType = "application/problem+json"
        response.writer.write(json.writeValueAsString(mapOf("type" to "about:blank", "title" to title, "status" to status, "detail" to (detail ?: reason), "reason" to reason)))
        return false
    }

    private companion object {
        const val APP_PACKAGE = "dev.workflow.app"
    }
}
