package dev.workflow.app.config

import ch.qos.logback.classic.spi.ILoggingEvent
import dev.workflow.app.config.web.ACCESS_LOGGER
import org.springframework.boot.json.JsonWriter
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer

private const val APP_DATASET = "workflow-engine.app"

/**
 * ECS fields Boot's formatter does not fill by itself. `event.dataset` for app lines (the access line sets its own);
 * `trace.id` / `span.id` from the active span (Micrometer Tracing puts them in the MDC as traceId and spanId).
 * `labels.endpoint`, `labels.origin` and `event.action` need no code here: LabelledAppender puts them in every
 * record's MDC under those names and Boot's formatter nests the dotted keys.
 */
class EventDatasetCustomizer : StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {
    override fun customize(members: JsonWriter.Members<ILoggingEvent>) {
        members.add("event.dataset") { e: ILoggingEvent -> if (e.loggerName == ACCESS_LOGGER) null else APP_DATASET }.whenNotNull()
        members.add("trace") { e: ILoggingEvent -> e.mdcPropertyMap["traceId"]?.let { mapOf("id" to it) } }.whenNotNull()
        members.add("span") { e: ILoggingEvent -> e.mdcPropertyMap["spanId"]?.let { mapOf("id" to it) } }.whenNotNull()
    }
}
