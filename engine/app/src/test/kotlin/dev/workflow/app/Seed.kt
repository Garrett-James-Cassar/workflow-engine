package dev.workflow.app

import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.system.exitProcess

/**
 * dm.5: demo data for a running engine, built from the Object Mother ([Mother]) and sent through the public API, so
 * the data is what a real client would create. `make seed` runs it; `make reseed` passes `reset` first (the engine
 * needs ALLOW_RESET=true). Idempotent: when runs already exist it starts nothing.
 * Gradle task `seed` (engine/app/build.gradle.kts); the engine is on `$PORT` (default 47831).
 */
private val http = HttpClient.newHttpClient()
private val mapper: JsonMapper = jacksonMapperBuilder().build()
private val base = "http://localhost:${System.getenv("PORT") ?: "47831"}"

private fun call(method: String, path: String, user: String, body: Any? = null): HttpResponse<String> {
    val b = if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))
    val req = HttpRequest.newBuilder(URI.create(base + path)).header("X-User", user).header("Content-Type", "application/json")
        .method(method, b).build()
    return http.send(req, HttpResponse.BodyHandlers.ofString())
}

private fun runCount(): Int = mapper.readTree(call("GET", "/api/v1/instances", "e.admin").body()).let { it.get("items")?.size() ?: it.size() }

/** The seed set: two of each of the two main samples and one of the others, each with its own named overrides. */
private fun seedRuns(): List<Pair<String, Map<String, Any?>>> = listOf(
    "confirmation-approval" to Mother.doc(),
    "confirmation-approval" to Mother.doc(notional = 2_000_000, counterparty = "Globex", id = "D-2"),
    "trade-booking" to Mother.trade(),
    "trade-booking" to Mother.trade(ref = "T-2", quantity = 750_000),
    "counterparty-onboarding" to Mother.counterparty(base = base),
    "clause-change-approval" to Mother.change(base = base),
)

fun main(args: Array<String>) {
    if ("reset" in args) {
        val r = call("POST", "/demo/reset", "e.admin")
        check(r.statusCode() == 200) { "reset refused (${r.statusCode()}): the engine needs ALLOW_RESET=true and dev auth. ${r.body()}" }
        println("reset: all runs wiped")
    }
    val existing = runCount()
    if (existing > 0) {
        println("seed: $existing runs already exist, nothing started (make reseed wipes first)")
        return
    }
    for ((workflow, vars) in seedRuns()) {
        val r = call("POST", "/api/v1/definitions/$workflow/instances", "a.ops", mapOf("vars" to vars))
        if (r.statusCode() !in 200..299) { System.err.println("seed: $workflow failed (${r.statusCode()}): ${r.body()}"); exitProcess(1) }
        println("seed: started $workflow")
    }
}
