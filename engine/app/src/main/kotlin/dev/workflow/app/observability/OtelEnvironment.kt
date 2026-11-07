package dev.workflow.app.observability

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

/**
 * OBSERVABILITY.md section 2, INDEPENDENCE.md: telemetry is never a startup dependency. With
 * `OTEL_EXPORTER_OTLP_ENDPOINT` unset the OTLP exporters (traces, metrics, logs) are switched off, so
 * nothing tries to reach a collector. Spans are still created, which is what fills trace.id in the logs
 * and the `traceparent` on responses. With it set, Boot maps the standard `OTEL_*` variables itself.
 */
class OtelEnvironment : EnvironmentPostProcessor {
    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        if (!environment.getProperty("OTEL_EXPORTER_OTLP_ENDPOINT").isNullOrBlank()) return
        val off = listOf("management.tracing.export.otlp.enabled", "management.otlp.metrics.export.enabled", "management.logging.export.otlp.enabled")
        environment.propertySources.addLast(MapPropertySource("workflow-otel-off", off.associateWith { false }))
    }
}
