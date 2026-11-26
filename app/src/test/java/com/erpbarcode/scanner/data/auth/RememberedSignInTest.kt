package com.erpbarcode.scanner.data.auth

import com.erpbarcode.scanner.data.extsystem.ntHashOf
import com.erpbarcode.scanner.testing.DAY
import com.erpbarcode.scanner.testing.HOUR
import com.erpbarcode.scanner.testing.TestClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who may be let in without the ERP, and the clock that is measured against.
 *
 * Most of these must come out as a refusal to pass: the rule exists to say no, and each way it has
 * of saying no is one somebody could otherwise get in by.
 */
class RememberedSignInTest {

    private val now = 1_800_000_000_000L

    /** The rule with everything in order, and one thing changed at a time. */
    private fun rule(
        days: Int = 5,
        keyInSecureHardware: Boolean = true,
        verifiedAt: Long? = now - DAY,
        trustedNow: Long = now,
        wallNow: Long = trustedNow,
    ) = rememberedSignIn(days, keyInSecureHardware, verifiedAt, trustedNow, wallNow)

    // ── The rule ───────────────────────────────────────────────────────────────

    @Test
    fun `a sign-in the ERP accepted yesterday lets its operator in`() {
        assertEquals(RememberedSignIn.ALLOWED, rule())
    }

    @Test
    fun `the last moment of the last day still counts, the next does not`() {
        assertEquals(RememberedSignIn.ALLOWED, rule(verifiedAt = now - 5 * DAY))
        assertEquals(RememberedSignIn.EXPIRED, rule(verifiedAt = now - 5 * DAY - 1))
    }

    @Test
    fun `one day means one day`() {
        assertEquals(RememberedSignIn.ALLOWED, rule(days = 1, verifiedAt = now - DAY))
        assertEquals(RememberedSignIn.EXPIRED, rule(days = 1, verifiedAt = now - DAY - 1))
    }

    @Test
    fun `nothing is remembered when the configuration says zero days`() {
        assertEquals(RememberedSignIn.OFF, rule(days = 0, verifiedAt = now))
        assertEquals(RememberedSignIn.OFF, rule(days = -3, verifiedAt = now))
    }

    @Test
    fun `nothing is remembered on a device whose key is not in secure hardware`() {
        assertEquals(RememberedSignIn.UNPROTECTED_KEY, rule(keyInSecureHardware = false, verifiedAt = now))
    }

    @Test
    fun `an operator nothing is saved for is not let in`() {
        assertEquals(RememberedSignIn.NOT_SAVED, rule(verifiedAt = null))
    }

    @Test
    fun `a sign-in accepted after now is not believed`() {
        assertEquals(RememberedSignIn.EXPIRED, rule(verifiedAt = now + 1))
    }

    @Test
    fun `no configuration can remember for longer than thirty days`() {
        assertEquals(RememberedSignIn.ALLOWED, rule(days = 365, verifiedAt = now - 30 * DAY))
        assertEquals(RememberedSignIn.EXPIRED, rule(days = 365, verifiedAt = now - 30 * DAY - 1))
    }

    @Test
    fun `a device clock standing well behind the trusted time lets nobody in`() {
        assertEquals(RememberedSignIn.CLOCK_BEHIND, rule(wallNow = now - HOUR - 1))
        assertEquals(RememberedSignIn.CLOCK_BEHIND, rule(wallNow = now - 3 * DAY))
    }

    @Test
    fun `a clock corrected by less than an hour costs nobody their sign-in`() {
        assertEquals(RememberedSignIn.ALLOWED, rule(wallNow = now - HOUR))
        assertEquals(RememberedSignIn.ALLOWED, rule(wallNow = now - 60_000))
    }

    @Test
    fun `setting the device clock back does not bring an expired sign-in back`() {
        // The device says it is still the day of the sign-in; the trusted clock knows better.
        val verifiedAt = now - 6 * DAY
        assertEquals(RememberedSignIn.EXPIRED, rule(verifiedAt = verifiedAt, trustedNow = now, wallNow = verifiedAt))
    }

    @Test
    fun `the first reason that applies is the one given`() {
        assertEquals(RememberedSignIn.OFF, rule(days = 0, keyInSecureHardware = false, verifiedAt = null))
        assertEquals(RememberedSignIn.UNPROTECTED_KEY, rule(keyInSecureHardware = false, verifiedAt = null))
        assertEquals(RememberedSignIn.EXPIRED, rule(verifiedAt = now - 6 * DAY, wallNow = now - 3 * DAY))
    }

    // ── A scanned sign-in code held against the saved sign-in ──────────────────

    private val accepted = ntHashOf("the password the ERP accepted")
    private val other = ntHashOf("some other password")

    @Test
    fun `a code carrying the password the ERP accepted lets its operator in`() {
        assertEquals(SignInCodeCheck.MATCHES, checkSignInCode(RememberedSignIn.ALLOWED, accepted, accepted))
    }

    @Test
    fun `a code carrying another password goes to the ERP, and is noted as differing`() {
        assertEquals(SignInCodeCheck.DIFFERS, checkSignInCode(RememberedSignIn.ALLOWED, accepted, other))
    }

    @Test
    fun `the right code opens nothing that the rule does not allow`() {
        // Must fail to pass, one for each way the rule says no.
        RememberedSignIn.entries.filter { it != RememberedSignIn.ALLOWED }.forEach { refusal ->
            assertEquals(
                "the right code under $refusal",
                SignInCodeCheck.NOT_REMEMBERED,
                checkSignInCode(refusal, accepted, accepted),
            )
        }
    }

    @Test
    fun `with nothing saved there is nothing to match`() {
        assertEquals(SignInCodeCheck.NOT_REMEMBERED, checkSignInCode(RememberedSignIn.ALLOWED, null, accepted))
    }

    @Test
    fun `a saved hash that is damaged matches no code, not even one damaged the same way`() {
        assertEquals(SignInCodeCheck.DIFFERS, checkSignInCode(RememberedSignIn.ALLOWED, "", ""))
        assertEquals(SignInCodeCheck.DIFFERS, checkSignInCode(RememberedSignIn.ALLOWED, "damaged", "damaged"))
    }

    // ── The trusted time, worked out from one reading ──────────────────────────

    private val seen = ClockMemory(highWater = now, anchorElapsed = 1_000_000L, boot = 7)

    @Test
    fun `with nothing remembered the time is what the device says`() {
        assertEquals(now, trustedNow(wall = now, elapsed = 5L, boot = 1, memory = null))
    }

    @Test
    fun `a clock left alone is believed`() {
        assertEquals(now + 2 * HOUR, trustedNow(now + 2 * HOUR, seen.anchorElapsed + 2 * HOUR, 7, seen))
    }

    @Test
    fun `a clock set back within one boot is overruled by the stopwatch`() {
        // Three days back on the clock, two hours on by the stopwatch nobody can set.
        assertEquals(now + 2 * HOUR, trustedNow(now - 3 * DAY, seen.anchorElapsed + 2 * HOUR, 7, seen))
    }

    @Test
    fun `a clock set forward is believed, because it can only shorten a sign-in`() {
        assertEquals(now + 9 * DAY, trustedNow(now + 9 * DAY, seen.anchorElapsed + HOUR, 7, seen))
    }

    @Test
    fun `after a restart with the clock set back, time does not go behind where it was`() {
        assertEquals(now, trustedNow(wall = now - 10 * DAY, elapsed = 30_000L, boot = 8, memory = seen))
    }

    @Test
    fun `after a restart with an honest clock, the time the device was off counts`() {
        assertEquals(now + 2 * DAY, trustedNow(wall = now + 2 * DAY, elapsed = 30_000L, boot = 8, memory = seen))
    }

    @Test
    fun `a stopwatch that has started over is not counted, even if the boot number is the same`() {
        // A device that does not count its restarts: the stopwatch below the mark says there was one.
        assertEquals(now, trustedNow(wall = now - DAY, elapsed = 30_000L, boot = 7, memory = seen))
    }

    // ── The clock itself, over several readings ────────────────────────────────

    @Test
    fun `the clock never reads earlier than it has read before`() {
        val clock = TestClock()
        val first = clock.trusted.now()

        clock.wall -= 3 * DAY
        clock.elapsed += HOUR

        assertEquals(first + HOUR, clock.trusted.now())
    }

    @Test
    fun `what the clock has seen survives the app being started again`() {
        val clock = TestClock()
        val first = clock.trusted.now()

        clock.wall -= 3 * DAY
        clock.elapsed += HOUR

        assertEquals(first + HOUR, clock.newTrustedClock().now())
    }

    @Test
    fun `after a restart with the clock set back, time stands where it was and runs on from there`() {
        val clock = TestClock()
        val first = clock.trusted.now()

        clock.restart()
        clock.wall = first - 10 * DAY
        val afterRestart = clock.newTrustedClock()

        assertEquals(first, afterRestart.now())
        clock.pass(HOUR)
        assertEquals(first + HOUR, afterRestart.now())
    }

    @Test
    fun `a device left switched off for a week has aged a week`() {
        val clock = TestClock()
        val first = clock.trusted.now()

        clock.restart(offFor = 7 * DAY)

        assertEquals(first + 7 * DAY, clock.newTrustedClock().now())
    }

    @Test
    fun `a reading the device clock pushed ahead is not followed by an earlier one`() {
        // Half a minute ahead and back again: less than what the clock writes down on its own.
        val clock = TestClock()
        val first = clock.trusted.now()

        clock.wall += 30_000
        val ahead = clock.trusted.now()
        clock.wall -= 30_000

        assertEquals(first + 30_000, ahead)
        assertEquals("never earlier than it has read before", ahead, clock.trusted.now())
    }

    @Test
    fun `a reading the device clock pushed ahead is still there after the app is started again`() {
        val clock = TestClock()
        clock.trusted.now()

        clock.wall += 30_000
        val ahead = clock.trusted.now()
        clock.wall -= 30_000

        assertEquals(ahead, clock.newTrustedClock().now())
    }

    @Test
    fun `a clock once set a day ahead keeps the device behind for as long as it stays on`() {
        // Must fail to pass: without the ERP there is no telling a corrected clock from one set back.
        val clock = TestClock()
        clock.trusted.now()

        clock.wall += DAY
        clock.trusted.now()
        clock.wall -= DAY
        clock.pass(3 * DAY)

        assertTrue(clock.trusted.deviceClockIsBehind())
        assertEquals(clock.wall + DAY, clock.trusted.now())
    }

    @Test
    fun `a clock corrected by a few minutes is not a clock that is behind`() {
        val clock = TestClock()
        clock.trusted.now()

        clock.wall += 5 * 60_000
        clock.trusted.now()
        clock.wall -= 5 * 60_000

        assertTrue(!clock.trusted.deviceClockIsBehind())
    }

    @Test
    fun `taking the device clock at its word again ends that, and holds across an app restart`() {
        val clock = TestClock()
        clock.trusted.now()
        clock.wall += DAY
        clock.trusted.now()
        clock.wall -= DAY

        clock.trusted.acceptDeviceClock()

        assertTrue(!clock.trusted.deviceClockIsBehind())
        assertEquals(clock.wall, clock.trusted.now())
        assertEquals(clock.wall, clock.newTrustedClock().now())
        // And from there it guards as before.
        clock.wall -= 3 * DAY
        clock.elapsed += HOUR
        assertTrue(clock.trusted.deviceClockIsBehind())
    }

    @Test
    fun `the device clock and the trusted clock agree while nobody touches the clock`() {
        val clock = TestClock()
        clock.trusted.now()
        clock.pass(5 * HOUR)
        assertEquals(clock.wall, clock.trusted.now())
        assertEquals(clock.wall, clock.trusted.wallNow())
        assertTrue(clock.memory != null)
    }
}
