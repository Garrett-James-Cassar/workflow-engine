package dev.workflow.core

import kotlin.reflect.KClass
import kotlin.time.Duration

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
