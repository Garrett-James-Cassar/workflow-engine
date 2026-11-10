package dev.workflow.app

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Export and import over HTTP (PORTABLE.md): Config kind `workflows`, the bundle, the Records kinds, and the rejections. */
class PortableTest : ApiTest() {
    @Suppress("UNCHECKED_CAST")
    private fun copyOf(name: String, mutate: MutableMap<String, Any?>.() -> Unit = {}): Map<String, Any?> =
        call("GET", "/api/v1/workflows/flaky:1/export").map.toMutableMap().apply {
            put("name", name)
            put("key", "$name:1")
            mutate()
        }

    /** The shared test clock moves between tests, so the day in a file name is read from it. */
    private fun day(): java.time.LocalDate = java.time.LocalDate.now(clock)

    private fun audits() = jdbc.sql("select count(*) from import_audit").query(Long::class.java).single()

    private fun import(body: Any?, path: String = "/api/v1/workflows/import", user: String = ADMIN) = call("POST", path, user, body)

    @Suppress("UNCHECKED_CAST")
    private fun steps(doc: Map<String, Any?>) = doc["steps"] as List<MutableMap<String, Any?>>

    private fun exported(path: String): Pair<Raw, List<Map<String, Any?>>> =
        raw("GET", path).let { it to (json.readValue(it.text, List::class.java) as List<Map<String, Any?>>) }

    // ---- Config kind: workflows ----

    @Test
    fun `E15 workflows round-trip, export then import then export is byte-identical and a re-import changes nothing`() {
        val before = raw("GET", "/api/v1/workflows/export")
        assertEquals(200, before.status)
        assertTrue(before.headers.getValue("content-disposition").contains("workflows-${day()}.json"), "${before.headers}")
        val builtIn = exported("/api/v1/workflows/export").second
        val same = import(builtIn)
        assertEquals(200, same.status, "${same.body}")
        assertEquals(mapOf("created" to 0, "updated" to 0, "skipped" to builtIn.size, "conflicts" to 0), same.map)

        val copy = copyOf("round-trip")
        assertEquals(1, import(copy).map["created"])
        val after = raw("GET", "/api/v1/workflows/export")
        assertNotEquals(before.text, after.text)
        assertEquals(copy, call("GET", "/api/v1/workflows/round-trip:1/export").map)

        val again = import(exported("/api/v1/workflows/export").second)
        assertEquals(mapOf("created" to 0, "updated" to 0, "skipped" to builtIn.size + 1, "conflicts" to 0), again.map)
        assertEquals(after.text, raw("GET", "/api/v1/workflows/export").text)
    }

    @Test
    fun `E15 a changed imported workflow is an overwrite and names the fields that differ`() {
        import(copyOf("overwrite-me"))
        val changed = copyOf("overwrite-me") { steps(this)[0]["label"] = "Renamed" }
        val plan = import(changed, "/api/v1/workflows/import/preview").map["entries"] as List<Map<String, Any?>>
        assertEquals("overwrite", plan.single()["action"])
        assertEquals(listOf("steps"), plan.single()["changed"])
        assertEquals(1, import(changed).map["updated"])
        assertEquals("Renamed", steps(call("GET", "/api/v1/workflows/overwrite-me:1/export").map)[0]["label"])
    }

    @Test
    fun `E15 the bundle round-trips, export then import then export is byte-identical`() {
        assertEquals(1, import(copyOf("in-bundle"), "/api/v1/import").map["created"])
        val first = raw("GET", "/api/v1/export")
        assertEquals(200, first.status)
        assertTrue(first.headers.getValue("content-disposition").contains("export-${day()}.json"))
        val docs = exported("/api/v1/export").second
        assertTrue(docs.any { it["key"] == "in-bundle:1" } && docs.any { it["key"] == "trade-booking:1" })
        val result = import(docs, "/api/v1/import")
        assertEquals(mapOf("created" to 0, "updated" to 0, "skipped" to docs.size, "conflicts" to 0), result.map)
        assertEquals(first.text, raw("GET", "/api/v1/export").text)
        val preview = import(docs, "/api/v1/import/preview").map["entries"] as List<Map<String, Any?>>
        assertTrue(preview.all { it["action"] == "unchanged" })
    }

    // ---- Rejections ----

    @Test
    fun `E15 a bad entry is a 400 naming its pointer and nothing is written`() {
        val audit = audits()
        val bad = copyOf("rejected-bad") { steps(this)[0]["label"] = 5 }
        val res = import(listOf(copyOf("rejected-ok"), bad))
        assertEquals(400, res.status)
        @Suppress("UNCHECKED_CAST")
        val errors = res.map["errors"] as List<Map<String, Any?>>
        assertEquals("/1/steps/0/label", errors.single()["path"])
        assertEquals(404, call("GET", "/api/v1/workflows/rejected-ok:1/export").status)
        assertEquals(audit, audits())
    }

    @Test
    fun `E15 a newer envelope version, an unknown kind and an unknown field are refused`() {
        val paths = { doc: Any? -> (import(doc).map["errors"] as List<Map<String, Any?>>).map { it["path"] } }
        assertEquals(listOf("/0/version"), paths(copyOf("v2") { put("version", 2) }))
        assertEquals(listOf("/0/kind"), paths(mapOf("kind" to "gadget", "version" to 1, "key" to "x")))
        assertEquals(listOf("/0/bogus"), paths(copyOf("extra") { put("bogus", true) }))
        assertEquals(400, import("not an envelope").status)
        assertEquals(404, call("GET", "/api/v1/workflows/v2:1/export").status)
    }

    @Test
    fun `E15 without the permission the export and import routes answer 403`() {
        listOf("/api/v1/workflows/export", "/api/v1/export", "/api/v1/instances/export", "/api/v1/tasks/export", "/api/v1/waits/export", "/api/v1/incidents/export", "/api/v1/events/export", "/api/v1/people/export")
            .forEach { assertEquals(403, call("GET", it, "r.viewer").status, it) }
        listOf("/api/v1/workflows/import", "/api/v1/workflows/import/preview", "/api/v1/import", "/api/v1/import/preview")
            .forEach { assertEquals(403, import(emptyList<Any>(), it, "r.viewer").status, it) }
        assertEquals(401, call("GET", "/api/v1/workflows/export", "").status)
    }

    @Test
    fun `E15 a preview says what would happen and writes nothing`() {
        val audit = audits()
        val plan = import(copyOf("previewed"), "/api/v1/workflows/import/preview")
        assertEquals(200, plan.status)
        @Suppress("UNCHECKED_CAST")
        val entry = (plan.map["entries"] as List<Map<String, Any?>>).single()
        assertEquals(mapOf("kind" to "workflow", "key" to "previewed:1", "action" to "create", "changed" to emptyList<String>(), "missing" to emptyList<String>()), entry)
        assertEquals(404, call("GET", "/api/v1/workflows/previewed:1/export").status)
        assertEquals(audit, audits())
    }

    @Test
    fun `E15 a handler the app does not have is a missing-ref in the preview and a 400 on import`() {
        val doc = copyOf("needs-handler") { steps(this)[0]["handler"] = "no.such.Handler" }
        @Suppress("UNCHECKED_CAST")
        val entry = (import(doc, "/api/v1/workflows/import/preview").map["entries"] as List<Map<String, Any?>>).single()
        assertEquals("missing-ref", entry["action"])
        assertEquals(listOf(mapOf("kind" to "handler", "key" to "no.such.Handler", "path" to "/steps/0/handler")), entry["missing"])
        val res = import(doc)
        assertEquals(400, res.status)
        @Suppress("UNCHECKED_CAST")
        assertEquals("/0/steps/0/handler", (res.map["errors"] as List<Map<String, Any?>>).single()["path"])
        assertEquals(404, call("GET", "/api/v1/workflows/needs-handler:1/export").status)
    }

    @Test
    fun `E15 a code-defined workflow cannot be changed by an import, a conflict is counted and skipped`() {
        val before = raw("GET", "/api/v1/workflows/flaky:1/export").text
        val changed = call("GET", "/api/v1/workflows/flaky:1/export").map.toMutableMap().also { steps(it)[0]["label"] = "Hijacked" }
        @Suppress("UNCHECKED_CAST")
        assertEquals("conflict", (import(changed, "/api/v1/workflows/import/preview").map["entries"] as List<Map<String, Any?>>).single()["action"])
        assertEquals(mapOf("created" to 0, "updated" to 0, "skipped" to 0, "conflicts" to 1), import(changed).map)
        assertEquals(before, raw("GET", "/api/v1/workflows/flaky:1/export").text)
    }

    @Test
    fun `E15 each import writes one audit row and a preview none`() {
        val start = audits()
        import(copyOf("audited"))
        assertEquals(start + 1, audits())
        import(copyOf("audited"))
        assertEquals(start + 2, audits())
        assertEquals("e.admin", jdbc.sql("select actor from import_audit order by id desc limit 1").query(String::class.java).single())
    }

    @Test
    fun `E15 imports take up to 5 MB where other routes stop at 1 MB`() {
        val pad = { bytes: Int -> json.writeValueAsString(listOf(copyOf("padded") { put("sample", mapOf("pad" to "x".repeat(bytes))) })) }
        assertEquals(200, raw("POST", "/api/v1/workflows/import/preview", body = pad(1_500_000)).status)
        assertEquals(413, raw("POST", "/api/v1/workflows/import/preview", body = pad(5_500_000)).status)
        assertEquals(413, raw("POST", "/api/v1/events/some-event", body = "{\"payload\":{\"pad\":\"" + "x".repeat(1_500_000) + "\"}}").status)
    }

    // ---- Records kinds: export only ----

    private fun records(path: String, kind: String, file: String): List<Map<String, Any?>> {
        val (res, docs) = exported(path)
        assertEquals(200, res.status, path)
        assertTrue(res.headers.getValue("content-disposition").contains("$file-${day()}.json"), "${res.headers}")
        assertTrue(docs.all { it["kind"] == kind && it["version"] == 1 && it["key"] is String }, "$docs")
        return docs
    }

    @Test
    fun `E15 instances export, all and one, without storage bookkeeping`() {
        val id = start(doc()).map["id"] as String
        val doc = records("/api/v1/instances/export", "instance", "instances").single { it["key"] == id }
        assertEquals("confirmation-approval", doc["definition"])
        assertEquals("waiting", doc["status"])
        assertTrue(doc.keys.none { it.contains("At") || it == "id" }, "${doc.keys}")
        assertEquals(doc, call("GET", "/api/v1/instances/$id/export").map)
        assertEquals(404, call("GET", "/api/v1/instances/00000000-0000-0000-0000-000000000000/export").status)
    }

    @Test
    fun `E15 tasks export lists the open tasks`() {
        val run = start(Mother.doc(id = "D-task"))
        val task = records("/api/v1/tasks/export", "task", "tasks").single { it["instance"] == run.map["id"] }
        assertEquals(openTask(run), task["key"])
        assertEquals("OPEN", task["status"])
    }

    @Test
    fun `E15 waits export lists the open waits`() {
        val run = start(Mother.counterparty(base = "http://localhost:${env.getProperty("local.server.port")}"), "counterparty-onboarding")
        val wait = records("/api/v1/waits/export", "wait", "waits").single { it["instance"] == run.map["id"] }
        assertEquals("screening-result", wait["event"])
        assertEquals("caseId", wait["correlationKey"])
    }

    @Test
    fun `E15 incidents export lists the failed runs`() {
        FlakyHandler.failing = true
        try {
            val run = start(emptyMap(), name = "flaky")
            val incident = records("/api/v1/incidents/export", "incident", "incidents").single { it["key"] == run.map["id"] }
            assertEquals("flaky", incident["failedStep"])
            assertEquals("flaky", incident["definition"])
        } finally {
            FlakyHandler.failing = false
        }
    }

    @Test
    fun `E15 events export carries the history of every run`() {
        val id = start(Mother.doc(id = "D-events")).map["id"] as String
        val events = records("/api/v1/events/export", "event", "events").filter { it["instance"] == id }
        assertTrue(events.isNotEmpty() && events.any { it["type"] == "instance-started" }, "$events")
        assertEquals("a.ops", events.single { it["type"] == "instance-started" }["actor"])
    }

    @Test
    fun `E15 people export lists who the app can act as`() {
        val people = records("/api/v1/people/export", "person", "people")
        val admin = people.single { it["key"] == "e.admin" }
        assertEquals("Erin Admin", admin["name"])
        assertEquals(listOf(mapOf("role" to "engine-admin", "scope" to emptyMap<String, String>())), admin["grants"])
    }
}
