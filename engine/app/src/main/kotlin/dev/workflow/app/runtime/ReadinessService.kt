package dev.workflow.app.runtime

import dev.workflow.app.ports.DatabaseProbe
import org.springframework.stereotype.Component

/** Readiness: the app's own infrastructure only (its database). Other fleet apps are never checked. */
@Component
class ReadinessService(private val database: DatabaseProbe) {
    /** Check name to whether it answers. */
    fun checks(): Map<String, Boolean> = mapOf("db" to database.up())
}
