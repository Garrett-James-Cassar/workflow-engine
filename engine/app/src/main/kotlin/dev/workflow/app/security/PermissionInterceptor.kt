package dev.workflow.app.security

import dev.workflow.app.persistence.Store
import dev.workflow.app.rbac.RbacPort
import dev.workflow.app.runtime.ForbiddenException
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
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
    private val rbac: RbacPort,
    private val store: Store,
    private val json: JsonMapper,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod) return true
        // Only this app's routes are gated here; framework routes (docs, error page) are open by path, see IdentityInterceptor.
        if (!handler.beanType.name.startsWith(APP_PACKAGE)) return true
        val method = handler.method
        if (method.isAnnotationPresent(Public::class.java) || method.isAnnotationPresent(Authenticated::class.java)) return true
        val requires = method.getAnnotation(Requires::class.java)
            ?: return deny(response, 403, "Route not declared", "${handler.beanType.simpleName}.${method.name} declares no permission, so it is denied")
        val login = request.getAttribute(Identity.LOGIN) as String?
            ?: return deny(response, 401, "Not signed in", "The identity is missing.")
        return try {
            when (requires.scope) {
                ScopeFrom.NONE -> authorizer.require(login, requires.permission)
                ScopeFrom.LIST -> authorizer.requireAnywhere(login, requires.permission)
                ScopeFrom.TASK -> requireTask(request, login, requires.permission)
            }
            true
        } catch (e: ForbiddenException) {
            deny(response, 403, "Not allowed", e.reason, e.message)
        }
    }

    /**
     * A route on one task. A person's own user task needs no permission to complete (the engine's rule);
     * a team task needs the team's role in the task's scope and then the permission in that scope.
     */
    private fun requireTask(request: HttpServletRequest, login: String, permission: String) {
        @Suppress("UNCHECKED_CAST")
        val vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as Map<String, String>
        val task = vars["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }?.let { store.task(it) } ?: return
        val assignment = task.assignment
        if (assignment.type == dev.workflow.core.Assignment.Type.USER) {
            if (permission == Permissions.TASKS_COMPLETE) {
                if (assignment.name == login) return
                throw ForbiddenException("'$login' may not complete this task", "task is assigned to user '${assignment.name}', not '$login'")
            }
            authorizer.require(login, permission, assignment.scope)
            return
        }
        if (permission == Permissions.TASKS_COMPLETE) {
            val held = rbac.holdsRole(login, assignment.name, assignment.scope)
            if (!held.allowed) throw ForbiddenException("'$login' may not complete this task", held.reason)
        }
        authorizer.require(login, permission, assignment.scope)
    }

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
