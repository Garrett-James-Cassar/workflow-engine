package dev.workflow.app.config.web

import dev.workflow.app.security.AuthMode
import dev.workflow.app.security.AuthProperties
import jakarta.validation.Validator
import org.slf4j.LoggerFactory
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

/** A response failed its schema. Raised in dev and test so the tests catch it; production logs it and still sends. */
class ResponseInvalidException(val paths: List<String>) : RuntimeException("Response does not match its schema")

/**
 * Validates every response body against the constraints on its DTO (the same schema model as the requests). In dev
 * and test a mismatch fails the request with 500; in live it is logged at error and the response goes out.
 */
@RestControllerAdvice
class ResponseValidation(private val validator: Validator, auth: AuthProperties) : ResponseBodyAdvice<Any> {
    private val log = LoggerFactory.getLogger(ResponseValidation::class.java)
    private val strict = auth.authMode == AuthMode.DEV

    override fun supports(returnType: MethodParameter, converterType: Class<out HttpMessageConverter<*>>) = true

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        contentType: MediaType,
        converterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse,
    ): Any? {
        val paths = elements(body).flatMap { e -> validator.validate(e).map { it.propertyPath.toString() } }
        if (paths.isEmpty()) return body
        if (strict) throw ResponseInvalidException(paths)
        log.error("Response does not match its schema at {}", paths)
        return body
    }

    private fun elements(body: Any?): List<Any> = when {
        body is Collection<*> -> body.filterNotNull().filter(::ours)
        body != null && ours(body) -> listOf(body)
        else -> emptyList()
    }

    private fun ours(o: Any) = o.javaClass.name.startsWith("dev.workflow.app.api.dtos.")
}
