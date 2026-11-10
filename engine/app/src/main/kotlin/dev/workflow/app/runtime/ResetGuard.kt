package dev.workflow.app.runtime

import dev.workflow.app.domain.NotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component

/**
 * dm.6: destructive operations (the demo reset and anything that wipes runs) run only when `ALLOW_RESET=true`, which
 * defaults to false. The app refuses to start with the flag set in production (`prod` profile, or `APP_ENV` prod/production).
 */
@Component
class ResetGuard(
    @Value("\${workflow.allow-reset:false}") private val allowReset: Boolean,
    environment: Environment,
) {
    init {
        check(!(allowReset && isProduction(environment))) {
            "ALLOW_RESET=true is refused in production: unset it, a production database is never reset"
        }
    }

    /** Throws (a 404, as if the route did not exist) unless resets are allowed. */
    fun check() {
        if (!allowReset) throw NotFoundException("resets are disabled: set ALLOW_RESET=true (never in production)")
    }

    private fun isProduction(environment: Environment): Boolean =
        environment.acceptsProfiles(Profiles.of("prod", "production")) ||
            environment.getProperty("APP_ENV") in setOf("prod", "production")
}
