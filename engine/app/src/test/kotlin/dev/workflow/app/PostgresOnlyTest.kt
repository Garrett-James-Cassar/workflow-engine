package dev.workflow.app

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.boot.test.context.TestConfiguration
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.assertFailsWith

/**
 * t.unit.c: checks that need the real PostgreSQL engine (its PL/pgSQL trigger, which H2 cannot
 * run). Testcontainers, so they run only in the end-of-task full gate (`postgresTest`), never in
 * the fast `test` task.
 */
@Tag("postgres")
@SpringBootTest
@Import(PostgresOnlySupport::class)
class PostgresOnlyTest {
    @Autowired lateinit var jdbc: JdbcClient

    @Test
    fun `E1 the event log is append-only, enforced by the database`() {
        jdbc.sql("insert into instances (id, definition, version, status, vars) values (cast('00000000-0000-4000-8000-000000000001' as uuid), 'x', 1, 'running', cast('{}' as jsonb))").update()
        jdbc.sql("insert into events (instance_id, type, payload, at) values (cast('00000000-0000-4000-8000-000000000001' as uuid), 'instance-started', cast('{}' as jsonb), now())").update()
        assertFailsWith<Exception> {
            jdbc.sql("update events set type = 'x' where instance_id = cast('00000000-0000-4000-8000-000000000001' as uuid)").update()
        }
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PostgresOnlySupport {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:17-alpine")
}
