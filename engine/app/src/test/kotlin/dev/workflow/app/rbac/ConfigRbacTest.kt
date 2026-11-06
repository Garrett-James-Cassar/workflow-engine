package dev.workflow.app.rbac

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** t.unit.a/b: the RBAC service with its real collaborator (the grants), no Spring context. */
class ConfigRbacTest {
    private val rbac = ConfigRbac(
        RbacProperties(
            grants = listOf(
                RbacProperties.Grant("l.jones", "legal"),
                RbacProperties.Grant("c.acme", "counterparty-signatory", mapOf("counterparty" to "ACME")),
                RbacProperties.Grant("k.comply", "compliance", mapOf("region" to "EU")),
            ),
        ),
    )

    @Test
    fun `a person without the role is refused with the reason`() {
        val d = rbac.check("a.ops", "legal", emptyMap())
        assertFalse(d.allowed)
        assertEquals("'a.ops' does not hold role 'legal'", d.reason)
    }

    @Test
    fun `an unscoped grant allows the role for any scope`() {
        assertTrue(rbac.check("l.jones", "legal", emptyMap()).allowed)
        assertTrue(rbac.check("l.jones", "legal", mapOf("region" to "EU")).allowed)
    }

    @Test
    fun `a scoped grant allows only the scope it was given`() {
        assertTrue(rbac.check("c.acme", "counterparty-signatory", mapOf("counterparty" to "ACME")).allowed)
        val other = rbac.check("c.acme", "counterparty-signatory", mapOf("counterparty" to "GLOBEX"))
        assertFalse(other.allowed)
        assertTrue(other.reason.contains("only in"), other.reason)
    }

    @Test
    fun `a granted scope is reported back when it allows`() {
        val d = rbac.check("k.comply", "compliance", mapOf("region" to "EU"))
        assertTrue(d.allowed)
        assertEquals("'k.comply' holds 'compliance' in {region=EU}", d.reason)
    }
}
