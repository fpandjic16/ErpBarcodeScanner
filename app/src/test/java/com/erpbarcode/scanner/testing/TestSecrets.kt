package com.erpbarcode.scanner.testing

import com.erpbarcode.scanner.data.auth.ClockMemory
import com.erpbarcode.scanner.data.auth.ClockMemoryStore
import com.erpbarcode.scanner.data.auth.SecretEditor
import com.erpbarcode.scanner.data.auth.SecretStorage
import com.erpbarcode.scanner.data.auth.TrustedClock

/**
 * What `SecureStore` keeps, in a map: the Android Keystore does not exist in a local test, and what
 * is built on that storage is worth testing without it. Plain Kotlin, so it serves a plain JUnit
 * test as well as one under Robolectric.
 */
internal class InMemorySecrets : SecretStorage {
    private val values = mutableMapOf<String, String>()

    override fun getString(name: String): String? = values[name]

    override fun edit(block: SecretEditor.() -> Unit) {
        object : SecretEditor {
            override fun putString(name: String, value: String) { values[name] = value }
            override fun remove(name: String) { values.remove(name) }
        }.block()
    }

    /** How many secrets are kept, for a test that says nothing must be left. */
    val size: Int get() = values.size
}

/** One day, in the milliseconds every clock here counts in. */
internal const val DAY = 86_400_000L

/** One hour. */
internal const val HOUR = 3_600_000L

/**
 * A device's clock set by hand: what the wall clock says, what the boot's stopwatch says and which
 * boot it is, each changed on its own, because telling them apart is the whole point of
 * [TrustedClock].
 */
internal class TestClock(
    var wall: Long = 1_800_000_000_000L,
    var elapsed: Long = 50_000_000L,
    var boot: Int = 7,
) {
    /** What survives a restart of the app: read and written by [trusted]. */
    var memory: ClockMemory? = null

    val trusted: TrustedClock = newTrustedClock()

    /** A clock reading the same memory afresh, as after the app is started again. */
    fun newTrustedClock(): TrustedClock = TrustedClock(
        object : ClockMemoryStore {
            override fun read(): ClockMemory? = memory
            override fun write(memory: ClockMemory) { this@TestClock.memory = memory }
        },
        { wall },
        { elapsed },
        { boot },
    )

    /** Time passes: the wall clock and the stopwatch move together, as they do on a device left alone. */
    fun pass(ms: Long) {
        wall += ms
        elapsed += ms
    }

    /** The device is switched off for [offFor] and on again: a new boot, the stopwatch from the start. */
    fun restart(offFor: Long = 0L) {
        wall += offFor
        elapsed = 30_000L
        boot += 1
    }
}
