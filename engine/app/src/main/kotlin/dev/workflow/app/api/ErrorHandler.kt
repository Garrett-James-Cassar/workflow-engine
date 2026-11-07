package dev.workflow.app.api

import dev.workflow.app.api.dtos.ErrorView
import dev.workflow.app.domain.BadRequestException
import dev.workflow.app.domain.ConflictException
import dev.workflow.app.domain.ForbiddenException
import dev.workflow.app.domain.NotFoundException
import dev.workflow.app.domain.RbacUnavailableException
import dev.workflow.app.domain.UnauthenticatedException
import dev.workflow.app.domain.UnknownDefinitionException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

const val USER_HEADER = "X-User"

@RestControllerAdvice
class ErrorHandler {
    @ExceptionHandler(NotFoundException::class, UnknownDefinitionException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: RuntimeException) = problem(404, "Not found", e.message ?: "not found")

    /** A denial: 403 as problem+json, with rbac's reason in words. */
    @ExceptionHandler(ForbiddenException::class)
    fun forbidden(e: ForbiddenException): ResponseEntity<Any> = problemResponse(403, "Not allowed", e.message ?: "forbidden", e.reason)

    @ExceptionHandler(UnauthenticatedException::class)
    fun unauthenticated(e: UnauthenticatedException): ResponseEntity<Any> = problemResponse(401, "Not signed in", e.message ?: "unauthenticated")

    /** rbac is down: the request fails closed. */
    @ExceptionHandler(RbacUnavailableException::class)
    fun rbacDown(e: RbacUnavailableException): ResponseEntity<Any> = problemResponse(503, "Access control unavailable", e.message ?: "rbac unavailable")

    @ExceptionHandler(BadRequestException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun badRequest(e: BadRequestException) = problem(400, "Invalid request", e.message ?: "bad request")

    @ExceptionHandler(ConflictException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun conflict(e: ConflictException) = problem(409, "Conflict", e.message ?: "conflict")

    private fun problem(status: Int, title: String, detail: String, reason: String? = null) = ErrorView(detail, reason, title, status, detail)

    private fun problemResponse(status: Int, title: String, detail: String, reason: String? = null): ResponseEntity<Any> =
        ResponseEntity.status(status).contentType(MediaType("application", "problem+json")).body(problem(status, title, detail, reason))
}
