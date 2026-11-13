package com.erpbarcode.scanner.data.db

import com.erpbarcode.scanner.data.auth.UserProfile
import com.erpbarcode.scanner.testing.TestDevice
import com.erpbarcode.scanner.testing.testDocument
import com.erpbarcode.scanner.testing.testLine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The provider of the per-operator databases: one database object per profile, whoever asks and
 * from whichever thread, and no deleting the file somebody is signed in to.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseProviderTest {

    private lateinit var device: TestDevice

    @Before
    fun setUp() {
        device = TestDevice()
    }

    @After
    fun tearDown() {
        device.close()
    }

    @Test
    fun `a profile asked for by many threads at once is opened once`() {
        // Never opened before, so every thread arrives to find nothing there yet.
        val colleague = UserProfile(id = "colleague", displayName = "colleague")
        val threads = 16
        val go = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val asked = List(threads) {
                pool.submit(Callable<AppDatabase> {
                    go.await()
                    device.provider.forProfile(colleague.key)
                })
            }
            go.countDown()
            val opened = asked.map { it.get(30, TimeUnit.SECONDS) }

            // Two objects over one file would each tell only their own screens what they wrote.
            assertTrue("one file, one database object", opened.all { it === opened.first() })
        } finally {
            pool.shutdownNow()
            device.provider.deleteProfileData(colleague.key)
        }
    }

    @Test
    fun `signing in again gives the database that was already open`() {
        device.signIn()
        val first = device.db

        device.signIn("colleague")
        assertNotSame("another operator, another file", first, device.db)

        // Kept open rather than closed on the switch: an upload may still be writing to it.
        device.signIn()
        assertSame(first, device.db)
    }

    @Test
    fun `the data of the signed-in operator cannot be deleted from under them`(): Unit = runBlocking {
        val profile = device.signIn()
        device.download(testDocument("D1", listOf(testLine("D1", 10000, "111", 1.0))))

        device.provider.deleteProfileData(profile.key)

        assertNotNull(device.document("D1"))
    }
}
