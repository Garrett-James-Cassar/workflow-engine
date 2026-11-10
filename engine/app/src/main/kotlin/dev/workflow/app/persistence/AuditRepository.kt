package dev.workflow.app.persistence

import dev.workflow.app.domain.AuditEntry
import dev.workflow.app.domain.AuditOutcome
import dev.workflow.app.domain.AuditQuery
import dev.workflow.app.ports.AuditStore
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.sql.ResultSet
import java.sql.Timestamp

private const val INSERT = "insert into audit_log (at, actor, via, action, resource, outcome, changes, trace_id) " +
    "values (:at, :actor, :via, :action, :resource, :outcome, :changes, :trace)"

private const val SELECT = "select id, at, actor, via, action, resource, outcome, changes, trace_id from audit_log " +
    "where (:actor is null or actor = :actor) and (:action is null or action = :action) and (:resource is null or resource = :resource) " +
    "and (cast(:from as timestamp with time zone) is null or at >= :from) and (cast(:to as timestamp with time zone) is null or at <= :to) " +
    "order by at desc, id desc limit :limit offset :offset"

/** The audit log in plain SQL. Append-only: it exposes insert and list and nothing that updates or deletes. */
@Repository
class AuditRepository(private val jdbc: JdbcClient, private val json: JsonMapper) : AuditStore {
    override fun insert(entry: AuditEntry) {
        jdbc.sql(INSERT).params(columns(entry)).update()
    }

    private fun columns(entry: AuditEntry) = mapOf(
        "at" to Timestamp.from(entry.at), "actor" to entry.actor, "via" to entry.via, "action" to entry.action,
        "resource" to entry.resource, "outcome" to entry.outcome.name.lowercase(), "changes" to json.writeValueAsString(entry.changes), "trace" to entry.traceId,
    )

    override fun list(query: AuditQuery): List<AuditEntry> = jdbc.sql(SELECT).params(
        mapOf(
            "actor" to query.actor, "action" to query.action, "resource" to query.resource,
            "from" to query.from?.let(Timestamp::from), "to" to query.to?.let(Timestamp::from), "limit" to query.limit, "offset" to query.offset,
        ),
    ).query { rs, _ -> row(rs) }.list()

    private fun row(rs: ResultSet) = AuditEntry(
        rs.getLong("id"), rs.getTimestamp("at").toInstant(), rs.getString("actor"), rs.getString("via"), rs.getString("action"),
        rs.getString("resource"), AuditOutcome.valueOf(rs.getString("outcome").uppercase()), json.readValue(rs.getString("changes")), rs.getString("trace_id"),
    )
}
