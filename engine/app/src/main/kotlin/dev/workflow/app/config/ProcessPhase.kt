package dev.workflow.app.config

import dev.workflow.app.telemetry.EndpointContext

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.spi.AppenderAttachable
import ch.qos.logback.core.spi.AppenderAttachableImpl
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.event.ApplicationStartingEvent
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextClosedEvent

/**
 * The base labels every log line carries when no request or job is in flight (OBSERVABILITY.md section 1,
 * endpoint labels): `labels.endpoint` Startup, Background or Shutdown, with `event.action` `process.start`,
 * `process.background` or `process.stop` and `labels.origin` `workflow-engine: <endpoint>`.
 */
enum class ProcessPhase(val endpoint: String, val action: String) {
    STARTUP("Startup", "process.start"),
    BACKGROUND("Background", "process.background"),
    SHUTDOWN("Shutdown", "process.stop");

    val origin: String get() = "${EndpointContext.APP}: $endpoint"

    companion object {
        @Volatile
        var current: ProcessPhase = STARTUP
    }
}

/** Moves the phase with the application lifecycle: starting, ready, closing. Registered in spring.factories to see the first event. */
class ProcessPhaseListener : ApplicationListener<ApplicationEvent> {
    override fun onApplicationEvent(event: ApplicationEvent) {
        when (event) {
            is ApplicationStartingEvent -> ProcessPhase.current = ProcessPhase.STARTUP
            is ApplicationReadyEvent -> ProcessPhase.current = ProcessPhase.BACKGROUND
            is ContextClosedEvent -> ProcessPhase.current = ProcessPhase.SHUTDOWN
            else -> Unit
        }
    }
}

/** An event whose MDC view carries the three labels: the request's or job's own, else the process phase's. */
private class LabelledEvent(private val e: ILoggingEvent, private val mdc: Map<String, String>) : ILoggingEvent by e {
    override fun getMDCPropertyMap(): Map<String, String> = mdc

    @Deprecated("Logback's alias of getMDCPropertyMap")
    override fun getMdc(): Map<String, String> = mdc
}

/**
 * Wraps the real appenders (stdout and OTLP) so that EVERY record, ours or Spring's, Hibernate's, Flyway's or
 * Tomcat's, carries `labels.endpoint`, `event.action` and `labels.origin` in both. Injection happens here, at the
 * logger layer, never at call sites. Request and job context (set in the MDC) override the phase defaults.
 */
class LabelledAppender : AppenderBase<ILoggingEvent>(), AppenderAttachable<ILoggingEvent> {
    private val children = AppenderAttachableImpl<ILoggingEvent>()

    override fun append(event: ILoggingEvent) {
        children.appendLoopOnAppenders(LabelledEvent(event, labelled(event.mdcPropertyMap)))
    }

    override fun stop() {
        children.detachAndStopAllAppenders()
        super.stop()
    }

    override fun addAppender(newAppender: Appender<ILoggingEvent>) = children.addAppender(newAppender)
    override fun iteratorForAppenders(): MutableIterator<Appender<ILoggingEvent>> = children.iteratorForAppenders()
    override fun getAppender(name: String?): Appender<ILoggingEvent>? = children.getAppender(name)
    override fun isAttached(appender: Appender<ILoggingEvent>): Boolean = children.isAttached(appender)
    override fun detachAndStopAllAppenders() = children.detachAndStopAllAppenders()
    override fun detachAppender(appender: Appender<ILoggingEvent>): Boolean = children.detachAppender(appender)
    override fun detachAppender(name: String?): Boolean = children.detachAppender(name)

    private fun labelled(mdc: Map<String, String>?): Map<String, String> {
        val phase = ProcessPhase.current
        val own = mdc.orEmpty()
        return own + mapOf(
            EndpointContext.ENDPOINT to own[EndpointContext.ENDPOINT].orEmpty().ifBlank { phase.endpoint },
            EndpointContext.ACTION to own[EndpointContext.ACTION].orEmpty().ifBlank { phase.action },
            EndpointContext.ORIGIN to own[EndpointContext.ORIGIN].orEmpty().ifBlank { phase.origin },
        )
    }
}
