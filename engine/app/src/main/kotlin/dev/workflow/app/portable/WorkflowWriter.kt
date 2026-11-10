package dev.workflow.app.portable

import dev.workflow.core.Assignee
import dev.workflow.core.Definition
import dev.workflow.core.FormField
import dev.workflow.core.HttpSpec
import dev.workflow.core.Route
import dev.workflow.core.Step

/** A workflow definition as plain JSON values: the write half of [WorkflowCodec]. */
internal object WorkflowWriter {
    fun fields(d: Definition): Map<String, Any?> =
        mapOf("name" to d.name, "workflowVersion" to d.version, "sample" to d.sample, "steps" to d.steps.map(::step))

    private fun step(s: Step): Map<String, Any?> =
        mapOf("id" to s.id, "label" to s.label, "outcomes" to s.outcomes, "routes" to s.routes.mapValues { route(it.value) }) + own(s)

    private fun own(s: Step): Map<String, Any?> = when (s) {
        is Step.UserTask -> userTask(s)
        is Step.ServiceTask -> mapOf("type" to "service-task", "handler" to s.handler?.java?.name, "http" to s.http?.let { http(s.id, it) }, "retries" to s.retries)
        is Step.EventWait -> eventWait(s)
        is Step.Decision -> mapOf("type" to "decision", "evaluator" to "code", "descriptions" to s.descriptions)
    }

    private fun userTask(s: Step.UserTask) = mapOf(
        "type" to "user-task", "assignee" to assignee(s.assignee), "form" to s.form.map(::field), "reassignableBy" to s.reassignableBy?.let(::assignee),
    )

    private fun eventWait(s: Step.EventWait) = mapOf(
        "type" to "event-wait", "event" to s.event, "timeout" to s.timeout?.toIsoString(),
        "correlation" to s.correlation?.let { mapOf("key" to it.first, "path" to it.second) },
    )

    private fun route(r: Route): Map<String, Any?> = when (r) {
        Route.Next -> mapOf("type" to "next")
        is Route.Goto -> mapOf("type" to "goto", "step" to r.step)
        is Route.End -> mapOf("type" to "end", "status" to r.status)
        is Route.Path -> mapOf("type" to "path", "name" to r.name, "steps" to r.steps.map(::step))
    }

    private fun assignee(a: Assignee): Map<String, Any?> = when (a) {
        is Assignee.Role -> mapOf("role" to a.name, "scope" to a.scope, "scopeFrom" to a.scopeFrom)
        is Assignee.User -> mapOf("user" to a.name)
        is Assignee.UserFrom -> mapOf("userFrom" to a.path)
    }

    private fun field(f: FormField) = mapOf("name" to f.name, "type" to f.type.name.lowercase(), "required" to f.required)

    private fun http(stepId: String, h: HttpSpec) =
        mapOf("method" to h.method.name, "url" to h.url, "bodyFrom" to h.bodyFrom, "headers" to h.headers.mapValues { (name, value) -> header(stepId, name, value) })

    /** A header that is not filled from vars is a literal, possibly a credential: it leaves as a `${VAR}` placeholder, never as itself. */
    private fun header(stepId: String, name: String, value: String): String =
        if (VARIABLE.containsMatchIn(value) || PLACEHOLDER.matches(value)) value else "\${" + placeholder(stepId, name) + "}"

    private fun placeholder(stepId: String, name: String) = "${stepId}_$name".uppercase().replace(Regex("[^A-Z0-9]+"), "_")

    private val VARIABLE = Regex("\\{[A-Za-z0-9_.-]+}")
    private val PLACEHOLDER = Regex("\\$\\{[A-Z0-9_]+}")
}
