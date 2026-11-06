package dev.workflow.app.api

import dev.workflow.app.persistence.Store
import dev.workflow.app.runtime.DemoGate
import dev.workflow.app.samples.DemoBookingSystem
import dev.workflow.app.security.Permissions
import dev.workflow.app.security.Public
import dev.workflow.app.security.Requires
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * `POST /demo/reset`: a fresh state for the next chapter. Clears every run, task, wait, event and mock and
 * brings the fake booking system back up. Dev and demo only (404 in live); needs `demo.reset`.
 */
@RestController
class DemoController(private val store: Store, private val dataSource: javax.sql.DataSource, private val gate: DemoGate) {
    /** The console asks this before it loads the Demo player: 200 in dev and demo, 404 in live. */
    @Public
    @GetMapping("/demo/status")
    fun status(): Map<String, Any> {
        gate.check()
        return mapOf("enabled" to true)
    }

    @Requires(Permissions.DEMO_RESET)
    @PostMapping("/demo/reset")
    fun reset(): Map<String, Any> {
        gate.check()
        store.resetAll(dataSource)
        DemoBookingSystem.up.set(true)
        return mapOf("reset" to true)
    }
}
