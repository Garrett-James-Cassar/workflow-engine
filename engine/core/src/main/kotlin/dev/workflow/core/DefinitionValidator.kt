package dev.workflow.core

internal const val TIMED_OUT = "TIMED_OUT"

internal val ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]*")

/** Checks a workflow's names, routes and step settings, collecting every error before it throws. */
internal class DefinitionValidator(private val name: String, private val steps: List<Step>) {
    private val errors = mutableListOf<String>()
    private val locations = linkedMapOf<String, Definition.Location>()
    private val pathNames = mutableSetOf<String>()

    fun validate(): Map<String, Definition.Location> {
        if (!ID.matches(name)) errors += "workflow name '$name' must match ${ID.pattern}"
        if (steps.isEmpty()) throw WorkflowDefinitionException(errors + "workflow '$name' has no steps")
        index(steps, null)
        locations.values.forEach { check(it.step) }
        if (errors.isNotEmpty()) throw WorkflowDefinitionException(errors)
        return locations
    }

    private fun index(sequence: List<Step>, branchedFrom: String?) =
        sequence.forEachIndexed { i, step ->
            register(step, Definition.Location(step, sequence, i, branchedFrom))
            step.routes.values.filterIsInstance<Route.Path>().forEach { path(step, it) }
        }

    private fun register(step: Step, location: Definition.Location) {
        if (!ID.matches(step.id)) errors += "step id '${step.id}' must match ${ID.pattern}"
        if (step.id in locations) errors += "duplicate step id '${step.id}'" else locations[step.id] = location
    }

    private fun path(step: Step, path: Route.Path) {
        if (!pathNames.add(path.name)) errors += "duplicate path name '${path.name}'"
        if (path.steps.isEmpty()) errors += "path '${path.name}' (from '${step.id}') has no steps" else index(path.steps, step.id)
    }

    private fun check(step: Step) {
        step.routes.forEach { (outcome, route) -> route(step, outcome, route) }
        when (step) {
            is Step.ServiceTask -> service(step)
            is Step.EventWait -> eventWait(step)
            is Step.UserTask, is Step.Decision -> Unit
        }
    }

    private fun route(step: Step, outcome: String, route: Route) {
        if (route is Route.Goto && route.step !in locations) {
            errors += "step '${step.id}' outcome $outcome: goto unknown step '${route.step}'"
        }
        if (route is Route.End && !ID.matches(route.status)) {
            errors += "step '${step.id}' outcome $outcome: end status '${route.status}' must match ${ID.pattern}"
        }
    }

    private fun service(step: Step.ServiceTask) {
        if (step.handler == null && step.http == null) errors += "service task '${step.id}' has no handler<…>() or http { }"
        if (step.handler != null && step.http != null) errors += "service task '${step.id}' has both a handler and http { }; pick one"
        if (step.retries < 0) errors += "service task '${step.id}' retries must be >= 0"
    }

    private fun eventWait(step: Step.EventWait) {
        val hasTimeoutOutcome = TIMED_OUT in step.outcomes
        if (hasTimeoutOutcome && step.timeout == null) errors += "event wait '${step.id}' routes TIMED_OUT but has no timeout(…)"
        if (!hasTimeoutOutcome && step.timeout != null) {
            errors += "event wait '${step.id}' has a timeout; declare it with ReceivedOrTimedOut::class and route TIMED_OUT"
        }
    }
}
