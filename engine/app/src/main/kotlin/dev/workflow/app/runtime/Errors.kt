package dev.workflow.app.runtime

class NotFoundException(message: String) : RuntimeException(message)

class ForbiddenException(message: String, val reason: String) : RuntimeException(message)

class BadRequestException(message: String) : RuntimeException(message)

class ConflictException(message: String) : RuntimeException(message)

class UnauthenticatedException : RuntimeException("Sign in: send the identity this app expects")
