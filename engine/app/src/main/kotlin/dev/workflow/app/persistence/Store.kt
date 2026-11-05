package dev.workflow.app.persistence

import dev.workflow.core.Assignment
import dev.workflow.core.EngineEvent
import dev.workflow.core.InstanceState
import dev.workflow.core.InstanceStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class TaskRow(
    val id: UUID,
    val instanceId: UUID,
    val step: String,
    val assignment: Assignment,
    val status: String,
    val outcome: String?,
    val form: Map<String, Any?>?,
    val decidedBy: String?,
    val decidedAt: Instant?,
    val createdAt: Instant,
)

data class WaitRow(
    val id: UUID,
    val instanceId: UUID,
    val step: String,
    val event: String,
    val correlationKey: String?,
    val correlationValue: String?,
    val expiresAt: Instant?,
    val status: String,
)

data class EventRow(val id: Long, val event: EngineEvent)

/** All persistence, in plain SQL. Callers own the transaction. */
@Repository
class Store(private val jdbc: JdbcClient, private val json: JsonMapper) {

    fun insertInstance(state: InstanceState) {
        jdbc.sql(
            """insert into instances (id, definition, version, status, current_step, end_status, vars)
               values (:id, :definition, :version, :status, :step, :end, cast(:vars as jsonb))""",
        )
            .param("id", UUID.fromString(state.id))
            .param("definition", state.definition)
            .param("version", state.version)
            .param("status", state.status.name)
            .param("step", state.currentStep)
            .param("end", state.endStatus)
            .param("vars", json.writeValueAsString(state.vars))
            .update()
    }

    fun saveInstance(state: InstanceState) {
        jdbc.sql(
            """update instances set status = :status, current_step = :step, end_status = :end,
               vars = cast(:vars as jsonb), updated_at = now() where id = :id""",
        )
            .param("id", UUID.fromString(state.id))
            .param("status", state.status.name)
            .param("step", state.currentStep)
            .param("end", state.endStatus)
            .param("vars", json.writeValueAsString(state.vars))
            .update()
    }

    fun instance(id: UUID): InstanceState? =
        jdbc.sql("select * from instances where id = :id").param("id", id).query { rs, _ -> instance(rs) }.optional().orElse(null)

    /** Load and lock the instance row for the rest of the transaction, so steps advance one at a time. */
    fun lockInstance(id: UUID): InstanceState? =
        jdbc.sql("select * from instances where id = :id for update").param("id", id).query { rs, _ -> instance(rs) }.optional().orElse(null)

    fun instances(limit: Int): List<InstanceState> =
        jdbc.sql("select * from instances order by created_at desc limit :limit").param("limit", limit).query { rs, _ -> instance(rs) }.list()

    fun appendEvents(instanceId: UUID, events: List<EngineEvent>) {
        events.forEach { e ->
            jdbc.sql(
                """insert into events (instance_id, type, step, outcome, actor, payload, at)
                   values (:instance, :type, :step, :outcome, :actor, cast(:payload as jsonb), :at)""",
            )
                .param("instance", instanceId)
                .param("type", e.type)
                .param("step", e.step)
                .param("outcome", e.outcome)
                .param("actor", e.actor)
                .param("payload", json.writeValueAsString(e.payload))
                .param("at", Timestamp.from(e.at))
                .update()
        }
    }

    fun events(instanceId: UUID): List<EventRow> =
        jdbc.sql("select * from events where instance_id = :id order by id").param("id", instanceId).query { rs, _ ->
            EventRow(
                rs.getLong("id"),
                EngineEvent(
                    rs.getString("type"), rs.getString("step"), rs.getString("outcome"), rs.getString("actor"),
                    json.readValue(rs.getString("payload")), rs.getTimestamp("at").toInstant(),
                ),
            )
        }.list()

    fun insertTask(instanceId: UUID, step: String, assignment: Assignment): UUID {
        val id = UUID.randomUUID()
        jdbc.sql(
            """insert into user_tasks (id, instance_id, step, assignee_type, assignee, scope, status)
               values (:id, :instance, :step, :type, :assignee, cast(:scope as jsonb), 'OPEN')""",
        )
            .param("id", id)
            .param("instance", instanceId)
            .param("step", step)
            .param("type", assignment.type.name)
            .param("assignee", assignment.name)
            .param("scope", json.writeValueAsString(assignment.scope))
            .update()
        return id
    }

    fun task(id: UUID): TaskRow? =
        jdbc.sql("select * from user_tasks where id = :id").param("id", id).query { rs, _ -> task(rs) }.optional().orElse(null)

    fun openTasks(instanceId: UUID? = null): List<TaskRow> =
        if (instanceId == null) {
            jdbc.sql("select * from user_tasks where status = 'OPEN' order by created_at").query { rs, _ -> task(rs) }.list()
        } else {
            jdbc.sql("select * from user_tasks where status = 'OPEN' and instance_id = :id order by created_at")
                .param("id", instanceId).query { rs, _ -> task(rs) }.list()
        }

    /** Move an open task to a new user or team. The history of who held it lives in `events`. */
    fun reassignTask(id: UUID, assignment: Assignment) {
        jdbc.sql(
            """update user_tasks set assignee_type = :type, assignee = :assignee, scope = cast(:scope as jsonb)
               where id = :id and status = 'OPEN'""",
        )
            .param("id", id)
            .param("type", assignment.type.name)
            .param("assignee", assignment.name)
            .param("scope", json.writeValueAsString(assignment.scope))
            .update()
    }

    fun closeTask(id: UUID, outcome: String, form: Map<String, Any?>, by: String, at: Instant) {
        jdbc.sql(
            """update user_tasks set status = 'DONE', outcome = :outcome, form = cast(:form as jsonb),
               decided_by = :by, decided_at = :at where id = :id""",
        )
            .param("id", id)
            .param("outcome", outcome)
            .param("form", json.writeValueAsString(form))
            .param("by", by)
            .param("at", Timestamp.from(at))
            .update()
    }

    fun insertWait(instanceId: UUID, step: String, event: String, key: String?, value: String?, expiresAt: Instant?) {
        jdbc.sql(
            """insert into waits (id, instance_id, step, event, correlation_key, correlation_value, expires_at, status)
               values (:id, :instance, :step, :event, :key, :value, :expires, 'OPEN')""",
        )
            .param("id", UUID.randomUUID())
            .param("instance", instanceId)
            .param("step", step)
            .param("event", event)
            .param("key", key)
            .param("value", value)
            .param("expires", expiresAt?.let(Timestamp::from))
            .update()
    }

    fun openWaits(event: String): List<WaitRow> =
        jdbc.sql("select * from waits where status = 'OPEN' and event = :event order by created_at")
            .param("event", event).query { rs, _ -> wait(rs) }.list()

    fun openWaitsFor(instanceId: UUID): List<WaitRow> =
        jdbc.sql("select * from waits where status = 'OPEN' and instance_id = :id")
            .param("id", instanceId).query { rs, _ -> wait(rs) }.list()

    fun expiredWaits(now: Instant): List<WaitRow> =
        jdbc.sql("select * from waits where status = 'OPEN' and expires_at <= :now order by expires_at")
            .param("now", Timestamp.from(now)).query { rs, _ -> wait(rs) }.list()

    /** Cancel every open user task and wait of an instance. The rows stay as history. */
    fun cancelOpenWork(instanceId: UUID) {
        jdbc.sql("update user_tasks set status = 'CANCELLED' where instance_id = :id and status = 'OPEN'").param("id", instanceId).update()
        jdbc.sql("update waits set status = 'CANCELLED' where instance_id = :id and status = 'OPEN'").param("id", instanceId).update()
    }

    fun closeWait(id: UUID) {
        jdbc.sql("update waits set status = 'DONE' where id = :id").param("id", id).update()
    }

    private fun instance(rs: ResultSet) = InstanceState(
        id = rs.getString("id"),
        definition = rs.getString("definition"),
        version = rs.getInt("version"),
        status = InstanceStatus.valueOf(rs.getString("status")),
        currentStep = rs.getString("current_step"),
        endStatus = rs.getString("end_status"),
        vars = json.readValue(rs.getString("vars")),
    )

    private fun task(rs: ResultSet) = TaskRow(
        id = rs.getObject("id", UUID::class.java),
        instanceId = rs.getObject("instance_id", UUID::class.java),
        step = rs.getString("step"),
        assignment = Assignment(
            Assignment.Type.valueOf(rs.getString("assignee_type")),
            rs.getString("assignee"),
            json.readValue(rs.getString("scope")),
        ),
        status = rs.getString("status"),
        outcome = rs.getString("outcome"),
        form = rs.getString("form")?.let { json.readValue(it) },
        decidedBy = rs.getString("decided_by"),
        decidedAt = rs.getTimestamp("decided_at")?.toInstant(),
        createdAt = rs.getTimestamp("created_at").toInstant(),
    )

    private fun wait(rs: ResultSet) = WaitRow(
        id = rs.getObject("id", UUID::class.java),
        instanceId = rs.getObject("instance_id", UUID::class.java),
        step = rs.getString("step"),
        event = rs.getString("event"),
        correlationKey = rs.getString("correlation_key"),
        correlationValue = rs.getString("correlation_value"),
        expiresAt = rs.getTimestamp("expires_at")?.toInstant(),
        status = rs.getString("status"),
    )
}
