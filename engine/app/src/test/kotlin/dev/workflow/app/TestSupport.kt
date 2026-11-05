package dev.workflow.app

import dev.workflow.app.registry.WorkflowCatalog
import dev.workflow.core.Done
import dev.workflow.core.ServiceContext
import dev.workflow.core.ServiceHandler
import dev.workflow.core.ServiceResult
import dev.workflow.core.Workflow
import dev.workflow.core.http
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class MutableClock(var now: Instant = Instant.parse("2026-11-04T09:00:00Z")) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    fun advanceDays(days: Long) {
        now = now.plusSeconds(days * 24 * 3600)
    }
}

/** Fails while [failing] is true, so tests can drive an incident and then a successful retry. */
class FlakyHandler : ServiceHandler<Done> {
    override fun execute(ctx: ServiceContext): ServiceResult<Done> {
        check(!failing) { "downstream unavailable" }
        return ServiceResult.done("ran" to true)
    }

    companion object {
        @Volatile var failing = true
    }
}

object HttpCallout : Workflow("http-callout", 1, {
    serviceTask("call") {
        http {
            post("{target.url}")
            bodyFrom("payload")
            header("X-Doc", "{payload.id}")
        }
        retries = 0
    }
})

object Flaky : Workflow("flaky", 1, {
    serviceTask("flaky") {
        handler<FlakyHandler>()
        retries = 1
    }
})

@TestConfiguration(proxyBeanMethods = false)
class TestSupport {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:17-alpine")

    @Bean
    @Primary
    fun testClock(): MutableClock = MutableClock()

    @Bean
    fun testCatalog(): WorkflowCatalog = object : WorkflowCatalog {
        override val workflows = listOf(HttpCallout, Flaky)
    }
}
