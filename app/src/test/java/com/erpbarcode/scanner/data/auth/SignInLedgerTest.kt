package com.erpbarcode.scanner.data.auth

import com.erpbarcode.scanner.data.extsystem.ExtSystemODataClient
import com.erpbarcode.scanner.data.extsystem.ExtSystemResult
import com.erpbarcode.scanner.data.extsystem.ntHashOf
import com.erpbarcode.scanner.testing.DAY
import com.erpbarcode.scanner.testing.TestDevice
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetAddress
import java.util.Base64

/**
 * What the ERP's answer does to the saved sign-in: the real client and the real NTLM handshake
 * against a local server that plays the ERP's side of it, with the saved sign-ins kept in a map.
 *
 * A transfer the ERP lets through renews the saved sign-in; one it refuses is held against it and
 * the fifth in a row removes it; and anything that is not the ERP's word on the account (no
 * answer, a server that never asked, a sign-in that is no longer the saved one) changes nothing.
 * Half of these must leave the saved sign-in exactly as it was to pass.
 */
@RunWith(RobolectricTestRunner::class)
class SignInLedgerTest {

    private lateinit var device: TestDevice
    private lateinit var erp: MockWebServer
    private lateinit var client: ExtSystemODataClient
    private lateinit var key: String

    private val signIn = ExtSystemCredentials.fromPassword("tester", "the password")

    @Before
    fun setUp() {
        device = TestDevice()
        key = device.signIn().key
        erp = MockWebServer()
        erp.start(InetAddress.getByName("127.0.0.1"), 0)
        client = device.erpClient()
        device.savedSignIns.save(key, signIn.username, signIn.ntHash)
    }

    @After
    fun tearDown() {
        runCatching { erp.shutdown() }
        device.close()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun address(path: String) = "http://127.0.0.1:${erp.port}$path"

    private fun config() = ExtSystemConfig(serverBaseUrl = address("/"))

    private fun saved(): SavedSignIn? = device.savedSignIns.saved(key)

    /** A Type 2 message: the signature, the type, and enough zeroes for the client to read it. */
    private val challenge: String = Base64.getEncoder().encodeToString(
        ByteArray(48).also {
            "NTLMSSP".toByteArray(Charsets.US_ASCII).copyInto(it)
            it[8] = 2
        }
    )

    /** Which NTLM message a request carries: 1, 3, or null when it carries none. */
    private fun RecordedRequest.ntlmMessage(): Int? =
        getHeader("Authorization")?.removePrefix("NTLM ")?.let { Base64.getDecoder().decode(it)[8].toInt() }

    /**
     * The ERP's side of the handshake: asks who is calling, sends its challenge, and answers the
     * request that carries the proof with [afterProof].
     */
    private fun erpAsksWhoIsCalling(afterProof: () -> MockResponse) {
        erp.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.ntlmMessage()) {
                null -> MockResponse().setResponseCode(401).addHeader("WWW-Authenticate", "NTLM")
                1 -> MockResponse().setResponseCode(401).addHeader("WWW-Authenticate", "NTLM $challenge")
                else -> afterProof()
            }
        }
    }

    private fun letThrough() = MockResponse().setResponseCode(200).setBody("{\"value\":[]}")

    /** What the ERP answers to a proof it does not accept: the request to start over. */
    private fun turnedAway() = MockResponse().setResponseCode(401).addHeader("WWW-Authenticate", "NTLM")

    private suspend fun transfer(creds: ExtSystemCredentials = signIn): ExtSystemResult<String> =
        client.transfer(config(), creds) { it.downloadRaw(address("/odata/Locations")) }

    // ── Accepted ───────────────────────────────────────────────────────────────

    @Test
    fun `a transfer the ERP lets through makes the saved sign-in good as of now`(): Unit = runBlocking {
        device.savedSignIns.recordRefused(key)
        val acceptedAt = saved()!!.verifiedAt
        device.clock.pass(3 * DAY)
        erpAsksWhoIsCalling { letThrough() }

        val result = transfer()

        assertTrue(result is ExtSystemResult.Success)
        // Asked, challenged, proved: one handshake.
        assertEquals(3, erp.requestCount)
        assertEquals(acceptedAt + 3 * DAY, saved()!!.verifiedAt)
        assertEquals(0, saved()!!.failures)
    }

    @Test
    fun `a request the ERP refuses for its own reasons still means it took the sign-in`(): Unit = runBlocking {
        val acceptedAt = saved()!!.verifiedAt
        device.clock.pass(DAY)
        erpAsksWhoIsCalling { MockResponse().setResponseCode(400).setBody("The document is closed.") }

        val result = transfer()

        assertTrue(result is ExtSystemResult.Failure)
        assertEquals(acceptedAt + DAY, saved()!!.verifiedAt)
        assertEquals(0, saved()!!.failures)
    }

    // ── Refused ────────────────────────────────────────────────────────────────

    @Test
    fun `four refused transfers keep the saved sign-in and the fifth removes it`(): Unit = runBlocking {
        erpAsksWhoIsCalling { turnedAway() }

        repeat(4) { transfer() }
        assertEquals(4, saved()!!.failures)

        transfer()
        assertNull(saved())
        // One handshake for each transfer, and nothing after it.
        assertEquals(15, erp.requestCount)
    }

    @Test
    fun `a refused transfer does not move the time the ERP last accepted the sign-in`(): Unit = runBlocking {
        val acceptedAt = saved()!!.verifiedAt
        device.clock.pass(2 * DAY)
        erpAsksWhoIsCalling { turnedAway() }

        val result = transfer()

        assertTrue(result is ExtSystemResult.Failure && result.accountRefused)
        assertEquals(acceptedAt, saved()!!.verifiedAt)
        assertEquals(1, saved()!!.failures)
    }

    @Test
    fun `a transfer the ERP lets through between refusals starts the count again`(): Unit = runBlocking {
        erpAsksWhoIsCalling { turnedAway() }
        repeat(4) { transfer() }

        erpAsksWhoIsCalling { letThrough() }
        transfer()
        assertEquals(0, saved()!!.failures)

        erpAsksWhoIsCalling { turnedAway() }
        repeat(4) { transfer() }
        assertNotNull("four after an acceptance are four, not eight", saved())
    }

    @Test
    fun `a refusal ends the transfer, whatever else it meant to ask for`(): Unit = runBlocking {
        erpAsksWhoIsCalling { turnedAway() }

        val answers = client.transfer(config(), signIn) {
            listOf(it.downloadRaw(address("/odata/Locations")), it.downloadRaw(address("/odata/Documents")))
        }

        assertTrue(answers.all { it is ExtSystemResult.Failure && it.accountRefused })
        assertEquals(3, erp.requestCount)
        // One transfer, one failure: not one for each thing it would have asked.
        assertEquals(1, saved()!!.failures)
    }

    // ── Not the ERP's word on the account: nothing may change ──────────────────

    @Test
    fun `a 401 from a server that never asked for the sign-in is not held against it`(): Unit = runBlocking {
        val before = saved()
        erp.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setResponseCode(401)
        }

        repeat(6) { transfer() }

        assertEquals(before, saved())
    }

    @Test
    fun `an answer from a server that never asked who was calling renews nothing`(): Unit = runBlocking {
        val before = saved()
        device.clock.pass(3 * DAY)
        erp.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = letThrough()
        }

        val result = transfer()

        assertTrue(result is ExtSystemResult.Success)
        assertEquals(before, saved())
    }

    @Test
    fun `no answer at all changes nothing`(): Unit = runBlocking {
        val before = saved()
        val config = config()
        val url = address("/odata/Locations")
        erp.shutdown()

        repeat(6) { client.transfer(config, signIn) { it.downloadRaw(url) } }

        assertEquals(before, saved())
    }

    @Test
    fun `a refusal of a sign-in that is no longer the saved one is not counted`(): Unit = runBlocking {
        // The password was changed and typed anew while a transfer under the old one was running.
        val old = signIn
        device.savedSignIns.save(key, "tester", ntHashOf("the new password"))
        val before = saved()
        erpAsksWhoIsCalling { turnedAway() }

        repeat(6) { transfer(old) }

        assertEquals(before, saved())
    }

    @Test
    fun `an acceptance of a sign-in that is no longer the saved one renews nothing`(): Unit = runBlocking {
        val old = signIn
        device.savedSignIns.save(key, "tester", ntHashOf("the new password"))
        val before = saved()
        device.clock.pass(DAY)
        erpAsksWhoIsCalling { letThrough() }

        transfer(old)

        assertEquals(before, saved())
    }

    @Test
    fun `with nobody signed in nothing is noted for anybody`(): Unit = runBlocking {
        val before = saved()
        device.provider.release()
        erpAsksWhoIsCalling { turnedAway() }

        repeat(6) { transfer() }

        assertEquals(before, saved())
    }
}
