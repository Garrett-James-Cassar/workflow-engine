package dev.workflow.app.persistence

import dev.workflow.app.domain.NewWait
import dev.workflow.app.domain.WaitRow
import dev.workflow.app.ports.WaitOps
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private const val INSERT_WAIT = """insert into waits (id, instance_id, step, event, correlation_key, correlation_value, expires_at, status)
    values (:id, :instance, :step, :event, :key, :value, :expires, 'OPEN')"""
private const val OPEN_WAITS = "select * from waits where status = 'OPEN'"

/** Event and timer waits, in plain SQL. Callers own the transaction. */
@Repository
class WaitRepository(private val jdbc: JdbcClient) : WaitOps {
    override fun insertWait(wait: NewWait) = write(INSERT_WAIT, waitParams(wait))

    override fun openWaits(event: String): List<WaitRow> = query("$OPEN_WAITS and event = :event order by created_at", "event" to event)

    override fun openWaitsFor(instanceId: UUID): List<WaitRow> = query("$OPEN_WAITS and instance_id = :id", "id" to instanceId)

    override fun openWaitsOf(instanceIds: Collection<UUID>): List<WaitRow> =
        if (instanceIds.isEmpty()) emptyList() else jdbc.sql("$OPEN_WAITS and instance_id in (:ids)").param("ids", instanceIds).query { rs, _ -> wait(rs) }.list()

    override fun expiredWaits(now: Instant): List<WaitRow> =
        query("$OPEN_WAITS and expires_at <= :now order by expires_at", "now" to Timestamp.from(now))

    override fun closeWait(id: UUID) = write("update waits set status = 'DONE' where id = :id", mapOf("id" to id))

    override fun cancelOpenWaits(instanceId: UUID) =
        write("update waits set status = 'CANCELLED' where instance_id = :id and status = 'OPEN'", mapOf("id" to instanceId))

    private fun write(sql: String, params: Map<String, Any?>) {
        jdbc.sql(sql).params(params).update()
    }

    private fun query(sql: String, param: Pair<String, Any>): List<WaitRow> = jdbc.sql(sql).param(param.first, param.second).query { rs, _ -> wait(rs) }.list()

    private fun waitParams(w: NewWait) = mapOf(
        "id" to UUID.randomUUID(), "instance" to w.instanceId, "step" to w.step, "event" to w.event,
        "key" to w.correlationKey, "value" to w.correlationValue, "expires" to w.expiresAt?.let(Timestamp::from),
    )

    private fun wait(rs: ResultSet) = WaitRow(
        rs.getObject("id", UUID::class.java), rs.getObject("instance_id", UUID::class.java), rs.getString("step"), rs.getString("event"),
        rs.getString("correlation_key"), rs.getString("correlation_value"), rs.getTimestamp("expires_at")?.toInstant(), rs.getString("status"),
    )
}
