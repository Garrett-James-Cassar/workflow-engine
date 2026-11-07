package dev.workflow.app.config.web

import dev.workflow.app.security.Public

import dev.workflow.app.security.Authenticated
import dev.workflow.app.security.Requires
import dev.workflow.app.telemetry.EndpointContext
import io.micrometer.tracing.Tracer
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

/**
 * First interceptor: names the request from its handler's annotation. `@Requires` and `@Authenticated` carry a
 * label; `@Public` routes, static assets and docs use their route (`GET /health`). The origin is the inbound
 * `fleet.origin` baggage if there is one, else this app's own `<app>: <label>`.
 */
@Component
class EndpointLabelInterceptor(private val tracer: ObjectProvider<Tracer>) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val route = "${request.method} ${request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) ?: request.requestURI}"
        val (label, action) = (handler as? HandlerMethod)?.let { declared(it) } ?: (route to fallbackAction(request))
        val origin = EndpointContext.originIn(request.getHeader(EndpointContext.BAGGAGE)) ?: "${EndpointContext.APP}: $label"
        EndpointContext.enter(label.ifBlank { route }, action, origin, tracer.ifAvailable?.currentSpan())
        return true
    }

    private fun declared(h: HandlerMethod): Pair<String, String>? {
        val m = h.method
        m.getAnnotation(Requires::class.java)?.let { return it.label to it.permission }
        m.getAnnotation(Authenticated::class.java)?.let { return it.label to it.action }
        return null
    }

    private fun fallbackAction(request: HttpServletRequest) = if (request.method == "GET") "public.read" else "public.write"
}
