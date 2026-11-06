package dev.workflow.app

import dev.workflow.app.persistence.Store
import dev.workflow.core.Assignment
import dev.workflow.core.InstanceState
import dev.workflow.core.InstanceStatus
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * t.unit.c: the custom repository (Store) against the real PostgreSQL, with its jsonb columns,
 * scoped assignments and timed waits. Testcontainers, so it runs only in `postgresTest`.
 */
@Tag("postgres")
@SpringBootTest
@Import(PostgresOnlySupport::class)
class StorePostgresTest {
    @Autowired lateinit var store: Store

    private fun newInstance(vars: Map<String, Any?>): InstanceState = InstanceState(
        id = UUID.randomUUID().toString(), definition = "confirmation-approval", version = 1,
        status = InstanceStatus.WAITING, currentStep = "legal-review", endStatus = null, vars = vars,
    ).also { store.insertInstance(it) }

    @Test
    fun `an instance's nested vars round-trip through jsonb`() {
        val vars = mapOf("doc" to mapOf("id" to "D-1", "notional" to 75_000_000, "providerRejects" to false))
        val saved = newInstance(vars)
        val back = assertNotNull(store.instance(UUID.fromString(saved.id)))
        assertEquals(saved.status, back.status)
        assertEquals("D-1", (back.vars["doc"] as Map<*, *>)["id"])
        assertEquals(75_000_000, ((back.vars["doc"] as Map<*, *>)["notional"] as Number).toLong())
    }

    @Test
    fun `a role assignment keeps its scope through the row`() {
        val run = newInstance(emptyMap())
        val id = store.insertTask(UUID.fromString(run.id), "compliance-review", Assignment(Assignment.Type.ROLE, "compliance", mapOf("region" to "EU")))
        val task = assertNotNull(store.task(id))
        assertEquals(Assignment.Type.ROLE, task.assignment.type)
        assertEquals(mapOf("region" to "EU"), task.assignment.scope)
    }

    @Test
    fun `events come back in the order they were written`() {
        val run = newInstance(emptyMap())
        val at = Instant.parse("2026-11-06T09:00:00Z")
        store.appendEvents(
            UUID.fromString(run.id),
            listOf(
                dev.workflow.core.EngineEvent("instance-started", null, at = at),
                dev.workflow.core.EngineEvent("step-entered", "legal-review", at = at),
            ),
        )
        assertEquals(listOf("instance-started", "step-entered"), store.events(UUID.fromString(run.id)).map { it.event.type })
    }

    @Test
    fun `only waits past their expiry are returned as expired`() {
        val run = newInstance(emptyMap())
        val uuid = UUID.fromString(run.id)
        store.insertWait(uuid, "confirmation-matched", "confirmation-matched", "tradeRef", "T-1", Instant.parse("2026-11-06T09:00:00Z"))
        store.insertWait(uuid, "signed", "signed", "envelopeId", "ENV-1", Instant.parse("2026-11-20T09:00:00Z"))
        val expired = store.expiredWaits(Instant.parse("2026-11-07T00:00:00Z"))
        assertEquals(listOf("confirmation-matched"), expired.filter { it.instanceId == uuid }.map { it.step })
        assertNull(store.instance(UUID.randomUUID()))
    }
}
