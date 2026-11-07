package dev.workflow.app.config.web

import io.github.bucket4j.Bucket
import io.github.bucket4j.ConsumptionProbe
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * `workflow.rate-limit.*` (SECURITY.md): per-minute limits for each class, per key. [enabled] is false only in the
 * test and NFR profiles (`RATE_LIMIT_ENABLED`).
 */
@ConfigurationProperties("workflow.rate-limit")
data class RateLimitProperties(
    val enabled: Boolean = true,
    val readPerMin: Int = 300,
    val writePerMin: Int = 60,
    val heavyPerMin: Int = 20,
) {
    fun limit(c: RateClass): Int = when (c) {
        RateClass.READ -> readPerMin
        RateClass.WRITE -> writePerMin
        RateClass.HEAVY -> heavyPerMin
    }
}

/** Reads and writes are classed by method; a route marks itself heavy next to its label. */
enum class RateClass(val label: String) { READ("read"), WRITE("write"), HEAVY("heavy") }

/** A token bucket per (key, class), kept in process (the engine runs as a single instance). Least recently used keys fall out past [MAX_KEYS]. */
class RateLimiter(private val props: RateLimitProperties) {
    private val buckets = object : LinkedHashMap<String, Bucket>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bucket>) = size > MAX_KEYS
    }

    fun take(key: String, rateClass: RateClass): ConsumptionProbe =
        bucket("${rateClass.label}|$key", rateClass).tryConsumeAndReturnRemaining(1)

    private fun bucket(id: String, rateClass: RateClass): Bucket = synchronized(buckets) {
        buckets.getOrPut(id) {
            val n = props.limit(rateClass).toLong()
            Bucket.builder().addLimit { it.capacity(n).refillGreedy(n, Duration.ofMinutes(1)) }.build()
        }
    }

    private companion object {
        const val MAX_KEYS = 10_000
    }
}
