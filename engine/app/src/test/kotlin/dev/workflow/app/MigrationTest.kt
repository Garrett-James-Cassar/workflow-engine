package dev.workflow.app

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * dm.2: the migrations on a real PostgreSQL. The rule is expand-contract, forward-fix only (RUNBOOK.md): every
 * migration must apply to an empty database, and the newest one must apply to a database at the version before it that
 * holds data, without losing that data. Runs in `postgresTest`.
 */
@Tag("postgres")
class MigrationTest {
    private fun fresh(): Pair<PostgreSQLContainer, DataSource> {
        val c = PostgreSQLContainer("postgres:17-alpine").also { it.start() }
        val ds = DriverManagerDataSource(c.jdbcUrl, c.username, c.password)
        return c to ds
    }

    private fun flyway(ds: DataSource, target: String = "latest") =
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target(target).load()

    @Test
    fun `DM2 every migration applies to an empty database`() {
        val (c, ds) = fresh()
        c.use {
            val flyway = flyway(ds)
            val result = flyway.migrate()
            assertEquals(flyway.info().all().size, result.migrationsExecuted)
            assertTrue(flyway.info().pending().isEmpty())
            assertEquals(0, flyway.validateWithResult().invalidMigrations?.size ?: 0)
        }
    }

    @Test
    fun `DM2 the latest migration applies to a database at the previous version with data`() {
        val (c, ds) = fresh()
        c.use {
            val all = flyway(ds).info().all()
            val previous = all[all.size - 2].version.version
            flyway(ds, previous).migrate()
            ds.connection.use { cn ->
                cn.createStatement().use {
                    it.execute("insert into instances (id, definition, version, status, vars) values (gen_random_uuid(), 'confirmation-approval', 1, 'WAITING', '{\"doc\":{\"id\":\"D-1\"}}'::jsonb)")
                }
            }
            val after = flyway(ds)
            assertEquals(1, after.info().pending().size, "exactly the latest migration is pending")
            after.migrate()
            ds.connection.use { cn ->
                cn.createStatement().use { st ->
                    st.executeQuery("select count(*) from instances").use { rs -> rs.next(); assertEquals(1, rs.getInt(1), "the data survives the upgrade") }
                }
            }
            assertTrue(after.info().pending().isEmpty())
        }
    }
}
