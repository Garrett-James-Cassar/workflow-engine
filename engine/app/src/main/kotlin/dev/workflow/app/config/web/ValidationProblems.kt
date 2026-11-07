package dev.workflow.app.config.web

import dev.workflow.app.api.dtos.ErrorView
import dev.workflow.app.api.dtos.FieldProblem
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.validation.FieldError
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import tools.jackson.core.JacksonException
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.databind.exc.UnrecognizedPropertyException
import tools.jackson.module.kotlin.KotlinInvalidNullException

/**
 * Every rejected input answers 400 problem+json with `errors[]` of paths and messages, never the rejected values
 * (SECURITY.md). One warn line lists the paths. The advice also answers the body-size (413) and media-type (415)
 * rejections that surface while a controller argument is read.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ValidationProblems {
    private val log = LoggerFactory.getLogger(ValidationProblems::class.java)

    @ExceptionHandler(BindException::class)
    fun bind(e: BindException) = invalid(e.fieldErrors.map { FieldProblem(it.field, fieldMessage(it)) })

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun method(e: HandlerMethodValidationException) = invalid(
        e.parameterValidationResults.flatMap { r ->
            val name = r.methodParameter.parameterName ?: "parameter"
            r.resolvableErrors.map { FieldProblem(name, it.defaultMessage ?: "Invalid") }
        },
    )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun mismatch(e: MethodArgumentTypeMismatchException) = invalid(listOf(FieldProblem(e.name, "Wrong type")))

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun missing(e: MissingServletRequestParameterException) = invalid(listOf(FieldProblem(e.parameterName, "Required")))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(e: HttpMessageNotReadableException): ResponseEntity<Any> =
        if (generateSequence<Throwable>(e) { it.cause }.any { it is PayloadTooLargeException }) tooLarge() else invalid(body(e.cause))

    @ExceptionHandler(PayloadTooLargeException::class)
    fun tooLarge(): ResponseEntity<Any> = answer(problem(413, ProblemType.TOO_LARGE, "Payload too large", "The request body is over the size limit"))

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun mediaType(): ResponseEntity<Any> = answer(problem(415, ProblemType.UNSUPPORTED_MEDIA, "Unsupported media type", "The body must be application/json"))

    @ExceptionHandler(ResponseInvalidException::class)
    fun responseInvalid(e: ResponseInvalidException): ResponseEntity<Any> {
        log.error("Response does not match its schema at {}", e.paths)
        return answer(problem(500, ProblemType.RESPONSE_INVALID, "Invalid response", "The response did not match its schema", e.paths.map { FieldProblem(it, "Invalid") }))
    }

    private fun fieldMessage(e: FieldError) = if (e.isBindingFailure) "Wrong type" else e.defaultMessage ?: "Invalid"

    private fun body(cause: Throwable?): List<FieldProblem> {
        val path = (cause as? JacksonException)?.path?.joinToString(".") { it.propertyName ?: "[${it.index}]" }?.replace(".[", "[")?.ifEmpty { null } ?: "body"
        val message = when (cause) {
            is UnrecognizedPropertyException -> "Unknown field"
            is KotlinInvalidNullException -> "Required"
            is MismatchedInputException -> "Wrong type"
            else -> "Malformed JSON"
        }
        return listOf(FieldProblem(path, message))
    }

    private fun invalid(errors: List<FieldProblem>): ResponseEntity<Any> {
        log.warn("Rejected request: invalid input at {}", errors.map { it.path })
        return answer(problem(400, ProblemType.VALIDATION, "Invalid request", "The request did not match its schema", errors))
    }

    private fun answer(body: ErrorView): ResponseEntity<Any> =
        ResponseEntity.status(body.status).contentType(MediaType.parseMediaType(PROBLEM_JSON)).body(body)
}
