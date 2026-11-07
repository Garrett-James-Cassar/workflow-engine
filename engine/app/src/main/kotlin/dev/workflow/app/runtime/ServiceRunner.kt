package dev.workflow.app.runtime

import dev.workflow.app.domain.ServiceOutcome
import dev.workflow.app.ports.HttpCaller
import dev.workflow.core.ServiceContext
import dev.workflow.core.ServiceHandler
import dev.workflow.core.Step
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.time.Duration

/** Runs a service task's linked code (a handler bean, or the built-in HTTP call) with retries. */
@Component
class ServiceRunner(
    private val context: ApplicationContext,
    private val http: HttpCaller,
    @Value("\${workflow.service.backoff:200ms}") private val backoff: Duration,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun run(step: Step.ServiceTask, ctx: ServiceContext): ServiceOutcome {
        var lastError: Exception? = null
        for (attempt in 0..step.retries) {
            try {
                return attemptOnce(step, ctx)
            } catch (e: Exception) {
                lastError = e
                log.warn("service task '{}' attempt {} of {} failed: {}", step.id, attempt + 1, step.retries + 1, e.message)
                if (attempt < step.retries) Thread.sleep(backoff.toMillis() shl attempt)
            }
        }
        log.error("service task '{}' failed after {} attempt(s); the run stops on an incident", step.id, step.retries + 1, lastError)
        return ServiceOutcome.Failed("service task '${step.id}' failed after ${step.retries + 1} attempt(s): ${lastError?.message ?: lastError}")
    }

    private fun attemptOnce(step: Step.ServiceTask, ctx: ServiceContext): ServiceOutcome.Ok =
        step.http?.let { ServiceOutcome.Ok("OK", http.call(it, ctx.vars), "service:http") } ?: runHandler(step, ctx)

    private fun runHandler(step: Step.ServiceTask, ctx: ServiceContext): ServiceOutcome.Ok {
        @Suppress("UNCHECKED_CAST")
        val type = step.handler!!.java as Class<ServiceHandler<*>>
        val handler: ServiceHandler<*> = context.getBeanProvider(type).getIfAvailable { context.autowireCapableBeanFactory.createBean(type) }
        val result = handler.execute(ctx)
        return ServiceOutcome.Ok(result.outcome.name, result.output, "service:${type.simpleName}")
    }
}
