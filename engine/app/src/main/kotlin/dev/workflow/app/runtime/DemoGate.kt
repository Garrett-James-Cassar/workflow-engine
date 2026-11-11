package dev.workflow.app.runtime

import dev.workflow.app.domain.NotFoundException
import dev.workflow.app.ports.WorkflowStore
import dev.workflow.app.samples.DemoBookingSystem
import dev.workflow.app.security.AuthMode
import dev.workflow.app.security.AuthProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** The demo reset (`POST /demo/reset`, used by `make reseed` and the audit tests): on in dev and demo (`DEMO_ENABLED`, default on), 404 in live. */
@Component
class DemoGate(
    @Value("\${workflow.demo.enabled:true}") private val enabled: Boolean,
    private val auth: AuthProperties,
    private val store: WorkflowStore,
    private val resetGuard: ResetGuard,
    private val audit: Audit,
) {
    fun check() {
        if (!enabled || auth.authMode != AuthMode.DEV) throw NotFoundException("the demo is not available in this deployment")
    }

    /** A fresh state for the next chapter: clears every run, task, wait and event, and brings the fake booking system back up. */
    fun reset() {
        check()
        resetGuard.check()
        audit.run(audit.context(), "demo.reset", "demo", listOf("instances", "tasks", "waits", "events")) {
            store.resetAll()
            DemoBookingSystem.up.set(true)
        }
    }
}
