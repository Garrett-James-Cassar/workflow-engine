package dev.workflow.core

import kotlin.reflect.KClass
import kotlin.time.Duration

@DslMarker
annotation class WorkflowDsl

/**
 * A workflow written in the Kotlin DSL. Declare one as an `object`:
 *
 * ```
 * object Approval : Workflow("approval", version = 1, {
 *     userTask("legal-review", LegalReview::class) {
 *         assignTo(role("legal"))
 *         on { when (it) { APPROVE -> next(); REJECT -> end("rejected") } }
 *     }
 * })
 * ```
 *
 * The definition is built and validated on first access, which the app forces at startup.
 */
abstract class Workflow(val name: String, val version: Int, private val block: WorkflowBuilder.() -> Unit) {
    val definition: Definition by lazy { WorkflowBuilder().apply(block).build(name, version) }
}

/**
 * Proof that every outcome of a step was routed. Only [OutcomeStepBuilder.on] can make one, and every
 * multi-outcome step's builder block must end with it — so a missing `on` is a compile error, and a
 * `when` that misses an outcome is a compile error too.
 */
class Routed<O : Enum<O>> internal constructor(internal val routes: Map<String, Route>)

@WorkflowDsl
open class StepsBuilder internal constructor(internal val pathName: String?) {
    internal val steps = mutableListOf<Step>()

    /** A user task with a single outcome (`Done.OK`), e.g. an acknowledgement. Continues with `next()`. */
    fun userTask(id: String, block: UserTaskBuilder<Done>.() -> Unit) {
        val b = UserTaskBuilder(id, pathName, Done::class)
        b.block()
        steps += b.build(b.defaultRoutes())
    }

    /** A user task whose outcomes are [outcomes]. The block must end with `on { when (it) { … } }`. */
    fun <O : Enum<O>> userTask(id: String, outcomes: KClass<O>, block: UserTaskBuilder<O>.() -> Routed<O>) {
        val b = UserTaskBuilder(id, pathName, outcomes)
        steps += b.build(b.block().routes)
    }

    /** A service task returning `Done.OK`. Link code with `handler<H>()` or a built-in `http { }` call. */
    fun serviceTask(id: String, block: ServiceTaskBuilder<Done>.() -> Unit) {
        val b = ServiceTaskBuilder(id, pathName, Done::class)
        b.block()
        steps += b.build(b.defaultRoutes())
    }

    /** A service task whose handler returns one of [outcomes]. The block must end with `on { … }`. */
    fun <O : Enum<O>> serviceTask(id: String, outcomes: KClass<O>, block: ServiceTaskBuilder<O>.() -> Routed<O>) {
        val b = ServiceTaskBuilder(id, pathName, outcomes)
        steps += b.build(b.block().routes)
    }

    /** Wait for `POST /events/{event}`. No timeout, so the only outcome is RECEIVED. */
    fun eventWait(id: String, block: EventWaitBuilder<Received>.() -> Unit = {}) {
        val b = EventWaitBuilder(id, pathName, Received::class)
        b.block()
        steps += b.build(b.defaultRoutes())
    }

    /** Wait for an event with a `timeout(…)`; route both RECEIVED and TIMED_OUT. */
    fun eventWait(
        id: String,
        outcomes: KClass<ReceivedOrTimedOut>,
        block: EventWaitBuilder<ReceivedOrTimedOut>.() -> Routed<ReceivedOrTimedOut>,
    ) {
        val b = EventWaitBuilder(id, pathName, outcomes)
        steps += b.build(b.block().routes)
    }

    /** A side-effect-free branch: `evaluate { … }` picks the outcome from vars. */
    fun <O : Enum<O>> decision(id: String, outcomes: KClass<O>, block: DecisionBuilder<O>.() -> Routed<O>) {
        val b = DecisionBuilder(id, pathName, outcomes)
        steps += b.build(b.block().routes)
    }
}

class WorkflowBuilder internal constructor() : StepsBuilder(null) {
    private var sample: Map<String, Any?> = emptyMap()

    /** Example vars used by preview and tests. */
    fun sample(vararg vars: Pair<String, Any?>) {
        sample = mapOf(*vars)
    }

    internal fun build(name: String, version: Int) = Definition.build(name, version, steps.toList(), sample)
}

@WorkflowDsl
class RouteScope internal constructor() {
    fun next(): Route = Route.Next

    fun goto(step: String): Route = Route.Goto(step)

    fun end(status: String): Route = Route.End(status)

    fun path(name: String, block: StepsBuilder.() -> Unit): Route =
        Route.Path(name, StepsBuilder(name).apply(block).steps.toList())
}

@WorkflowDsl
abstract class OutcomeStepBuilder<O : Enum<O>> internal constructor(
    val id: String,
    internal val pathName: String?,
    outcomes: KClass<O>,
) {
    var label: String = id
    internal val constants: List<O> = outcomes.java.enumConstants.toList()

    /** Route every outcome. Write the body as a `when (it)` so the compiler checks it is exhaustive. */
    fun on(block: RouteScope.(O) -> Route): Routed<O> {
        val scope = RouteScope()
        return Routed(constants.associate { it.name to scope.block(it) })
    }

    internal fun defaultRoutes(): Map<String, Route> = constants.associate { it.name to Route.Next }

    internal val outcomeNames: List<String> get() = constants.map { it.name }
}

fun role(name: String, scope: Map<String, String> = emptyMap(), scopeFrom: Map<String, String> = emptyMap()): Assignee =
    Assignee.Role(name, scope, scopeFrom)

fun user(name: String): Assignee = Assignee.User(name)

fun userFrom(path: String): Assignee = Assignee.UserFrom(path)
