package com.erpbarcode.scanner.data.auth

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the clock remembers between two readings: the latest time it has vouched for, and the
 * moment of the running boot at which it did.
 */
data class ClockMemory(
    /** The latest trusted time, in wall-clock milliseconds. Never goes down. */
    val highWater: Long,
    /** `SystemClock.elapsedRealtime()` when [highWater] was noted. */
    val anchorElapsed: Long,
    /** The boot [anchorElapsed] belongs to. */
    val boot: Int,
)

/** Where a [ClockMemory] is kept. A plain file on the device; a map in a test. */
interface ClockMemoryStore {
    fun read(): ClockMemory?
    fun write(memory: ClockMemory)
}

/**
 * The time a remembered sign-in is measured against, given what the device says now.
 *
 * The device's clock can be set by whoever holds the device, so on its own it would let a
 * remembered sign-in be kept alive by turning the date back. This takes the latest of three:
 * what the clock says, the latest time ever vouched for, and that time plus what has run on the
 * boot's own stopwatch since, which no setting changes. So within one boot time cannot be held
 * back at all, and across a restart it cannot go back behind where it already was.
 *
 * What is left: a device that is switched off, or on which the app is not running, and whose
 * clock is then set back before the app starts, hides that stretch. An app has no trusted time
 * of its own while it is not running; the answer to that is to lock the date setting on the
 * device.
 */
fun trustedNow(wall: Long, elapsed: Long, boot: Int, memory: ClockMemory?): Long {
    if (memory == null) return wall
    val sameBoot = boot == memory.boot && elapsed >= memory.anchorElapsed
    val ticked = if (sameBoot) elapsed - memory.anchorElapsed else 0L
    return maxOf(wall, memory.highWater + ticked)
}

/**
 * The device's clock, with a memory. See [trustedNow] for the rule and what it cannot do.
 *
 * Only the remembered sign-in reads it. Document dates, scan times and the NTLM timestamp stay on
 * the device's own clock: those have to agree with the ERP, not withstand the operator.
 */
@Singleton
class TrustedClock internal constructor(
    private val store: ClockMemoryStore,
    private val wall: () -> Long,
    private val elapsed: () -> Long,
    private val boot: () -> Int,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(
        PrefsClockMemory(context),
        System::currentTimeMillis,
        SystemClock::elapsedRealtime,
        // Counts the device's restarts; 0 where a device does not keep it, and then a restart is
        // still seen by the stopwatch starting over.
        { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0) },
    )

    /** The latest reading, kept for as long as the app runs. */
    private var memory: ClockMemory? = null

    /** What the file holds, which is what the app would start from if it were killed now. */
    private var written: ClockMemory? = null
    private var loaded = false

    /** The time to measure a remembered sign-in against. Never earlier than it has been before. */
    @Synchronized
    fun now(): Long {
        if (!loaded) {
            written = store.read()
            memory = written
            loaded = true
        }
        val known = memory
        val w = wall()
        val e = elapsed()
        val b = boot()
        val now = trustedNow(w, e, b, known)
        // Every reading is remembered while the app runs. Remembering only what was written let a
        // reading the device clock had pushed ahead be followed by an earlier one once the clock
        // stepped back, and a sign-in saved in between then looked as if it had been accepted
        // after now.
        val noted = ClockMemory(highWater = now, anchorElapsed = e, boot = b)
        memory = noted

        // The file is written when it would no longer give this reading back: at the first one,
        // after a restart, and when the reading has run ahead of what the file plus the stopwatch
        // come to (the device clock moved it). Otherwise once a minute, which only matters across
        // a restart, where the stopwatch is lost.
        val onFile = written
        val restarted = onFile != null && (b != onFile.boot || e < onFile.anchorElapsed)
        val fromFile = onFile?.let { it.highWater + (if (restarted) 0L else e - it.anchorElapsed) }
        if (onFile == null || fromFile == null || restarted ||
            now - fromFile >= WRITE_WHEN_AHEAD_MS || now - onFile.highWater >= SAVE_EVERY_MS
        ) {
            store.write(noted)
            written = noted
        }
        return now
    }

    /** What the device's own clock says, for comparing with [now]. */
    fun wallNow(): Long = wall()

    /**
     * Whether the device's clock stands well behind a time this app has already seen: the same
     * test [rememberedSignIn] applies. It has two causes that cannot be told apart from here: the
     * clock was ahead once and has since been put right, or it has been set back.
     */
    fun deviceClockIsBehind(): Boolean = wall() < now() - CLOCK_TOLERANCE_MS

    /**
     * Takes the device's clock at its word again, forgetting the later time seen before.
     *
     * A clock that was once set ahead by mistake leaves the trusted time ahead by as much for
     * good: both run on at the same rate, so the device would never let anybody in on a
     * remembered sign-in again. This is the way back, and it must be used only where no saved
     * sign-in is carried across it, since going back in time is exactly what a remembered sign-in
     * must not survive: `AppViewModel.signIn` calls it when the ERP has just accepted a sign-in
     * on a device whose clock is behind, after removing every saved sign-in.
     */
    @Synchronized
    fun acceptDeviceClock() {
        val noted = ClockMemory(highWater = wall(), anchorElapsed = elapsed(), boot = boot())
        store.write(noted)
        written = noted
        memory = noted
        loaded = true
    }

    private companion object {
        const val SAVE_EVERY_MS = 60_000L
        const val WRITE_WHEN_AHEAD_MS = 1_000L
    }
}

/** The clock's memory in a preferences file of its own. Nothing in it is secret. */
private class PrefsClockMemory(private val context: Context) : ClockMemoryStore {
    private val prefs by lazy { context.getSharedPreferences("trusted_clock", Context.MODE_PRIVATE) }

    override fun read(): ClockMemory? {
        val highWater = prefs.getLong("highWater", -1L)
        if (highWater < 0) return null
        return ClockMemory(highWater, prefs.getLong("anchorElapsed", 0L), prefs.getInt("boot", 0))
    }

    override fun write(memory: ClockMemory) {
        prefs.edit()
            .putLong("highWater", memory.highWater)
            .putLong("anchorElapsed", memory.anchorElapsed)
            .putInt("boot", memory.boot)
            .apply()
    }
}
