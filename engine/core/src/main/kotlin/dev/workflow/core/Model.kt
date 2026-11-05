package dev.workflow.core

import kotlin.reflect.KClass
import kotlin.time.Duration

/** Built-in outcome for steps with nothing to choose. Such steps may omit `on`; they continue with `next()`. */
enum class Done { OK }

/** Built-in outcome of an event wait without a timeout. */
enum class Received { RECEIVED }

/** Built-in outcome of an event wait with a timeout. The timeout is just another outcome to route. */
enum class ReceivedOrTimedOut { RECEIVED, TIMED_OUT }

/** Where an outcome sends the instance. Built only through [RouteScope]. */
sealed interface Route {
    /** The next step in the current sequence; at the end of a path, rejoin after the step that branched. */
    data object Next : Route

    data class Goto(val step: String) : Route

    data class End(val status: String) : Route

    /** An inline branch of steps. When it runs off its end it rejoins after the step that branched. */
    data class Path(val name: String, val steps: List<Step>) : Route
}

sealed interface Assignee {
    /** Any user holding [name], in the scope built from [scope] plus values read from vars by [scopeFrom]. */
    data class Role(val name: String, val scope: Map<String, String>, val scopeFrom: Map<String, String>) : Assignee

    data class User(val name: String) : Assignee

    /** The username is read from vars at the time the task is created. */
    data class UserFrom(val path: String) : Assignee
}

enum class TaskScope { USER, TEAM }

/** The default reassignment permission: anyone holding the `ops` role. */
val DEFAULT_REASSIGNER: Assignee.Role = Assignee.Role("ops", emptyMap(), emptyMap())

enum class FieldType { TEXT, NUMBER, BOOLEAN }

data class FormField(val name: String, val type: FieldType, val required: Boolean)

enum class HttpMethod { GET, POST, PUT, PATCH, DELETE }

/**
 * A built-in third-party call. `{a.b}` placeholders in [url] are filled from vars; the body is the
 * value at [bodyFrom] in vars, serialised as JSON. Non-2xx after retries raises an incident.
 */
data class HttpSpec(
    val method: HttpMethod,
    val url: String,
    val bodyFrom: String?,
    val headers: Map<String, String>,
)

/** Every step produces one of its [outcomes]; [routes] says where each one goes. */
sealed class Step {
    abstract val id: String
    abstract val label: String
    abstract val outcomes: List<String>
    abstract val routes: Map<String, Route>

    /** Name of the innermost path this step is declared in; null on the main flow. */
    abstract val path: String?

    data class UserTask(
        override val id: String,
        override val label: String,
        override val outcomes: List<String>,
        override val routes: Map<String, Route>,
        override val path: String?,
        val assignee: Assignee,
        val form: List<FormField>,
        /** Who may reassign this open task to a user or a team. Null: nobody may. */
        val reassignableBy: Assignee.Role?,
    ) : Step() {
        /** A task is user-scoped when one named person (or the person in vars) holds it; team-scoped when a role does. */
        val scope: TaskScope get() = if (assignee is Assignee.Role) TaskScope.TEAM else TaskScope.USER
    }

    data class ServiceTask(
        override val id: String,
        override val label: String,
        override val outcomes: List<String>,
        override val routes: Map<String, Route>,
        override val path: String?,
        val handler: KClass<out ServiceHandler<*>>?,
        val http: HttpSpec?,
        val retries: Int,
    ) : Step()

    data class EventWait(
        override val id: String,
        override val label: String,
        override val outcomes: List<String>,
        override val routes: Map<String, Route>,
        override val path: String?,
        val event: String,
        /** Event correlation key name to the vars path holding the value to match. */
        val correlation: Pair<String, String>?,
        val timeout: Duration?,
    ) : Step()

    class Decision(
        override val id: String,
        override val label: String,
        override val outcomes: List<String>,
        override val routes: Map<String, Route>,
        override val path: String?,
        val evaluate: (Vars) -> String,
        /** Optional readable edge labels, e.g. LARGE to "notional > 50m". */
        val descriptions: Map<String, String>,
    ) : Step()
}

/** Code linked to a service task. The outcome type must match the task's outcome type. */
interface ServiceHandler<O : Enum<O>> {
    fun execute(ctx: ServiceContext): ServiceResult<O>
}

data class ServiceContext(val instanceId: String, val stepId: String, val vars: Vars)

data class ServiceResult<O : Enum<O>>(val outcome: O, val output: Map<String, Any?> = emptyMap()) {
    companion object {
        fun done(vararg output: Pair<String, Any?>): ServiceResult<Done> = ServiceResult(Done.OK, mapOf(*output))
    }
}
