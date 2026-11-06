package dev.workflow.app

import dev.workflow.app.security.Authenticated
import dev.workflow.app.security.Public
import dev.workflow.app.security.Requires
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Deny by default (fleet RBAC.md section 5): every route of this app declares exactly one of @Requires,
 * @Authenticated or @Public, and the gate answers 401 with no identity and 403 with rbac's reason for a
 * person without the permission.
 */
class AuthorizationTest : ApiTest() {
    @Autowired lateinit var mappings: RequestMappingInfoHandlerMapping

    /** p.maint holds no grants, so every permission is denied to them, with the reason. */
    private val denied = "p.maint"

    @Test
    fun `every route of this app declares exactly one of Requires, Authenticated or Public`() {
        val ours = mappings.handlerMethods.filterKeys { true }.values
            .filter { it.beanType.name.startsWith("dev.workflow.app") }
        assertTrue(ours.isNotEmpty())
        val undeclared = ours.filter { h ->
            listOf(
                h.method.isAnnotationPresent(Requires::class.java),
                h.method.isAnnotationPresent(Authenticated::class.java),
                h.method.isAnnotationPresent(Public::class.java),
            ).count { it } != 1
        }.map { "${it.beanType.simpleName}.${it.method.name}" }
        assertEquals(emptyList(), undeclared, "routes without exactly one declaration")
    }

    @Test
    fun `no identity gets 401 on every protected read`() {
        for (path in listOf("/instances", "/tasks", "/rbac/people", "/definitions", "/instances/00000000-0000-4000-8000-000000000001")) {
            assertEquals(401, call("GET", path, user = "").status, path)
        }
    }

    @Test
    fun `a denied person gets 403 with the reason on instances, tasks and the people list`() {
        for (path in listOf("/instances", "/tasks", "/rbac/people")) {
            val res = call("GET", path, user = denied)
            assertEquals(403, res.status, path)
            val reason = res.map["reason"] as String?
            assertNotNull(reason, path)
            assertTrue(reason.contains("'$denied'") && reason.contains("'"), reason)
        }
    }

    @Test
    fun `a denied person cannot start a run, and the refusal says which role allows it`() {
        val res = call("POST", "/definitions/confirmation-approval/instances", denied, mapOf("vars" to doc()))
        assertEquals(403, res.status)
        assertTrue((res.map["reason"] as String).contains("instances.start"), "${res.map["reason"]}")
    }

    @Test
    fun `a person who holds a read in one scope sees only the tasks in that scope`() {
        val eu = start(doc(), "counterparty-onboarding").also { }
        assertEquals(200, call("GET", "/tasks", "k.comply").status)
        val listed = call("GET", "/tasks", "k.comply").list
        assertTrue(listed.all { (it["assignee"] as Map<*, *>)["scope"].let { s -> s == emptyMap<String, String>() || (s as Map<*, *>)["region"] == "EU" } })
        assertNotNull(eu.map["id"])
    }

    @Test
    fun `a counterparty sees only its own counterparty's tasks, and the listing omits the rest`() {
        val acme = start(doc(counterparty = "ACME")).let { complete(it, "l.jones", "COUNTER") }
        val globex = start(doc(counterparty = "GLOBEX")).let { complete(it, "l.jones", "COUNTER") }
        val acmeId = acme.map["id"] as String
        val globexId = globex.map["id"] as String

        val own = call("GET", "/tasks?instance=$acmeId", "c.acme")
        assertEquals(200, own.status)
        assertEquals("negotiate", own.list.single()["step"])

        val other = call("GET", "/tasks?instance=$globexId", "c.acme")
        assertEquals(200, other.status, "no 403 leak: the listing just omits it")
        assertEquals(emptyList(), other.list)
        assertEquals(emptyList(), call("GET", "/tasks?mine=true&instance=$globexId", "c.acme").list)
    }
}
