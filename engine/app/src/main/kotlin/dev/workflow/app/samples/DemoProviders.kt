package dev.workflow.app.samples

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fake third parties for the sample workflows, so they run with nothing else installed.
 * The samples reach them through the built-in `http { }` task (URLs come from the instance vars).
 */
@RestController
@RequestMapping("/demo")
class DemoProviders {

    /** Sanctions/KYC screening: accepts a case and replies later via `POST /events/screening-result`. */
    @dev.workflow.app.security.Public
    @PostMapping("/screening")
    fun screening(@RequestBody body: Map<String, Any?>) =
        mapOf("caseId" to "CASE-" + UUID.randomUUID().toString().take(8), "received" to body["name"])

    /** Clause library: publish a change and get the new version. */
    @dev.workflow.app.security.Public
    @PostMapping("/clause-store/publish")
    fun publish(@RequestBody body: Map<String, Any?>) =
        mapOf("clause" to body["clauseId"], "version" to publishedVersions.incrementAndGet())

    @dev.workflow.app.security.Public
    @GetMapping("/booking-system")
    fun bookingSystemState() = mapOf("up" to DemoBookingSystem.up.get())

    /** Switch the fake booking system off/on to drive incidents and retries. */
    @dev.workflow.app.security.Public
    @PostMapping("/booking-system/{state}")
    fun bookingSystem(@PathVariable state: String): Map<String, Any> {
        DemoBookingSystem.up.set(state == "up")
        return mapOf("up" to DemoBookingSystem.up.get())
    }

    private companion object {
        val publishedVersions = AtomicInteger(0)
    }
}

object DemoBookingSystem {
    val up = AtomicBoolean(true)
}
