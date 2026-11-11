package dev.workflow.app.persistence

import dev.workflow.app.domain.EventRow
import dev.workflow.app.ports.InstanceOps
import dev.workflow.core.EngineEvent
import dev.workflow.core.InstanceState
import dev.workflow.core.InstanceStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

private const val INSERT_INSTANCE = """insert into instances (id, definition, version, status, current_step, end_status, vars)
    values (:id, :definition, :version, :status, :step, :end, cast(:vars as jsonb))"""
private const val SAVE_INSTANCE = """update instances set status = :status, current_step = :step, end_status = :end,
    vars = cast(:vars as jsonb), updated_at = now() where id = :id"""
private const val INSERT_EVENT = """insert into events (instance_id, type, step, outcome, actor, payload, at)
    values (:instance, :type, :step, :outcome, :actor, cast(:payload as jsonb), :at)"""
private val TABLES = listOf("events", "waits", "user_tasks", "instances")

/** Runs and their events, in plain SQL. Callers own the transaction. */
@Repository
class InstanceRepository(private val jdbc: JdbcClient, private val json: JsonMapper, private val dataSource: DataSource) : InstanceOps {
    override fun insertInstance(state: InstanceState) = write(INSERT_INSTANCE, instanceParams(state))

    override fun saveInstance(state: InstanceState) = write(SAVE_INSTANCE, instanceParams(state))

    override fun instance(id: UUID): InstanceState? = one("select * from instances where id = :id", id)

    override fun lockInstance(id: UUID): InstanceState? = one("select * from instances where id = :id for update", id)

    override fun instances(limit: Int): List<InstanceState> =
        jdbc.sql("select * from instances order by created_at desc limit :limit").param("limit", limit).query { rs, _ -> instance(rs) }.list()

    override fun instancesByIds(ids: Collection<UUID>): List<InstanceState> =
        if (ids.isEmpty()) emptyList() else jdbc.sql("select * from instances where id in (:ids)").param("ids", ids).query { rs, _ -> instance(rs) }.list()

    override fun appendEvents(instanceId: UUID, events: List<EngineEvent>) = events.forEach { write(INSERT_EVENT, eventParams(instanceId, it)) }

    override fun events(instanceId: UUID): List<EventRow> =
        jdbc.sql("select * from events where instance_id = :id order by id").param("id", instanceId).query { rs, _ -> eventRow(rs) }.list()

    // Postgres: one TRUNCATE, which skips the append-only trigger. H2 (tests) cannot truncate a referenced table.
    override fun resetAll() = if (isH2()) deleteAll() else truncateAll()

    private fun deleteAll() = TABLES.forEach { write("delete from $it") }

    private fun truncateAll() = write("truncate table ${TABLES.joinToString()}")

    private fun isH2() = dataSource.connection.use { it.metaData.databaseProductName.equals("H2", ignoreCase = true) }

    private fun write(sql: String, params: Map<String, Any?> = emptyMap()) {
        jdbc.sql(sql).params(params).update()
    }

    private fun one(sql: String, id: UUID): InstanceState? = jdbc.sql(sql).param("id", id).query { rs, _ -> instance(rs) }.optional().orElse(null)

    private fun instanceParams(s: InstanceState) = mapOf(
        "id" to UUID.fromString(s.id), "definition" to s.definition, "version" to s.version, "status" to s.status.name,
        "step" to s.currentStep, "end" to s.endStatus, "vars" to json.writeValueAsString(s.vars),
    )

    private fun eventParams(instanceId: UUID, e: EngineEvent) = mapOf(
        "instance" to instanceId, "type" to e.type, "step" to e.step, "outcome" to e.outcome, "actor" to e.actor,
        "payload" to json.writeValueAsString(e.payload), "at" to Timestamp.from(e.at),
    )

    private fun instance(rs: ResultSet) = InstanceState(
        rs.getString("id"), rs.getString("definition"), rs.getInt("version"), InstanceStatus.valueOf(rs.getString("status")),
        rs.getString("current_step"), rs.getString("end_status"), json.readValue(rs.getString("vars")),
    )

    private fun eventRow(rs: ResultSet) = EventRow(rs.getLong("id"), event(rs))

    private fun event(rs: ResultSet) = EngineEvent(
        rs.getString("type"), rs.getString("step"), rs.getString("outcome"), rs.getString("actor"),
        json.readValue(rs.getString("payload")), rs.getTimestamp("at").toInstant(),
    )
}
