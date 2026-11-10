package dev.workflow.app.persistence

import dev.workflow.app.ports.DatabaseProbe
import org.springframework.stereotype.Component
import java.sql.Statement
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/** `SELECT 1` on the app's own DataSource, answered within [TIMEOUT_SECONDS] (a stopped database also fails to hand out a connection in time). */
@Component
class JdbcDatabaseProbe(private val dataSource: DataSource) : DatabaseProbe {
    override fun up(): Boolean =
        CompletableFuture.supplyAsync(::selectOne)
            .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .exceptionally { false }
            .join()

    private fun selectOne(): Boolean = runCatching { query() }.getOrDefault(false)

    private fun query(): Boolean = dataSource.connection.use { it.createStatement().use(::ask) }

    private fun ask(statement: Statement): Boolean {
        statement.queryTimeout = TIMEOUT_SECONDS.toInt()
        return statement.executeQuery("SELECT 1").use { it.next() }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 1L
    }
}
