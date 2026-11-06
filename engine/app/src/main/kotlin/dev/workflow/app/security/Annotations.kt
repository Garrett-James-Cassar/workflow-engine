package dev.workflow.app.security

/** How a route's scope is derived. NONE: the whole app. TASK: the scope of the task it acts on. */
enum class ScopeFrom { NONE, TASK }

/**
 * Declares the permission a route needs (deny by default: a route without one fails
 * `RouteAuthorizationTest`). The service layer makes the check before doing work.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Requires(val permission: String, val scope: ScopeFrom = ScopeFrom.NONE)

/** Any signed-in person, with no permission needed (the caller's own `/me`). */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Authenticated

/** Open to everyone: health and API docs only. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Public
