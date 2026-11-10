package dev.workflow.app

import dev.workflow.app.domain.NotFoundException
import dev.workflow.app.runtime.ResetGuard
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** dm.6: a reset is refused unless ALLOW_RESET=true, and the app will not start with it on in production. */
class ResetGuardTest {
    @Test
    fun `DM6 a reset is refused without the flag`() {
        assertFailsWith<NotFoundException> { ResetGuard(false, MockEnvironment()).check() }
        ResetGuard(true, MockEnvironment()).check()
    }

    @Test
    fun `DM6 the flag is refused in production, by profile or by APP_ENV`() {
        val byProfile = MockEnvironment().apply { setActiveProfiles("prod") }
        val e = assertFailsWith<IllegalStateException> { ResetGuard(true, byProfile) }
        assertTrue(e.message!!.contains("ALLOW_RESET"))
        assertFailsWith<IllegalStateException> { ResetGuard(true, MockEnvironment().withProperty("APP_ENV", "production")) }
        ResetGuard(false, byProfile) // off in production is the normal case
    }
}
