package dev.workflow.app.portable

import dev.workflow.core.Assignee
import dev.workflow.core.Done
import dev.workflow.core.FieldType
import dev.workflow.core.FormField
import dev.workflow.core.HttpMethod
import dev.workflow.core.HttpSpec
import dev.workflow.core.Route
import dev.workflow.core.ServiceContext
import dev.workflow.core.ServiceHandler
import dev.workflow.core.ServiceResult
import dev.workflow.core.Step
import kotlin.time.Duration

/** Stands in for a handler class this app does not have. The import is refused with a `missing-ref`, so it never runs. */
class UnresolvedHandler : ServiceHandler<Done> {
    override fun execute(ctx: ServiceContext): ServiceResult<Done> = error("handler is not available in this app")
}

/** A decision's code is not portable: an imported decision stops the run instead of guessing. */
internal fun imported(stepId: String): (dev.workflow.core.Vars) -> String = { error("decision '$stepId' came from an import and has no code here") }

/** What every step carries, read before its own fields. */
private class Head(val id: String, val label: String, val outcomes: List<String>, val routes: Map<String, Route>, val path: String?)

/** Reads steps from a document, failing at the JSON pointer of the first thing that is wrong. */
internal object WorkflowReader {
    private val COMMON = setOf("type", "id", "label", "outcomes", "routes")

    fun steps(n: Node, pathName: String?): List<Step> = n.items().map { step(it, pathName) }

    private fun step(n: Node, pathName: String?): Step {
        val outcomes = n["outcomes"].items().map { it.text() }
        val routes = routes(n["routes"], outcomes, pathName)
        val head = Head(n["id"].text(), n["label"].text(), outcomes, routes, pathName)
        return when (n["type"].text()) {
            "user-task" -> userTask(n.only(COMMON + setOf("assignee", "form", "reassignableBy")), head)
            "service-task" -> serviceTask(n.only(COMMON + setOf("handler", "http", "retries")), head)
            "event-wait" -> eventWait(n.only(COMMON + setOf("event", "timeout", "correlation")), head)
            "decision" -> decision(n.only(COMMON + setOf("evaluator", "descriptions")), head)
            else -> n["type"].fail("must be user-task, service-task, event-wait or decision")
        }
    }

    private fun routes(n: Node, outcomes: List<String>, pathName: String?): Map<String, Route> {
        n.obj().keys.firstOrNull { it !in outcomes }?.let { n[it].fail("is not an outcome of this step") }
        return outcomes.associateWith { o -> if (n.obj()[o] == null) n.fail("no route for outcome $o") else route(n[o], pathName) }
    }

    private fun route(n: Node, pathName: String?): Route = when (n["type"].text()) {
        "next" -> Route.Next
        "goto" -> Route.Goto(n.only(setOf("type", "step"))["step"].text())
        "end" -> Route.End(n.only(setOf("type", "status"))["status"].text())
        "path" -> n.only(setOf("type", "name", "steps")).let { Route.Path(it["name"].text(), steps(it["steps"], it["name"].text())) }
        else -> n["type"].fail("must be next, goto, end or path")
    }

    private fun userTask(n: Node, h: Head) =
        Step.UserTask(
            h.id, h.label, h.outcomes, h.routes, h.path, assignee(n["assignee"]), n["form"].items().map(::field),
            n["reassignableBy"].let { if (it.isNull()) null else assignee(it) as? Assignee.Role ?: it.fail("must be a role") },
        )

    private fun assignee(n: Node): Assignee = when {
        n.obj().containsKey("role") -> n.only(setOf("role", "scope", "scopeFrom")).let { Assignee.Role(it["role"].text(), it["scope"].strings(), it["scopeFrom"].strings()) }
        n.obj().containsKey("user") -> Assignee.User(n.only(setOf("user"))["user"].text())
        n.obj().containsKey("userFrom") -> Assignee.UserFrom(n.only(setOf("userFrom"))["userFrom"].text())
        else -> n.fail("must have role, user or userFrom")
    }

    private fun field(n: Node): FormField {
        n.only(setOf("name", "type", "required"))
        val type = FieldType.entries.firstOrNull { it.name.lowercase() == n["type"].text() } ?: n["type"].fail("must be text, number or boolean")
        return FormField(n["name"].text(), type, n["required"].bool())
    }

    private fun serviceTask(n: Node, h: Head) =
        Step.ServiceTask(h.id, h.label, h.outcomes, h.routes, h.path, handler(n["handler"]), n["http"].let { if (it.isNull()) null else http(it) }, n["retries"].int())

    private fun handler(n: Node) = if (n.isNull()) null else HandlerNames.find(n.text()) ?: UnresolvedHandler::class

    private fun http(n: Node): HttpSpec {
        n.only(setOf("method", "url", "bodyFrom", "headers"))
        val method = HttpMethod.entries.firstOrNull { it.name == n["method"].text() } ?: n["method"].fail("must be GET, POST, PUT, PATCH or DELETE")
        return HttpSpec(method, n["url"].text(), n["bodyFrom"].let { if (it.isNull()) null else it.text() }, n["headers"].strings())
    }

    private fun eventWait(n: Node, h: Head): Step.EventWait {
        val timeout = n["timeout"].let { if (it.isNull()) null else duration(it) }
        val correlation = n["correlation"].let { if (it.isNull()) null else it.only(setOf("key", "path"))["key"].text() to it["path"].text() }
        return Step.EventWait(h.id, h.label, h.outcomes, h.routes, h.path, n["event"].text(), correlation, timeout)
    }

    private fun duration(n: Node): Duration = runCatching { Duration.parseIsoString(n.text()) }.getOrElse { n.fail("must be an ISO-8601 duration such as PT1M") }

    private fun decision(n: Node, h: Head): Step.Decision {
        if (n["evaluator"].text() != "code") n["evaluator"].fail("must be \"code\"")
        return Step.Decision(h.id, h.label, h.outcomes, h.routes, h.path, imported(h.id), n["descriptions"].strings())
    }
}
