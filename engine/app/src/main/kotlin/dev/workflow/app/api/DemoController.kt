package dev.workflow.app.api

import dev.workflow.app.runtime.DemoGate
import dev.workflow.app.security.Permissions
import dev.workflow.app.security.Requires
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * `POST /demo/reset`: a fresh state for the next chapter. Clears every run, task, wait, event and mock and
 * brings the fake booking system back up. Dev and demo only (404 in live); needs `demo.reset`.
 */
@RestController
class DemoController(private val gate: DemoGate) {
    @Requires(Permissions.DEMO_RESET, label = "Reset demo data", heavy = true)
    @PostMapping("/demo/reset")
    fun reset(): Map<String, Any> {
        gate.reset()
        return mapOf("reset" to true)
    }
}
