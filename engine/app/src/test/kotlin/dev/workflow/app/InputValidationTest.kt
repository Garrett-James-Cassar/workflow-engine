package dev.workflow.app

import dev.workflow.app.api.dtos.DefinitionSummary
import dev.workflow.app.config.web.ResponseInvalidException
import dev.workflow.app.config.web.ResponseValidation
import jakarta.validation.Constraint
import jakarta.validation.Valid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Input validation (SECURITY.md sec.1, api.6): one schema per route, strict bodies, size and media-type limits, problem+json. */
class InputValidationTest : ApiTest() {
    @Autowired @Qualifier("requestMappingHandlerMapping") lateinit var mapping: RequestMappingHandlerMapping
    @Autowired lateinit var responses: ResponseValidation

    private val secret = "SECRET-VALUE-9f3"
    private val problemJson = "application/problem+json"

    private val ours get() = mapping.handlerMethods.values.filter { it.beanType.name.startsWith("dev.workflow.app") }

    private fun constrained(a: Array<out Annotation>) = a.any { it is Valid || it.annotationClass.java.isAnnotationPresent(Constraint::class.java) }

    /** A parameter has a schema when the framework binds it from the request and a constraint or @Valid sits on it. */
    private val typedByClass = setOf(java.util.UUID::class.java, Boolean::class.javaPrimitiveType, java.lang.Boolean::class.java)

    private fun schemaGaps(h: HandlerMethod): List<String> = h.methodParameters.filter { p ->
        val a = p.parameterAnnotations
        val bound = a.any { it is RequestBody || it is PathVariable || it is RequestParam } ||
            (a.none { it.annotationClass.java.name.startsWith("org.springframework.web.bind.annotation") } && p.parameterType.name.startsWith("dev.workflow.app.api.dtos"))
        bound && p.parameterType !in typedByClass && !constrained(a)
    }.map { "${h.beanType.simpleName}.${h.method.name}(${it.parameterIndex})" }

    @Test
    fun `S1 every route declares a request schema for its body, path and query`() {
        val gaps = ours.flatMap(::schemaGaps)
        assertEquals(emptyList(), gaps, "parameters bound from the request without a constraint or @Valid")
        assertTrue(ours.size >= 18, "routes seen: ${ours.size}")
    }

    private data class Write(val name: String, val path: () -> String, val body: Map<String, Any?>, val wrongType: Pair<String, Any?>, val required: String?, val user: String = ADMIN)

    private fun writes(): List<Write> {
        val instance = start(doc())
        val id = instance.map["id"] as String
        val task = openTask(instance)
        return listOf(
            Write("start", { "/api/v1/definitions/confirmation-approval/instances" }, mapOf("vars" to doc()), "vars" to "text", null),
            Write("restart", { "/api/v1/instances/$id/restart" }, mapOf("step" to "approve"), "step" to listOf(1), "step"),
            Write("reassign", { "/api/v1/tasks/$task/reassign" }, mapOf("to" to "j.smith"), "toTeam" to secret, "to"),
            Write("complete", { "/api/v1/tasks/$task/complete" }, mapOf("outcome" to "APPROVE"), "form" to secret, "outcome", "l.jones"),
            Write("event", { "/api/v1/events/screening-result" }, mapOf("payload" to mapOf("risk" to "LOW")), "correlation" to secret, null),
        )
    }

    private fun assertProblem(r: Raw, status: Int, type: String): Map<String, Any?> {
        assertEquals(status, r.status, r.text)
        assertTrue(r.headers["content-type"]!!.startsWith(problemJson), "content type ${r.headers["content-type"]}")
        assertTrue(!r.text.contains(secret), "the rejected value is not echoed: ${r.text}")
        assertEquals(type, r.map["type"])
        return r.map
    }

    @Suppress("UNCHECKED_CAST")
    private fun errors(problem: Map<String, Any?>) = (problem["errors"] as List<Map<String, Any?>>).also { assertTrue(it.isNotEmpty()) }

    @Test
    fun `S2 every write route answers 400 for an unknown field, a wrong type and a missing field, as problem+json with paths`() {
        writes().forEach { w ->
            val unknown = assertProblem(raw("POST", w.path(), w.user, body = json.writeValueAsString(w.body + ("bogus" to secret))), 400, "https://fleet.local/problems/validation")
            assertEquals("Invalid request", unknown["title"], w.name)
            assertEquals("bogus", errors(unknown).first()["path"], "${w.name}: the path of the unknown field")
            assertEquals("Unknown field", errors(unknown).first()["message"])

            val wrong = assertProblem(raw("POST", w.path(), w.user, body = json.writeValueAsString(w.body + w.wrongType)), 400, "https://fleet.local/problems/validation")
            assertEquals(w.wrongType.first, errors(wrong).first()["path"], "${w.name}: wrong type path")

            w.required?.let { field ->
                val missing = assertProblem(raw("POST", w.path(), w.user, body = json.writeValueAsString(w.body - field)), 400, "https://fleet.local/problems/validation")
                assertEquals(field, errors(missing).first()["path"], "${w.name}: missing path")
                assertEquals("Required", errors(missing).first()["message"])
            }
        }
    }

    @Test
    fun `S3 limits on lengths, patterns and ranges answer 400 with the path`() {
        val long = "x".repeat(101)
        val tooLong = assertProblem(raw("POST", writes().first { it.name == "restart" }.path(), body = """{"step":"$long"}"""), 400, "https://fleet.local/problems/validation")
        assertEquals("step", errors(tooLong).first()["path"])
        val badPath = assertProblem(raw("POST", "/api/v1/definitions/bad%20name$secret/instances", body = "{}"), 400, "https://fleet.local/problems/validation")
        assertEquals("name", errors(badPath).first()["path"])
        assertEquals("limit", errors(assertProblem(raw("GET", "/api/v1/instances?limit=0"), 400, "https://fleet.local/problems/validation")).first()["path"])
        assertEquals("limit", errors(assertProblem(raw("GET", "/api/v1/instances?limit=$secret"), 400, "https://fleet.local/problems/validation")).first()["path"])
        assertEquals("Wrong type", errors(assertProblem(raw("GET", "/api/v1/instances/$secret"), 400, "https://fleet.local/problems/validation")).first()["message"])
        assertEquals(200, raw("GET", "/api/v1/instances?limit=500").status)
    }

    @Test
    fun `S4 a body over 1 MB answers 413, declared or chunked`() {
        val big = """{"vars":{"a":"${"y".repeat(1_100_000)}"}}"""
        val path = "/api/v1/definitions/confirmation-approval/instances"
        assertProblem(raw("POST", path, body = big), 413, "https://fleet.local/problems/payload-too-large")
        assertProblem(raw("POST", path, body = big, chunked = true), 413, "https://fleet.local/problems/payload-too-large")
    }

    @Test
    fun `S5 a body that is not JSON answers 415`() {
        val path = "/api/v1/definitions/confirmation-approval/instances"
        assertProblem(raw("POST", path, body = "vars=1", contentType = "text/plain"), 415, "https://fleet.local/problems/unsupported-media-type")
        assertProblem(raw("POST", path, body = "{}", contentType = null), 415, "https://fleet.local/problems/unsupported-media-type")
    }

    @Test
    fun `S6 a response that breaks its schema fails in dev and test`() {
        val ok = DefinitionSummary("wf", 1, listOf(1), 3)
        assertEquals(ok, write(ok))
        val bad = DefinitionSummary("wf", 0, listOf(1), -1)
        val e = assertFailsWith<ResponseInvalidException> {
            write(listOf(bad))
        }
        assertEquals(setOf("latestVersion", "steps"), e.paths.toSet())
    }

    private fun write(body: Any) = responses.beforeBodyWrite(
        body,
        dummyParameter(),
        org.springframework.http.MediaType.APPLICATION_JSON,
        org.springframework.http.converter.json.JacksonJsonHttpMessageConverter::class.java,
        org.springframework.http.server.ServletServerHttpRequest(org.springframework.mock.web.MockHttpServletRequest()),
        org.springframework.http.server.ServletServerHttpResponse(org.springframework.mock.web.MockHttpServletResponse()),
    )

    private fun dummyParameter() = org.springframework.core.MethodParameter(Any::class.java.getMethod("toString"), -1)
}
