package dev.workflow.app.runtime

import dev.workflow.app.domain.BadRequestException
import dev.workflow.core.FieldType
import dev.workflow.core.FormField
import dev.workflow.core.Step

/** The form a person submits with a task decision: known fields only, required ones present, typed as declared. */
object FormValidation {
    fun check(step: Step.UserTask, form: Map<String, Any?>) {
        val unknown = form.keys - step.form.map { it.name }.toSet()
        if (unknown.isNotEmpty()) throw BadRequestException("unknown form field(s) $unknown; expected ${step.form.map { it.name }}")
        step.form.forEach { checkField(it, form[it.name]) }
    }

    private fun checkField(field: FormField, value: Any?) {
        if (value == null) {
            if (field.required) throw BadRequestException("form field '${field.name}' is required")
            return
        }
        if (!matches(field.type, value)) throw BadRequestException("form field '${field.name}' must be ${field.type.name.lowercase()}")
    }

    private fun matches(type: FieldType, value: Any): Boolean = when (type) {
        FieldType.TEXT -> value is String
        FieldType.NUMBER -> value is Number
        FieldType.BOOLEAN -> value is Boolean
    }
}
