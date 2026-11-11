package dev.workflow.app

import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Rate limiting (SECURITY.md sec.3) with every class set to 2 a minute. */
@TestPropertySource(
    properties = [
        "workflow.rate-limit.enabled=true",
        "workflow.rate-limit.read-per-min=2",
        "workflow.rate-limit.write-per-min=2",
        "workflow.rate-limit.heavy-per-min=2",
        "spring.datasource.url=jdbc:h2:mem:workflow-ratelimit;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    ],
)
class RateLimitTest : ApiTest() {
    @Autowired lateinit var meters: MeterRegistry

    private val reads = "/api/v1/definitions"

    @Test
    fun `R1 the third read in a minute answers 429 with Retry-After and the rate limit headers`() {
        val first = raw("GET", reads, ADMIN)
        assertEquals(200, first.status)
        assertEquals("2", first.headers["ratelimit-limit"])
        assertEquals("1", first.headers["ratelimit-remaining"])
        assertNotNull(first.headers["ratelimit-reset"])
        assertEquals(200, raw("GET", reads, ADMIN).status)

        val third = raw("GET", reads, ADMIN)
        assertEquals(429, third.status, third.text)
        assertTrue(third.headers["content-type"]!!.startsWith("application/problem+json"))
        assertTrue((third.headers["retry-after"] ?: "0").toLong() >= 1, "Retry-After: ${third.headers["retry-after"]}")
        assertEquals("0", third.headers["ratelimit-remaining"])
        assertEquals("https://fleet.local/problems/rate-limited", third.map["type"])
        assertEquals(429, third.map["status"])
    }

    @Test
    fun `R2 two users have separate buckets`() {
        repeat(2) { assertEquals(200, raw("GET", reads, "a.ops").status) }
        assertEquals(429, raw("GET", reads, "a.ops").status)
        assertEquals(200, raw("GET", reads, "r.viewer").status, "bob has his own bucket")
    }

    @Test
    fun `R3 health, docs and the openapi document are never limited`() {
        repeat(10) {
            assertEquals(200, raw("GET", "/health", null).status)
            assertEquals(200, raw("GET", "/openapi.json", null).status)
        }
    }

    @Test
    fun `R4 writes and heavy routes have their own classes`() {
        val retry = "/api/v1/instances/00000000-0000-0000-0000-000000000000/retry"
        repeat(2) { assertTrue(raw("POST", retry, "ops.admin").status != 429) }
        val limited = raw("POST", retry, "ops.admin")
        assertEquals(429, limited.status, "retry is a heavy route: ${limited.text}")
        assertTrue(raw("GET", reads, "ops.admin").status != 429, "reads are a separate class")
    }

    @Test
    fun `R5 a caller with no identity is keyed by client IP, and a rejection is counted`() {
        val before = meters.find("fleet.rate_limit.rejected").counters().sumOf { it.count() }
        repeat(2) { assertEquals(200, raw("GET", "/demo/booking-system", null).status) }
        assertEquals(429, raw("GET", "/demo/booking-system", null).status)
        val after = meters.find("fleet.rate_limit.rejected").counters().sumOf { it.count() }
        assertTrue(after > before, "fleet.rate_limit.rejected went from $before to $after")
    }
}
