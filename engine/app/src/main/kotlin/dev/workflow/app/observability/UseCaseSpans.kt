package dev.workflow.app.observability

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.springframework.stereotype.Component
import java.util.function.Supplier

/**
 * One manual span (and duration metric) per key use case. Inside a request it is a child of the server
 * span; in a background tick it is its own root span, so the tick's log lines carry a trace.id too.
 * Attributes are low-cardinality and never personal: workflow names and step ids, never a login.
 */
@Component
class UseCaseSpans(private val registry: ObservationRegistry) {
    fun <T> run(name: String, vararg attributes: Pair<String, String>, block: () -> T): T {
        val observation = Observation.createNotStarted(name, registry)
        attributes.forEach { (k, v) -> observation.lowCardinalityKeyValue(k, v) }
        return observation.observe(Supplier { block() })!!
    }
}
