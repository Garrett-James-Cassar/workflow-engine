package dev.workflow.core

class WorkflowDefinitionException(val errors: List<String>) :
    RuntimeException("invalid workflow definition:\n" + errors.joinToString("\n") { "  - $it" })

/** Where an outcome resolves to once `next()`, paths and rejoins are worked out. */
sealed interface Target {
    data class ToStep(val id: String) : Target

    data class ToEnd(val status: String) : Target
}

/** A validated workflow: steps plus the resolved navigation every outcome takes. */
class Definition private constructor(
    val name: String,
    val version: Int,
    /** The main flow, in declaration order. Steps inside paths hang off their routes. */
    val steps: List<Step>,
    val sample: Map<String, Any?>,
    private val locations: Map<String, Location>,
) {
    internal class Location(val step: Step, val sequence: List<Step>, val index: Int, val branchedFrom: String?)

    /** Every step, main flow and paths, in pre-order (a step, then the paths its outcomes open). */
    val allSteps: List<Step> = locations.values.map { it.step }

    val first: Step get() = steps.first()

    fun step(id: String): Step = locations[id]?.step ?: throw IllegalArgumentException("no step '$id' in $name v$version")

    fun hasStep(id: String): Boolean = id in locations

    /** Where [outcome] of step [stepId] leads. */
    fun resolve(stepId: String, outcome: String): Target {
        val step = step(stepId)
        val route = step.routes[outcome] ?: throw IllegalArgumentException("step '$stepId' has no outcome '$outcome'")
        return when (route) {
            Route.Next -> successor(stepId)
            is Route.Goto -> Target.ToStep(route.step)
            is Route.End -> Target.ToEnd(route.status)
            is Route.Path -> Target.ToStep(route.steps.first().id)
        }
    }

    /** The step after [stepId] in its sequence; past the end of a path, rejoin after its branching step. */
    fun successor(stepId: String): Target {
        val loc = locations.getValue(stepId)
        return when {
            loc.index + 1 < loc.sequence.size -> Target.ToStep(loc.sequence[loc.index + 1].id)
            loc.branchedFrom == null -> Target.ToEnd(COMPLETED)
            else -> successor(loc.branchedFrom)
        }
    }

    companion object {
        /** End status of a workflow that runs off the end of its main flow. */
        const val COMPLETED = "completed"

        private val ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]*")

        internal fun build(name: String, version: Int, steps: List<Step>, sample: Map<String, Any?>): Definition {
            val errors = mutableListOf<String>()
            if (!ID.matches(name)) errors += "workflow name '$name' must match ${ID.pattern}"
            if (steps.isEmpty()) throw WorkflowDefinitionException(errors + "workflow '$name' has no steps")

            val locations = linkedMapOf<String, Location>()
            val pathNames = mutableSetOf<String>()

            fun index(sequence: List<Step>, branchedFrom: String?) {
                sequence.forEachIndexed { i, step ->
                    if (!ID.matches(step.id)) errors += "step id '${step.id}' must match ${ID.pattern}"
                    if (step.id in locations) errors += "duplicate step id '${step.id}'"
                    else locations[step.id] = Location(step, sequence, i, branchedFrom)
                    step.routes.values.filterIsInstance<Route.Path>().forEach { path ->
                        if (!pathNames.add(path.name)) errors += "duplicate path name '${path.name}'"
                        if (path.steps.isEmpty()) errors += "path '${path.name}' (from '${step.id}') has no steps"
                        else index(path.steps, step.id)
                    }
                }
            }
            index(steps, null)

            for (loc in locations.values) {
                val step = loc.step
                for ((outcome, route) in step.routes) {
                    if (route is Route.Goto && route.step !in locations) {
                        errors += "step '${step.id}' outcome $outcome: goto unknown step '${route.step}'"
                    }
                    if (route is Route.End && !ID.matches(route.status)) {
                        errors += "step '${step.id}' outcome $outcome: end status '${route.status}' must match ${ID.pattern}"
                    }
                }
                when (step) {
                    is Step.ServiceTask -> {
                        if (step.handler == null && step.http == null) {
                            errors += "service task '${step.id}' has no handler<…>() or http { }"
                        }
                        if (step.handler != null && step.http != null) {
                            errors += "service task '${step.id}' has both a handler and http { }; pick one"
                        }
                        if (step.retries < 0) errors += "service task '${step.id}' retries must be >= 0"
                    }
                    is Step.EventWait -> {
                        val hasTimeoutOutcome = TIMED_OUT in step.outcomes
                        if (hasTimeoutOutcome && step.timeout == null) {
                            errors += "event wait '${step.id}' routes TIMED_OUT but has no timeout(…)"
                        }
                        if (!hasTimeoutOutcome && step.timeout != null) {
                            errors += "event wait '${step.id}' has a timeout; declare it with ReceivedOrTimedOut::class and route TIMED_OUT"
                        }
                    }
                    is Step.UserTask, is Step.Decision -> Unit
                }
            }

            if (errors.isNotEmpty()) throw WorkflowDefinitionException(errors)

            val definition = Definition(name, version, steps, sample, locations)
            val reachable = definition.reachableSteps()
            val unreachable = locations.keys - reachable
            if (unreachable.isNotEmpty()) {
                throw WorkflowDefinitionException(unreachable.map { "step '$it' is unreachable" })
            }
            return definition
        }

        private const val TIMED_OUT = "TIMED_OUT"
    }

    private fun reachableSteps(): Set<String> {
        val seen = mutableSetOf<String>()
        val queue = ArrayDeque(listOf(first.id))
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!seen.add(id)) continue
            for (outcome in step(id).outcomes) {
                val target = resolve(id, outcome)
                if (target is Target.ToStep) queue += target.id
            }
        }
        return seen
    }
}
