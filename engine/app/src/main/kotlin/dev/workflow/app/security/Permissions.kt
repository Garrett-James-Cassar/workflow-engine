package dev.workflow.app.security

/**
 * Every permission this app has, named `<resource>.<verb>` (fleet RBAC.md section 2). The apps/web
 * `permissions.ts` list and the rbac persona file (`resources/rbac/workflow-engine.json`) use the same
 * names; `PermissionsSyncTest` fails if they drift.
 */
object Permissions {
    const val WORKFLOWS_READ = "workflows.read"
    const val WORKFLOWS_EXPORT = "workflows.export"
    const val INSTANCES_READ = "instances.read"
    const val INSTANCES_START = "instances.start"
    const val INSTANCES_RETRY = "instances.retry"
    const val INSTANCES_RESTART = "instances.restart"
    const val INSTANCES_FIRE_TIMEOUT = "instances.fire-timeout"
    const val INSTANCES_EXPORT = "instances.export"
    const val TASKS_READ = "tasks.read"
    const val TASKS_COMPLETE = "tasks.complete"
    const val TASKS_REASSIGN = "tasks.reassign"
    const val EVENTS_SEND = "events.send"
    const val INCIDENTS_READ = "incidents.read"
    const val INCIDENTS_RETRY = "incidents.retry"
    const val INCIDENTS_EXPORT = "incidents.export"
    const val PEOPLE_READ = "people.read"
    const val DEMO_RESET = "demo.reset"

    val ALL: List<String> = listOf(
        WORKFLOWS_READ, WORKFLOWS_EXPORT,
        INSTANCES_READ, INSTANCES_START, INSTANCES_RETRY, INSTANCES_RESTART, INSTANCES_FIRE_TIMEOUT, INSTANCES_EXPORT,
        TASKS_READ, TASKS_COMPLETE, TASKS_REASSIGN,
        EVENTS_SEND,
        INCIDENTS_READ, INCIDENTS_RETRY, INCIDENTS_EXPORT,
        PEOPLE_READ, DEMO_RESET,
    )
}
