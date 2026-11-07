package dev.workflow.app.config.web

import dev.workflow.app.api.dtos.ErrorView
import dev.workflow.app.api.dtos.FieldProblem
import jakarta.servlet.http.HttpServletResponse
import tools.jackson.databind.json.JsonMapper

/** The problem types of SECURITY.md. */
object ProblemType {
    const val VALIDATION = "https://fleet.local/problems/validation"
    const val RATE_LIMITED = "https://fleet.local/problems/rate-limited"
    const val TOO_LARGE = "https://fleet.local/problems/payload-too-large"
    const val UNSUPPORTED_MEDIA = "https://fleet.local/problems/unsupported-media-type"
    const val RESPONSE_INVALID = "https://fleet.local/problems/response-invalid"
}

const val PROBLEM_JSON = "application/problem+json"

fun problem(status: Int, type: String, title: String, detail: String, errors: List<FieldProblem>? = null) =
    ErrorView(error = detail, title = title, status = status, detail = detail, type = type, errors = errors)

/** Writes a problem+json answer from a filter or interceptor, where no controller advice runs. */
fun HttpServletResponse.writeProblem(json: JsonMapper, body: ErrorView) {
    status = body.status
    contentType = PROBLEM_JSON
    characterEncoding = "UTF-8"
    writer.write(json.writeValueAsString(body))
}

/** The body went over the size cap while it was being read. */
class PayloadTooLargeException : RuntimeException("The request body is over the size limit")
