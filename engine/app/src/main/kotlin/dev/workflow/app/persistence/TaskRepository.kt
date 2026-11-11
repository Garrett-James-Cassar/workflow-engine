package dev.workflow.app.persistence

import dev.workflow.app.domain.TaskDecision
import dev.workflow.app.domain.TaskRow
import dev.workflow.app.ports.TaskOps
import dev.workflow.core.Assignment
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

private const val INSERT_TASK = """insert into user_tasks (id, instance_id, step, assignee_type, assignee, scope, status)
    values (:id, :instance, :step, :type, :assignee, cast(:scope as jsonb), 'OPEN')"""
private const val REASSIGN_TASK = """update user_tasks set assignee_type = :type, assignee = :assignee, scope = cast(:scope as jsonb)
    where id = :id and status = 'OPEN'"""
private const val CLOSE_TASK = """update user_tasks set status = 'DONE', outcome = :outcome, form = cast(:form as jsonb),
    decided_by = :by, decided_at = :at where id = :id"""
private const val OPEN_TASKS = "select * from user_tasks where status = 'OPEN'"
private const val OPEN_ORDER = " order by created_at, id limit :limit offset :offset"

/** User tasks, in plain SQL. Callers own the transaction. */
@Repository
class TaskRepository(private val jdbc: JdbcClient, private val json: JsonMapper) : TaskOps {
    override fun insertTask(instanceId: UUID, step: String, assignment: Assignment): UUID =
        UUID.randomUUID().also { write(INSERT_TASK, assignmentParams(it, assignment) + mapOf("instance" to instanceId, "step" to step)) }

    override fun task(id: UUID): TaskRow? = jdbc.sql("select * from user_tasks where id = :id").param("id", id).query { rs, _ -> task(rs) }.optional().orElse(null)

    override fun openTasks(instanceId: UUID?, limit: Int, offset: Int): List<TaskRow> =
        jdbc.sql(OPEN_TASKS + (if (instanceId == null) "" else " and instance_id = :id") + OPEN_ORDER)
            .params(listOfNotNull(instanceId?.let { "id" to it }).toMap() + mapOf("limit" to limit, "offset" to offset))
            .query { rs, _ -> task(rs) }.list()

    override fun openTasksOf(instanceIds: Collection<UUID>): List<TaskRow> =
        if (instanceIds.isEmpty()) emptyList() else jdbc.sql("$OPEN_TASKS and instance_id in (:ids) order by created_at, id").param("ids", instanceIds).query { rs, _ -> task(rs) }.list()

    override fun reassignTask(id: UUID, assignment: Assignment) = write(REASSIGN_TASK, assignmentParams(id, assignment))

    override fun closeTask(id: UUID, decision: TaskDecision) = write(CLOSE_TASK, decisionParams(id, decision))

    override fun cancelOpenTasks(instanceId: UUID) =
        write("update user_tasks set status = 'CANCELLED' where instance_id = :id and status = 'OPEN'", mapOf("id" to instanceId))

    private fun write(sql: String, params: Map<String, Any?>) {
        jdbc.sql(sql).params(params).update()
    }

    private fun query(sql: String, id: UUID? = null): List<TaskRow> =
        jdbc.sql(sql).params(listOfNotNull(id?.let { "id" to it }).toMap()).query { rs, _ -> task(rs) }.list()

    private fun assignmentParams(id: UUID, a: Assignment) =
        mapOf("id" to id, "type" to a.type.name, "assignee" to a.name, "scope" to json.writeValueAsString(a.scope))

    private fun decisionParams(id: UUID, d: TaskDecision) = mapOf(
        "id" to id, "outcome" to d.outcome, "form" to json.writeValueAsString(d.form), "by" to d.by, "at" to Timestamp.from(d.at),
    )

    private fun task(rs: ResultSet) = TaskRow(
        rs.getObject("id", UUID::class.java), rs.getObject("instance_id", UUID::class.java), rs.getString("step"), assignment(rs),
        rs.getString("status"), rs.getString("outcome"), rs.getString("form")?.let { json.readValue(it) }, rs.getString("decided_by"),
        rs.getTimestamp("decided_at")?.toInstant(), rs.getTimestamp("created_at").toInstant(),
    )

    private fun assignment(rs: ResultSet) =
        Assignment(Assignment.Type.valueOf(rs.getString("assignee_type")), rs.getString("assignee"), json.readValue(rs.getString("scope")))
}
