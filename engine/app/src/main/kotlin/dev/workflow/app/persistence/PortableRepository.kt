package dev.workflow.app.persistence

import dev.workflow.app.ports.PortableStore
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

private const val UPDATE = "update config_docs set doc = :doc, updated_at = :at where doc_kind = :kind and doc_key = :key"
private const val INSERT = "insert into config_docs (doc_kind, doc_key, doc, updated_at) values (:kind, :key, :doc, :at)"

/** Imported config documents, in plain SQL. Callers own the transaction. */
@Repository
class PortableRepository(private val jdbc: JdbcClient) : PortableStore {
    override fun docs(kind: String): Map<String, String> =
        jdbc.sql("select doc_key, doc from config_docs where doc_kind = :kind order by doc_key").param("kind", kind)
            .query { rs, _ -> rs.getString("doc_key") to rs.getString("doc") }.list().toMap()

    override fun put(kind: String, key: String, json: String, at: Instant) {
        val params = mapOf("kind" to kind, "key" to key, "doc" to json, "at" to Timestamp.from(at))
        if (jdbc.sql(UPDATE).params(params).update() == 0) jdbc.sql(INSERT).params(params).update()
    }
}
