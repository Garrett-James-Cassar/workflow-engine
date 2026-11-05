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

class UserTaskBuilder<O : Enum<O>> internal constructor(id: String, pathName: String?, outcomes: KClass<O>) :
    OutcomeStepBuilder<O>(id, pathName, outcomes) {
    private var assignee: Assignee? = null
    private val fields = mutableListOf<FormField>()
    private var reassignableBy: Assignee.Role? = DEFAULT_REASSIGNER

    fun assignTo(assignee: Assignee) {
        this.assignee = assignee
    }

    /** Who may move this task to another user or team. Defaults to the `ops` role. */
    fun reassignableBy(role: Assignee.Role) {
        reassignableBy = role
    }

    /** Turn reassignment off for this task. */
    fun notReassignable() {
        reassignableBy = null
    }

    fun form(block: FormBuilder.() -> Unit) {
        fields += FormBuilder().apply(block).fields
    }

    internal fun build(routes: Map<String, Route>) = Step.UserTask(
        id, label, outcomeNames, routes, pathName,
        assignee ?: throw WorkflowDefinitionException(listOf("user task '$id' has no assignTo(...)")),
        fields.toList(),
        reassignableBy,
    )
}

@WorkflowDsl
class FormBuilder internal constructor() {
    internal val fields = mutableListOf<FormField>()

    fun text(name: String, required: Boolean = false) {
        fields += FormField(name, FieldType.TEXT, required)
    }

    fun number(name: String, required: Boolean = false) {
        fields += FormField(name, FieldType.NUMBER, required)
    }

    fun boolean(name: String, required: Boolean = false) {
        fields += FormField(name, FieldType.BOOLEAN, required)
    }
}

class ServiceTaskBuilder<O : Enum<O>> internal constructor(id: String, pathName: String?, outcomes: KClass<O>) :
    OutcomeStepBuilder<O>(id, pathName, outcomes) {
    @PublishedApi
    internal var handlerClass: KClass<out ServiceHandler<*>>? = null
    internal var http: HttpSpec? = null

    /** Extra attempts after the first failure, before the instance is marked failed (an incident). */
    var retries: Int = 2

    /** Link the Kotlin code to run. Its outcome type must match this task's. */
    inline fun <reified H : ServiceHandler<O>> handler() {
        handlerClass = H::class
    }

    internal fun build(routes: Map<String, Route>) =
        Step.ServiceTask(id, label, outcomeNames, routes, pathName, handlerClass, http, retries)
}

/** Built-in HTTP call. Only for `Done` service tasks: a business outcome needs your own handler. */
fun ServiceTaskBuilder<Done>.http(block: HttpBuilder.() -> Unit) {
    http = HttpBuilder().apply(block).build()
}

@WorkflowDsl
class HttpBuilder internal constructor() {
    private var method: HttpMethod? = null
    private var url: String? = null
    private var bodyFrom: String? = null
    private val headers = linkedMapOf<String, String>()

    fun get(url: String) = call(HttpMethod.GET, url)

    fun post(url: String) = call(HttpMethod.POST, url)

    fun put(url: String) = call(HttpMethod.PUT, url)

    fun patch(url: String) = call(HttpMethod.PATCH, url)

    fun delete(url: String) = call(HttpMethod.DELETE, url)

    /** Send the value at this vars path as the JSON body. */
    fun bodyFrom(path: String) {
        bodyFrom = path
    }

    fun header(name: String, value: String) {
        headers[name] = value
    }

    private fun call(method: HttpMethod, url: String) {
        this.method = method
        this.url = url
    }

    internal fun build(): HttpSpec {
        val m = method ?: throw WorkflowDefinitionException(listOf("http { } needs get/post/put/patch/delete(url)"))
        return HttpSpec(m, url!!, bodyFrom, headers.toMap())
    }
}

class EventWaitBuilder<O : Enum<O>> internal constructor(id: String, pathName: String?, outcomes: KClass<O>) :
    OutcomeStepBuilder<O>(id, pathName, outcomes) {
    /** Name in `POST /events/{event}`. Defaults to the step id. */
    var event: String = id
    private var correlation: Pair<String, String>? = null
    private var timeout: Duration? = null

    /** Match only events whose `correlation[key]` equals the value at `varsPath`. */
    fun correlateBy(keyToVarsPath: Pair<String, String>) {
        correlation = keyToVarsPath
    }

    fun timeout(after: Duration) {
        timeout = after
    }

    internal fun build(routes: Map<String, Route>) =
        Step.EventWait(id, label, outcomeNames, routes, pathName, event, correlation, timeout)
}

@WorkflowDsl
class DecisionScope internal constructor(val vars: Vars)

class DecisionBuilder<O : Enum<O>> internal constructor(id: String, pathName: String?, outcomes: KClass<O>) :
    OutcomeStepBuilder<O>(id, pathName, outcomes) {
    private var evaluator: (DecisionScope.() -> O)? = null
    private val descriptions = linkedMapOf<String, String>()

    fun evaluate(block: DecisionScope.() -> O) {
        evaluator = block
    }

    /** A readable label for an outcome's edge in the diagram. */
    fun describe(outcome: O, text: String) {
        descriptions[outcome.name] = text
    }

    internal fun build(routes: Map<String, Route>): Step.Decision {
        val eval = evaluator ?: throw WorkflowDefinitionException(listOf("decision '$id' has no evaluate { }"))
        return Step.Decision(id, label, outcomeNames, routes, pathName, { DecisionScope(it).eval().name }, descriptions.toMap())
    }
}
