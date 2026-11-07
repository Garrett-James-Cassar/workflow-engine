package dev.workflow.app.security

/**
 * How a route's scope is derived. NONE: the whole app (the caller must hold the permission there, or
 * globally). TASK: the scope of the task named by the path's `id`. LIST: the caller must hold the
 * permission in some scope, and the listing returns only the items inside the scopes they hold.
 */
enum class ScopeFrom { NONE, TASK, LIST }

/**
 * Declares the permission a route needs and its [label]: a short, verb-first, sentence-case name in the
 * words of UI-SPEC.md ("Start run"). Every log line written while the route runs carries the label
 * (OBSERVABILITY.md, endpoint labels) (deny by default: a route without one fails
 * `RouteAuthorizationTest`). The service layer makes the check before doing work. [heavy] puts the route in the
 * expensive rate-limit class (runs the engine, calls out or resets data); reads and writes are classed by method.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Requires(val permission: String, val scope: ScopeFrom = ScopeFrom.NONE, val label: String, val heavy: Boolean = false)

/** Any signed-in person, with no permission needed (the caller's own `/me`). [action] stands where a permission name would. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Authenticated(val label: String, val action: String)

/** Open to everyone: health, API docs and the demo providers. Unlabelled: logs name the route, such as `GET /health`. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Public
