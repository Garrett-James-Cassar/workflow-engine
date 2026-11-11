package dev.workflow.app

import dev.workflow.app.clients.HttpRbac
import dev.workflow.app.domain.RbacUnavailableException
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.net.ServerSocket
import java.time.Duration
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** NFR P5: a collaborator that accepts the connection and never answers costs the caller at most the timeout, then fails closed. */
class OutboundTimeoutTest {
    @Test
    fun `P5 a hanging rbac fails closed within the timeout`() {
        ServerSocket(0).use { hang ->
            val rbac = HttpRbac("http://127.0.0.1:${hang.localPort}", RestClient.builder(), timeout = Duration.ofMillis(500))
            val started = System.nanoTime()
            assertFailsWith<RbacUnavailableException> { rbac.check("a.ops", "instances.read", emptyMap()) }
            assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(5), "the call did not give up")
        }
    }

    @Test
    fun `P5 the default outbound timeout is at most 10 s`() {
        assertTrue(HttpRbac.TIMEOUT <= Duration.ofSeconds(10))
    }
}
