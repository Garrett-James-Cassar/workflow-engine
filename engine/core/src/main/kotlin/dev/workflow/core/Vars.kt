package dev.workflow.core

/**
 * Read-only view over an instance's variables, addressed by dotted paths such as `doc.notional`.
 * Each completed step stores its record under its id: `legal-review.outcome`, `legal-review.comment`.
 */
class Vars(val values: Map<String, Any?>) {
    operator fun get(path: String): Any? =
        path.split('.').fold(values as Any?) { node, key -> (node as? Map<*, *>)?.get(key) }

    fun has(path: String): Boolean = get(path) != null

    fun str(path: String): String =
        get(path)?.toString() ?: throw MissingVarException(path)

    fun num(path: String): Double = when (val v = get(path)) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull() ?: throw MissingVarException(path, "is not a number: $v")
        null -> throw MissingVarException(path)
        else -> throw MissingVarException(path, "is not a number: $v")
    }

    fun bool(path: String): Boolean = when (val v = get(path)) {
        is Boolean -> v
        is String -> v.toBooleanStrict()
        null -> throw MissingVarException(path)
        else -> throw MissingVarException(path, "is not a boolean: $v")
    }

    /** Replace `{a.b}` placeholders with values from vars. */
    fun fill(template: String): String =
        PLACEHOLDER.replace(template) { str(it.groupValues[1]) }

    private companion object {
        val PLACEHOLDER = Regex("""\{([A-Za-z0-9_.\-]+)}""")
    }
}

class MissingVarException(path: String, problem: String = "is missing") :
    RuntimeException("var '$path' $problem")
