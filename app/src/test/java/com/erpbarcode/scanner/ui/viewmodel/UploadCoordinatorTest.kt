package com.erpbarcode.scanner.ui.viewmodel

import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.data.auth.ExtSystemConfig
import com.erpbarcode.scanner.data.auth.ExtSystemCredentials
import com.erpbarcode.scanner.data.auth.RecordingUploadMode
import com.erpbarcode.scanner.data.extsystem.DocumentUploadSoap
import com.erpbarcode.scanner.data.extsystem.ExtSystemODataClient
import com.erpbarcode.scanner.data.model.DocState
import com.erpbarcode.scanner.data.model.DocumentType
import com.erpbarcode.scanner.i18n.inAppLanguage
import com.erpbarcode.scanner.testing.TestDevice
import com.erpbarcode.scanner.testing.testDocument
import com.erpbarcode.scanner.testing.testLine
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The upload loop from end to end: a real database, the real repository and the real ERP client,
 * talking HTTP to a local server that answers as the ERP would. What is checked is what leaves the
 * device, in which order, and what the device believes afterwards.
 *
 * The server never asks for authentication, so the sign-in handshake is not part of these tests.
 */
@RunWith(RobolectricTestRunner::class)
class UploadCoordinatorTest {

    private lateinit var device: TestDevice
    private lateinit var erp: MockWebServer
    private lateinit var client: ExtSystemODataClient
    private lateinit var coordinator: UploadCoordinator

    private val credentials = ExtSystemCredentials("tester", "not-a-real-password")
    private val shipment = DocumentType.WAREHOUSE_SHIPMENT

    @Before
    fun setUp() {
        device = TestDevice()
        device.signIn()
        erp = MockWebServer()
        erp.start(InetAddress.getByName("127.0.0.1"), 0)
        client = ExtSystemODataClient(device.context)
        coordinator = UploadCoordinator(device.context, device.repository, client)
    }

    @After
    fun tearDown() {
        client.close()
        runCatching { erp.shutdown() }
        device.close()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun address(path: String) = "http://127.0.0.1:${erp.port}$path"

    private fun perScan() = ExtSystemConfig(
        serverBaseUrl = address("/"),
        recordingSyncUrl = address("/odata/Recordings"),
        documentTypeCodes = mapOf(
            DocumentType.WAREHOUSE_SHIPMENT to "SHIPMENT",
            DocumentType.RETAIL_SHIPMENT to "SHIPMENT",
            DocumentType.TRANSPORT_SHEET to "TRANSPORT",
        ),
    )

    private fun perDocument() = perScan().copy(
        uploadMode = RecordingUploadMode.PER_DOCUMENT,
        documentUploadUrl = address("/WS/Company/Codeunit/BarcodeAppUpload"),
    )

    /** In the app's language, the way the coordinator builds its own messages. */
    private fun text(id: Int, vararg args: Any): String = device.context.inAppLanguage().getString(id, *args)

    private fun accepted() = MockResponse().setResponseCode(201).setBody("{}")
    private fun refused(why: String) = MockResponse().setResponseCode(400).setBody(why)

    private fun nextRequest(): RecordedRequest =
        erp.takeRequest(5, TimeUnit.SECONDS) ?: error("The server received no further request")

    private fun RecordedRequest.json(): JsonObject = JsonParser.parseString(body.readUtf8()).asJsonObject

    /** D1 with [scans] scans on line 10000, in the order they were made. */
    private suspend fun d1WithScans(scans: Int) {
        device.download(testDocument("D1", listOf(testLine("D1", 10000, "111", 2.0), testLine("D1", 20000, "222", 1.0))))
        repeat(scans) { device.scan("D1", 10000, "111") }
    }

    private suspend fun upload(vararg documentNos: String, config: ExtSystemConfig = perScan()): Int =
        coordinator.runUpload(documentNos.map { device.document(it)!! }, config, credentials)

    // ── One request per scan ───────────────────────────────────────────────────

    @Test
    fun `per scan, accepted rows go in order and the document is removed`(): Unit = runBlocking {
        d1WithScans(0)
        // Scanned out of line order on purpose.
        device.scan("D1", 20000, "222")
        device.scan("D1", 10000, "111")
        device.scan("D1", 10000, "111")
        val stored = device.recordings("D1")
        repeat(3) { erp.enqueue(accepted()) }

        val failures = upload("D1")

        assertEquals(0, failures)
        assertEquals(3, erp.requestCount)
        val requests = List(3) { nextRequest() }
        assertTrue(requests.all { it.method == "POST" && it.path == "/odata/Recordings" })
        val rows = requests.map { it.json() }
        // By line, then in the order scanned: not in whatever order the database returns them.
        assertEquals(listOf(10000, 10000, 20000), rows.map { it["Document_Line_No"].asInt })
        assertEquals(listOf(1, 2, 1), rows.map { it["Recording_Line_No"].asInt })
        assertEquals(stored.map { it.recordingGuid }, rows.map { it["Recording_Guid"].asString })
        assertEquals("SHIPMENT", rows[0]["Document_Type"].asString)
        assertFalse("a warehouse document says so", rows[0]["Retail_Location"].asBoolean)
        assertNull(device.document("D1"))
    }

    @Test
    fun `per scan, a refused row does not stop the others`(): Unit = runBlocking {
        d1WithScans(3)
        erp.enqueue(accepted())
        erp.enqueue(refused("Field too long"))
        erp.enqueue(accepted())

        val failures = upload("D1")

        assertEquals(1, failures)
        assertEquals(3, erp.requestCount)
        val (first, second, third) = device.recordings("D1")
        assertNotNull(first.sentAt)
        assertNull(second.sentAt)
        assertTrue(second.lastError.orEmpty().contains("Field too long"))
        assertNotNull(third.sentAt)
        assertEquals(DocState.UploadFailed(text(R.string.upload_error_rows_failed, 1, 3)), device.state("D1"))
        val document = device.document("D1")!!
        assertEquals(2, document.sentScans)
        assertEquals(1, document.pendingScans)
        assertEquals(1, document.failedScans.size)
    }

    @Test
    fun `a retry sends only what is still queued, with the same identity`(): Unit = runBlocking {
        d1WithScans(3)
        erp.enqueue(accepted())
        erp.enqueue(refused("Field too long"))
        erp.enqueue(accepted())
        upload("D1")
        val stuck = device.recordings("D1")[1]
        repeat(3) { nextRequest() }

        erp.enqueue(accepted())
        val failures = upload("D1")

        assertEquals(0, failures)
        assertEquals(4, erp.requestCount)
        val retried = nextRequest().json()
        assertEquals(2, retried["Recording_Line_No"].asInt)
        assertEquals(stuck.recordingGuid, retried["Recording_Guid"].asString)
        assertNull(device.document("D1"))
    }

    @Test
    fun `per scan, no answer from the server stops at the first row`(): Unit = runBlocking {
        d1WithScans(3)
        val config = perScan()
        erp.shutdown()

        val failures = upload("D1", config = config)

        assertEquals(1, failures)
        val (first, second, third) = device.recordings("D1")
        assertNotNull(first.lastError)
        // The rest were not tried: each would have cost a connect timeout to learn the same thing.
        assertNull(second.lastError)
        assertNull(third.lastError)
        assertTrue(device.recordings("D1").all { it.sentAt == null })
        assertEquals(DocState.UploadFailed(first.lastError!!), device.state("D1"))
    }

    @Test
    fun `one document failing does not hold back the next`(): Unit = runBlocking {
        device.download(
            testDocument("D1", listOf(testLine("D1", 10000, "111", 2.0))),
            testDocument("D2", listOf(testLine("D2", 10000, "333", 1.0))),
        )
        device.scan("D1", 10000, "111")
        device.scan("D2", 10000, "333")
        erp.enqueue(refused("Document is closed"))
        erp.enqueue(accepted())

        val failures = upload("D1", "D2")

        assertEquals(1, failures)
        assertTrue(device.state("D1") is DocState.UploadFailed)
        assertNull(device.document("D2"))
    }

    @Test
    fun `only a retail or warehouse document carries the retail flag`(): Unit = runBlocking {
        val sheet = DocumentType.TRANSPORT_SHEET
        val retail = DocumentType.RETAIL_SHIPMENT
        device.download(testDocument("T1", listOf(testLine("T1", 10000, "111", 1.0)), type = sheet), type = sheet)
        device.scan("T1", 10000, "111", type = sheet)
        device.download(
            testDocument("S1", listOf(testLine("S1", 10000, "111", 1.0)), type = retail, isSourceRetail = true),
            type = retail,
        )
        device.scan("S1", 10000, "111", type = retail)
        repeat(2) { erp.enqueue(accepted()) }

        val failures = coordinator.runUpload(
            listOf(device.document("T1", sheet)!!, device.document("S1", retail)!!), perScan(), credentials,
        )

        assertEquals(0, failures)
        val transport = nextRequest().json()
        assertEquals("TRANSPORT", transport["Document_Type"].asString)
        assertFalse(transport.has("Retail_Location"))
        val shop = nextRequest().json()
        assertEquals("SHIPMENT", shop["Document_Type"].asString)
        assertTrue(shop["Retail_Location"].asBoolean)
    }

    // ── What is never sent, and what is never deleted ──────────────────────────

    @Test
    fun `a document holding orphaned scans is not sent at all`(): Unit = runBlocking {
        d1WithScans(1)
        device.scan("D1", 20000, "222")
        // The ERP has removed line 10000 since.
        device.download(testDocument("D1", listOf(testLine("D1", 20000, "222", 1.0))))

        val failures = upload("D1")

        assertEquals(1, failures)
        // Not even the scan on the line that is still there: all of a document or none of it.
        assertEquals(0, erp.requestCount)
        assertEquals(DocState.UploadFailed(text(R.string.upload_error_needs_review, 1)), device.state("D1"))
        assertEquals(2, device.recordings("D1").size)
        assertTrue(device.recordings("D1").all { it.sentAt == null })
    }

    @Test
    fun `a downloaded document with no scans is left alone`(): Unit = runBlocking {
        d1WithScans(0)

        val failures = upload("D1")

        assertEquals(0, failures)
        assertEquals(0, erp.requestCount)
        assertEquals(DocState.Downloaded, device.state("D1"))
    }

    @Test
    fun `a document whose scans were all accepted earlier is removed`(): Unit = runBlocking {
        d1WithScans(1)
        device.repository.markRecordingSent("D1", shipment.key, 10000, 1)

        val failures = upload("D1")

        assertEquals(0, failures)
        assertEquals(0, erp.requestCount)
        assertNull(device.document("D1"))
    }

    @Test
    fun `nothing is sent without a server, a sign-in or an upload address`(): Unit = runBlocking {
        d1WithScans(1)
        val documents = listOf(device.document("D1")!!)

        assertEquals(1, coordinator.runUpload(documents, ExtSystemConfig(), credentials))
        assertEquals(DocState.UploadFailed(text(R.string.upload_error_not_configured)), device.state("D1"))

        assertEquals(1, coordinator.runUpload(documents, perScan(), null))
        assertEquals(DocState.UploadFailed(text(R.string.upload_error_not_configured)), device.state("D1"))

        assertEquals(1, coordinator.runUpload(documents, perScan().copy(recordingSyncUrl = ""), credentials))
        assertEquals(DocState.UploadFailed(text(R.string.upload_error_sync_url_missing)), device.state("D1"))

        assertEquals(1, coordinator.runUpload(documents, perDocument().copy(documentUploadUrl = ""), credentials))
        assertEquals(DocState.UploadFailed(text(R.string.upload_error_document_url_missing)), device.state("D1"))

        assertEquals(0, erp.requestCount)
        assertNull(device.recordings("D1").single().sentAt)
    }

    // ── One request per document ───────────────────────────────────────────────

    @Test
    fun `per document, one call carries every row and the document is removed`(): Unit = runBlocking {
        d1WithScans(3)
        val stored = device.recordings("D1")
        erp.enqueue(MockResponse().setResponseCode(200).setBody("<ok/>"))

        val failures = upload("D1", config = perDocument())

        assertEquals(0, failures)
        assertEquals(1, erp.requestCount)
        val request = nextRequest()
        assertEquals("/WS/Company/Codeunit/BarcodeAppUpload", request.path)
        assertEquals(DocumentUploadSoap.SOAP_ACTION, request.getHeader("SOAPAction"))
        val envelope = request.body.readUtf8()
        stored.forEach { assertTrue(envelope.contains("{${it.recordingGuid}}")) }
        assertNull(device.document("D1"))
    }

    @Test
    fun `per document, a fault leaves every row queued with its reason`(): Unit = runBlocking {
        d1WithScans(3)
        val fault = "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><s:Fault>" +
            "<faultcode>a:Error</faultcode><faultstring xml:lang=\"en-US\">The document is closed.</faultstring>" +
            "</s:Fault></s:Body></s:Envelope>"
        erp.enqueue(MockResponse().setResponseCode(500).setBody(fault))

        val failures = upload("D1", config = perDocument())

        assertEquals(1, failures)
        assertEquals(1, erp.requestCount)
        val rows = device.recordings("D1")
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.sentAt == null && it.lastError == "The document is closed." })
        assertEquals(DocState.UploadFailed("The document is closed."), device.state("D1"))
    }

    // ── A scan made while its document is on its way ───────────────────────────

    /**
     * The ERP accepts everything, and while it is serving the first request the operator scans
     * line 20000 of D1: a scan the upload did not read and is not sending.
     */
    private fun acceptWithAScanDuringTheFirstRequest(response: () -> MockResponse) {
        val first = AtomicBoolean(true)
        erp.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (first.getAndSet(false)) runBlocking { device.scan("D1", 20000, "222") }
                return response()
            }
        }
    }

    @Test
    fun `per scan, a scan made during the upload keeps the document`(): Unit = runBlocking {
        d1WithScans(2)
        acceptWithAScanDuringTheFirstRequest { accepted() }

        val failures = upload("D1")

        assertEquals(0, failures)
        // Only the two rows that were queued when the upload began went out.
        assertEquals(2, erp.requestCount)
        val (first, second, late) = device.recordings("D1")
        assertNotNull(first.sentAt)
        assertNotNull(second.sentAt)
        assertEquals(20000, late.documentLine)
        assertNull(late.sentAt)
        // Every line is exact, counting what was sent and what was not.
        assertEquals(DocState.Completed, device.state("D1"))

        // The next upload sends the late scan alone, and then the document goes.
        assertEquals(0, upload("D1"))
        assertEquals(3, erp.requestCount)
        assertEquals(20000, List(3) { nextRequest() }.last().json()["Document_Line_No"].asInt)
        assertNull(device.document("D1"))
    }

    @Test
    fun `per document, a scan made during the upload keeps the document`(): Unit = runBlocking {
        d1WithScans(2)
        acceptWithAScanDuringTheFirstRequest { MockResponse().setResponseCode(200).setBody("<ok/>") }

        val failures = upload("D1", config = perDocument())

        assertEquals(0, failures)
        assertEquals(1, erp.requestCount)
        val rows = device.recordings("D1")
        assertEquals(3, rows.size)
        assertEquals(listOf(20000), rows.filter { it.sentAt == null }.map { it.documentLine })
        assertNotNull(device.document("D1"))
    }

    // ── Two uploads at once ────────────────────────────────────────────────────

    @Test
    fun `two uploads of one document at once send each row once`(): Unit = runBlocking {
        d1WithScans(2)
        val stored = device.recordings("D1")
        // Both are handed the document as the list showed it when Upload was pressed.
        val asListed = listOf(device.document("D1")!!)
        // Slow enough that the second upload starts while the first is still sending.
        erp.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                accepted().setBodyDelay(200, TimeUnit.MILLISECONDS)
        }

        val failures = List(2) {
            async(Dispatchers.IO) { coordinator.runUpload(asListed, perScan(), credentials) }
        }.awaitAll()

        // The second waited, found the document gone, and had nothing to send or to report.
        assertEquals(listOf(0, 0), failures)
        assertEquals(2, erp.requestCount)
        val sent = List(2) { nextRequest().json()["Recording_Guid"].asString }
        assertEquals(stored.map { it.recordingGuid }, sent)
        assertNull(device.document("D1"))
    }
}
