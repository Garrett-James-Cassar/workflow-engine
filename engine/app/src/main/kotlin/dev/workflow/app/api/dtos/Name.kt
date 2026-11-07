package dev.workflow.app.api.dtos

import jakarta.validation.Constraint
import jakarta.validation.Payload
import jakarta.validation.ReportAsSingleViolation
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import kotlin.reflect.KClass

/** An identifier or name in a path, query or body: 1 to 100 characters of letters, digits and `. _ : -`. */
@NotBlank
@Size(max = Limits.NAME)
@Pattern(regexp = Limits.ID)
@ReportAsSingleViolation
@Constraint(validatedBy = [])
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
annotation class Name(
    val message: String = "Invalid name",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
