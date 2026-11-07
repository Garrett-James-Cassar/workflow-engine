package dev.workflow.app.config

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Component

/**
 * Hands the OpenTelemetry SDK to the Logback appender declared in logback-spring.xml. With
 * `OTEL_EXPORTER_OTLP_ENDPOINT` set, every log line is then also exported as an OTLP log record (Loki, with
 * trace_id and span_id); with it unset the SDK has no log exporter and nothing leaves the process.
 */
@Component
class LogExport(private val openTelemetry: OpenTelemetry) : InitializingBean {
    override fun afterPropertiesSet() = OpenTelemetryAppender.install(openTelemetry)
}
