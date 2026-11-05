package dev.workflow.app.runtime

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Fires TIMED_OUT for event waits past their expiry. In-process, so the engine works standalone;
 * `POST /timeouts/{instance}/{step}` is the same action for an external scheduler.
 */
@Component
class TimeoutPoller(private val instances: InstanceService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${workflow.timeouts.poll-interval:5s}")
    fun fireDue(): Int {
        var fired = 0
        for (wait in instances.expiredWaits()) {
            try {
                instances.fireTimeout(wait.instanceId, wait.step)
                fired++
            } catch (e: ConflictException) {
                log.debug("timeout for {} at '{}' already handled: {}", wait.instanceId, wait.step, e.message)
            } catch (e: Exception) {
                log.error("timeout for {} at '{}' failed", wait.instanceId, wait.step, e)
            }
        }
        return fired
    }
}
