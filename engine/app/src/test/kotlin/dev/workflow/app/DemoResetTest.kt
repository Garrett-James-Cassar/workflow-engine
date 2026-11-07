package dev.workflow.app

import dev.workflow.app.samples.DemoBookingSystem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `POST /demo/reset`: a fresh state. Needs the demo.reset permission; the admin persona holds it. */
class DemoResetTest : ApiTest() {
    @Test
    fun `reset clears every run and brings the booking system back up`() {
        start(doc())
        assertTrue(call("GET", "/api/v1/instances").list.isNotEmpty())
        DemoBookingSystem.up.set(false)
        assertEquals(200, call("POST", "/demo/reset", ADMIN).status)
        assertEquals(emptyList(), call("GET", "/api/v1/instances").list)
        assertEquals(true, DemoBookingSystem.up.get())
    }

    @Test
    fun `reset needs the demo reset permission`() {
        val res = call("POST", "/demo/reset", "a.ops")
        assertEquals(403, res.status)
        assertTrue((res.map["reason"] as String).contains("demo.reset"), "${res.map["reason"]}")
    }

    @Test
    fun `reset without identity is refused`() {
        assertEquals(401, call("POST", "/demo/reset", "").status)
    }

    @Test
    fun `status is open to the console in dev`() {
        assertEquals(200, call("GET", "/demo/status", "").status)
    }
}
