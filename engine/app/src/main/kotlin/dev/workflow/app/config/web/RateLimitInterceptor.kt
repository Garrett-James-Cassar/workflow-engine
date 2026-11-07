package dev.workflow.app.config.web

import dev.workflow.app.security.Identity
import dev.workflow.app.security.Requires
import dev.workflow.app.telemetry.EndpointContext
import io.github.bucket4j.ConsumptionProbe
import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.TimeUnit

private val EXEMPT = listOf("/health", "/ready", "/metrics", "/docs", "/openapi.json", "/v3/api-docs", "/swagger-ui")

/**
 * Rate limit (SECURITY.md), after identity so the key is the signed-in user, else the client IP. Health, ready,
 * metrics and docs are exempt, as is anything that is not a controller route (static files). Every answer carries
 * `RateLimit-Limit/Remaining/Reset`; over the limit it is 429 problem+json with `Retry-After`, a warn line and the
 * `fleet.rate_limit.rejected` counter.
 */
@Component
class RateLimitInterceptor(
    private val props: RateLimitProperties,
    private val json: JsonMapper,
    private val meters: ObjectProvider<MeterRegistry>,
) : HandlerInterceptor {
    private val log = LoggerFactory.getLogger(RateLimitInterceptor::class.java)
    private val limiter = RateLimiter(props)

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (!props.enabled || handler !is HandlerMethod || exempt(request.requestURI)) return true
        val rateClass = classify(handler, request.method)
        val probe = limiter.take(key(request), rateClass)
        headers(response, props.limit(rateClass), probe)
        return probe.isConsumed || reject(response, rateClass, probe)
    }

    private fun exempt(uri: String): Boolean {
        val path = uri.removePrefix(API_PREFIX)
        return EXEMPT.any { path == it || path.startsWith("$it/") }
    }

    private fun classify(handler: HandlerMethod, method: String): RateClass = when {
        handler.getMethodAnnotation(Requires::class.java)?.heavy == true -> RateClass.HEAVY
        method == "GET" || method == "HEAD" -> RateClass.READ
        else -> RateClass.WRITE
    }

    private fun key(request: HttpServletRequest): String = (request.getAttribute(Identity.LOGIN) as? String)?.let { "user:$it" } ?: "ip:${request.remoteAddr}"

    private fun headers(response: HttpServletResponse, limit: Int, probe: ConsumptionProbe) {
        response.setHeader("RateLimit-Limit", limit.toString())
        response.setHeader("RateLimit-Remaining", probe.remainingTokens.toString())
        response.setHeader("RateLimit-Reset", seconds(probe.nanosToWaitForReset).toString())
    }

    private fun reject(response: HttpServletResponse, rateClass: RateClass, probe: ConsumptionProbe): Boolean {
        val retry = seconds(probe.nanosToWaitForRefill)
        log.warn("Rate limit exceeded for the {} class; retry in {}s", rateClass.label, retry)
        meters.ifAvailable?.counter("fleet.rate_limit.rejected", "fleet.action", org.slf4j.MDC.get(EndpointContext.ACTION) ?: "unknown", "class", rateClass.label)?.increment()
        response.setHeader("Retry-After", retry.toString())
        response.writeProblem(json, problem(429, ProblemType.RATE_LIMITED, "Too many requests", "Rate limit exceeded; retry in $retry seconds"))
        return false
    }

    private fun seconds(nanos: Long): Long = maxOf(1L, TimeUnit.NANOSECONDS.toSeconds(nanos + TimeUnit.SECONDS.toNanos(1) - 1))
}
