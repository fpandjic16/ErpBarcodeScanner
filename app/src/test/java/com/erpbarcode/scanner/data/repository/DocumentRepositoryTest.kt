package com.erpbarcode.scanner.data.repository

import com.erpbarcode.scanner.data.extsystem.toErpRecording
import com.erpbarcode.scanner.data.model.DocState
import com.erpbarcode.scanner.data.model.DocumentType
import com.erpbarcode.scanner.data.model.Line
import com.erpbarcode.scanner.testing.TEST_USER
import com.erpbarcode.scanner.testing.TestDevice
import com.erpbarcode.scanner.testing.testDocument
import com.erpbarcode.scanner.testing.testLine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The repository's rules against a real database: what a download may replace and what it may
 * never touch, where a scan ends up when the ERP changes the document under it, how the document's
 * state follows its recordings, and what can and cannot be deleted.
 *
 * Runs on the JVM under Robolectric, through the same provider, database file and generated DAOs
 * the app uses, so the SQL is tested and not only the Kotlin around it.
 */
@RunWith(RobolectricTestRunner::class)
class DocumentRepositoryTest {

    private lateinit var device: TestDevice
    private val repository: DocumentRepository get() = device.repository

    private val shipment = DocumentType.WAREHOUSE_SHIPMENT.key
    private val inventory = DocumentType.INVENTORY

    @Before
    fun signIn() {
        device = TestDevice()
        device.signIn()
    }

    @After
    fun close() = device.close()

    /** D1 as the ERP first sends it: line 10000 expects two of barcode 111, line 20000 one of 222. */
    private fun d1(
        lines: List<Line> = listOf(testLine("D1", 10000, "111", 2.0), testLine("D1", 20000, "222", 1.0)),
    ) = testDocument("D1", lines)

    private fun count(documentNo: String = "INV1") =
        testDocument(documentNo, listOf(testLine(documentNo, 10000, "111", 5.0)), type = inventory)

    // ── Download and merge ─────────────────────────────────────────────────────

    @Test
    fun `a scan survives the document being downloaded again`(): Unit = runBlocking {
        device.download(d1())
        device.scan("D1", 10000, "111")
        val before = device.recordings("D1").single()

        device.download(d1())

        // The whole row, not only its quantity: the same identity, time and operator.
        assertEquals(before, device.recordings("D1").single())
        assertEquals(1.0, device.document("D1")!!.lines.first { it.lineNo == 10000 }.scanned, 0.0)
        assertEquals(DocState.InProgress, device.state("D1"))
    }

    @Test
    fun `a renumbered line takes its scans with it`(): Unit = runBlocking {
        device.download(d1())
        device.scan("D1", 10000, "111")
        val before = device.recordings("D1").single()

        // The ERP now carries barcode 111 on line 15000.
        device.download(d1(listOf(testLine("D1", 15000, "111", 2.0), testLine("D1", 20000, "222", 1.0))))

        val after = device.recordings("D1").single()
        assertEquals(15000, after.documentLine)
        assertEquals(before.recordingGuid, after.recordingGuid)
        assertTrue(repository.getOrphanedRecordings("D1", shipment).isEmpty())
        assertEquals(1, repository.getQueuedRecordings("D1", shipment).size)
        assertEquals(1.0, device.document("D1")!!.lines.first { it.lineNo == 15000 }.scanned, 0.0)
    }

    @Test
    fun `a scan whose line the ERP removed becomes an orphan`(): Unit = runBlocking {
        device.download(d1())
        device.scan("D1", 10000, "111")
        val before = device.recordings("D1").single()

        // Line 10000 is gone and no other line carries its barcode.
        device.download(d1(listOf(testLine("D1", 20000, "222", 1.0))))

        assertEquals(before, device.recordings("D1").single())
        assertEquals(listOf(before), repository.getOrphanedRecordings("D1", shipment))
        assertTrue(repository.getQueuedRecordings("D1", shipment).isEmpty())
        val document = device.document("D1")!!
        assertEquals(1, document.orphanedScans.size)
        assertTrue(document.needsReview)
    }

    @Test
    fun `discarding orphans removes them and nothing else`(): Unit = runBlocking {
        device.download(d1())
        device.scan("D1", 10000, "111")
        device.scan("D1", 20000, "222")
        device.download(d1(listOf(testLine("D1", 20000, "222", 1.0))))

        repository.discardOrphanedScans("D1", shipment)

        assertEquals(20000, device.recordings("D1").single().documentLine)
        assertFalse(device.document("D1")!!.needsReview)
        // Recomputed from what is left: the one remaining line is scanned exactly.
        assertEquals(DocState.Completed, device.state("D1"))
    }

    @Test
    fun `a download removes a missing document only when it holds no scan`(): Unit = runBlocking {
        val receipt = DocumentType.WAREHOUSE_RECEIPT
        device.download(d1(), testDocument("D2", listOf(testLine("D2", 10000, "333", 1.0))))
        device.download(testDocument("R1", listOf(testLine("R1", 10000, "444", 1.0)), type = receipt), type = receipt)
        device.scan("D1", 10000, "111")

        // The ERP no longer lists any shipment.
        device.download()

        assertNotNull("a document with a scan stays", device.document("D1"))
        assertEquals(1, device.recordings("D1").size)
        assertNull("an untouched document goes", device.document("D2"))
        assertNotNull("another type is not this download's business", device.document("R1", receipt))
    }

    @Test
    fun `a stock count download keeps the batches it did not ask for`(): Unit = runBlocking {
        device.download(count("INV1"), type = inventory, pruneMissing = false)

        device.download(count("INV2"), type = inventory, pruneMissing = false)

        assertNotNull(device.document("INV1", inventory))
        assertNotNull(device.document("INV2", inventory))
    }

    // ── State follows the recordings ───────────────────────────────────────────

    @Test
    fun `the first scan starts a downloaded document`(): Unit = runBlocking {
        device.download(d1())
        assertEquals(DocState.Downloaded, device.state("D1"))

        device.scan("D1", 10000, "111")

        assertEquals(DocState.InProgress, device.state("D1"))
    }

    @Test
    fun `an exact document is completed by a download and reopened by a scan`(): Unit = runBlocking {
        device.download(d1())
        device.scan("D1", 10000, "111", quantity = 2.0)
        device.scan("D1", 20000, "222")

        device.download(d1())
        assertEquals(DocState.Completed, device.state("D1"))

        device.scan("D1", 20000, "222")
        assertEquals(DocState.InProgress, device.state("D1"))
    }

    @Test
    fun `a pending upload left behind by a dead process is reset`(): Unit = runBlocking {
        device.download(d1(), testDocument("D2", listOf(testLine("D2", 10000, "333", 1.0))))
        device.scan("D1", 10000, "111")
        repository.updateDocState("D1", shipment, DocState.PendingUpload)
        repository.updateDocState("D2", shipment, DocState.PendingUpload)

        repository.recoverStalePendingUploads()

        assertEquals(DocState.InProgress, device.state("D1"))
        assertEquals(DocState.Downloaded, device.state("D2"))
        assertEquals("recovering never deletes", 1, device.recordings("D1").size)
    }

    // ── Editing a quantity by hand ─────────────────────────────────────────────

    @Test
    fun `lowering a quantity undoes the newest scans first`(): Unit = runBlocking {
        device.download(d1())
        device.scan("D1", 10000, "111", quantity = 1.0)
        device.scan("D1", 10000, "111", quantity = 2.0)
        device.scan("D1", 10000, "111", quantity = 3.0)
        val (first, second, _) = device.recordings("D1")

        val result = repository.setLineScanned("D1", shipment, 10000, 2.5, TEST_USER)

        assertEquals(SetQuantityResult.Applied, result)
        val left = device.recordings("D1")
        assertEquals(2, left.size)
        assertEquals("the oldest scan is untouched", first, left[0])
        // The middle one is trimmed, and stays the same scan: its identity is kept.
        assertEquals(second.copy(quantity = 1.5), left[1])
    }

    @Test
    fun `raising a quantity adds one scan and leaves the others alone`(): Unit = runBlocking {
        device.download(d1())
        device.scan("D1", 10000, "111", quantity = 1.0)
        device.scan("D1", 10000, "111", quantity = 2.0)
        val before = device.recordings("D1")

        val result = repository.setLineScanned("D1", shipment, 10000, 5.0, TEST_USER)

        assertEquals(SetQuantityResult.Applied, result)
        val after = device.recordings("D1")
        assertEquals(before, after.take(2))
        assertEquals(2.0, after[2].quantity, 0.0)
        assertEquals(3, after[2].recordingLineNo)
        assertTrue(after[2].recordingGuid.isNotBlank())
        assertNotEquals(before[0].recordingGuid, after[2].recordingGuid)
        assertNotEquals(before[1].recordingGuid, after[2].recordingGuid)
    }

    @Test
    fun `a quantity cannot be set below what the ERP already holds`(): Unit = runBlocking {
        device.download(d1())
        device.scan("D1", 10000, "111", quantity = 2.0)
        repository.markRecordingSent("D1", shipment, 10000, 1)
        device.scan("D1", 10000, "111", quantity = 1.0)
        val before = device.recordings("D1")

        // Refused, and nothing moves: not clamped to the floor behind the operator's back.
        assertEquals(SetQuantityResult.BelowSent(2.0), repository.setLineScanned("D1", shipment, 10000, 1.0, TEST_USER))
        assertEquals(before, device.recordings("D1"))

        // Down to the floor is allowed, and takes only the queued scan.
        assertEquals(SetQuantityResult.Applied, repository.setLineScanned("D1", shipment, 10000, 2.0, TEST_USER))
        assertEquals(listOf(before[0]), device.recordings("D1"))
    }

    @Test
    fun `taking back the only scan returns the document to downloaded`(): Unit = runBlocking {
        device.download(d1())
        device.scan("D1", 10000, "111")

        repository.setLineScanned("D1", shipment, 10000, 0.0, TEST_USER)

        assertTrue(device.recordings("D1").isEmpty())
        assertEquals(DocState.Downloaded, device.state("D1"))
    }

    // ── What may be deleted ────────────────────────────────────────────────────

    @Test
    fun `a queued scan can be deleted, and the state follows`(): Unit = runBlocking {
        device.download(d1())
        device.scan("D1", 10000, "111")

        repository.deleteQueuedRecording("D1", shipment, 10000, 1)

        assertTrue(device.recordings("D1").isEmpty())
        assertEquals(DocState.Downloaded, device.state("D1"))
    }

    @Test
    fun `a scan the ERP has accepted cannot be deleted`(): Unit = runBlocking {
        device.download(d1())
        device.scan("D1", 10000, "111")
        device.scan("D1", 10000, "111")
        repository.markRecordingSent("D1", shipment, 10000, 1)

        // One by one: refused.
        repository.deleteQueuedRecording("D1", shipment, 10000, 1)
        assertEquals(2, device.recordings("D1").size)

        // Clearing the document: takes the queued scan and leaves the accepted one.
        repository.deleteDocumentRecordings("D1", shipment)
        val left = device.recordings("D1").single()
        assertEquals(1, left.recordingLineNo)
        assertNotNull(left.sentAt)
        assertEquals(DocState.InProgress, device.state("D1"))
    }

    @Test
    fun `discarding refused scans leaves the untried and the accepted ones`(): Unit = runBlocking {
        device.download(d1())
        repeat(3) { device.scan("D1", 10000, "111") }
        // Scan 1 was accepted, and then an error was written against it all the same.
        repository.markRecordingSent("D1", shipment, 10000, 1)
        repository.recordRecordingFailure("D1", shipment, 10000, 1, "already exists")
        // Scan 2 was refused. Scan 3 has not been tried.
        repository.recordRecordingFailure("D1", shipment, 10000, 2, "HTTP 400")

        repository.discardFailedScans("D1", shipment)

        assertEquals(listOf(1, 3), device.recordings("D1").map { it.recordingLineNo })
    }

    // ── Removing documents ─────────────────────────────────────────────────────

    @Test
    fun `a document sent in full is removed with its lines and scans`(): Unit = runBlocking {
        device.download(d1())
        device.scan("D1", 10000, "111")
        repository.markRecordingSent("D1", shipment, 10000, 1)

        assertTrue(repository.deleteDocumentIfNothingUnsent("D1", shipment))

        assertNull(device.document("D1"))
        assertTrue(device.db.documentLineDao().getAll().isEmpty())
        assertTrue(device.db.recordingDao().getAll().isEmpty())
    }

    @Test
    fun `a document with a scan still queued is not removed`(): Unit = runBlocking {
        device.download(d1())
        device.scan("D1", 10000, "111")
        device.scan("D1", 10000, "111")
        repository.markRecordingSent("D1", shipment, 10000, 1)
        // What a background upload leaves on the document while it runs.
        repository.updateDocState("D1", shipment, DocState.PendingUpload)

        assertFalse(repository.deleteDocumentIfNothingUnsent("D1", shipment))

        assertEquals(2, device.recordings("D1").size)
        // And it is not left "being sent": line 20000 has nothing yet.
        assertEquals(DocState.InProgress, device.state("D1"))
    }

    @Test
    fun `a document whose unsent scan lost its line is not removed`(): Unit = runBlocking {
        device.download(d1())
        device.scan("D1", 20000, "222")
        // The ERP drops line 20000: the scan has nowhere to go, and has not been sent.
        device.download(d1(lines = listOf(testLine("D1", 10000, "111", 2.0))))

        assertFalse(repository.deleteDocumentIfNothingUnsent("D1", shipment))

        assertEquals(1, device.recordings("D1").size)
    }

    @Test
    fun `deleting every document keeps those with an unsent scan`(): Unit = runBlocking {
        device.download(
            d1(),
            testDocument("D2", listOf(testLine("D2", 10000, "111", 1.0))),
            testDocument("D3", listOf(testLine("D3", 10000, "111", 1.0))),
        )
        // D1 has a scan waiting, D2 has nothing scanned, D3 was sent in full.
        device.scan("D1", 10000, "111")
        device.scan("D3", 10000, "111")
        repository.markRecordingSent("D3", shipment, 10000, 1)

        assertEquals(1, repository.deleteDocumentsWithNothingUnsent())

        assertNotNull(device.document("D1"))
        assertNull(device.document("D2"))
        assertNull(device.document("D3"))
        assertEquals(listOf("D1"), device.db.recordingDao().getAll().map { it.documentNo })

        // Once the operator has deleted the scan, the document can go as well.
        repository.deleteDocumentRecordings("D1", shipment)
        assertEquals(0, repository.deleteDocumentsWithNothingUnsent())
        assertNull(device.document("D1"))
    }

    // ── Stock count: lines added on the device ─────────────────────────────────

    @Test
    fun `an unlisted item gets a line of the device's own`(): Unit = runBlocking {
        device.download(count(), type = inventory, pruneMissing = false)

        repository.recordScanNotOnDocument("INV1", inventory.key, "999", "KOM", TEST_USER, 1.0)
        repository.recordScanNotOnDocument("INV1", inventory.key, "999", "KOM", TEST_USER, 1.0)
        repository.recordScanNotOnDocument("INV1", inventory.key, "888", "KOM", TEST_USER, 2.0)

        val lines = device.document("INV1", inventory)!!.lines
        assertEquals(listOf(-2, -1, 10000), lines.map { it.lineNo })
        val added = lines.first { it.lineNo == -1 }
        assertTrue(added.isAddedOnDevice)
        assertEquals("999", added.barcodeNo)
        assertEquals("999", added.item.no)
        assertEquals(0.0, added.expected, 0.0)
        // The second scan of 999 landed on the line the first one created.
        assertEquals(2.0, added.scanned, 0.0)
        assertTrue(lines.first { it.lineNo == -2 }.isAddedOnDevice)
        assertFalse(lines.first { it.lineNo == 10000 }.isAddedOnDevice)

        // Its own number means nothing to the ERP, so it is sent as line 0.
        val scan = device.recordings("INV1", inventory).first { it.documentLine == -1 }
        assertEquals(0, scan.toErpRecording("INVENTORY", null).documentLineNo)
    }

    @Test
    fun `a device line lasts until the ERP sends its barcode`(): Unit = runBlocking {
        device.download(count(), type = inventory, pruneMissing = false)
        repository.recordScanNotOnDocument("INV1", inventory.key, "999", "KOM", TEST_USER, 1.0)
        repository.recordScanNotOnDocument("INV1", inventory.key, "888", "KOM", TEST_USER, 1.0)
        val guid999 = device.recordings("INV1", inventory).first { it.barcodeNo == "999" }.recordingGuid

        // The same count again: the ERP knows neither item yet.
        device.download(count(), type = inventory, pruneMissing = false)
        assertEquals(listOf(-2, -1, 10000), device.document("INV1", inventory)!!.lines.map { it.lineNo })

        // The ERP has processed 999 and now lists it on line 20000.
        device.download(
            testDocument(
                "INV1",
                listOf(testLine("INV1", 10000, "111", 5.0), testLine("INV1", 20000, "999", 0.0)),
                type = inventory,
            ),
            type = inventory,
            pruneMissing = false,
        )

        val document = device.document("INV1", inventory)!!
        assertEquals(listOf(-2, 10000, 20000), document.lines.map { it.lineNo })
        assertEquals(1.0, document.lines.first { it.lineNo == 20000 }.scanned, 0.0)
        assertTrue(document.orphanedScans.isEmpty())
        val moved = device.recordings("INV1", inventory).first { it.barcodeNo == "999" }
        assertEquals(20000, moved.documentLine)
        assertEquals(guid999, moved.recordingGuid)
    }

    @Test
    fun `a device line goes when its last scan is taken back`(): Unit = runBlocking {
        device.download(count(), type = inventory, pruneMissing = false)
        repository.recordScanNotOnDocument("INV1", inventory.key, "999", "KOM", TEST_USER, 1.0)

        repository.setLineScanned("INV1", inventory.key, -1, 0.0, TEST_USER)

        assertEquals(listOf(10000), device.document("INV1", inventory)!!.lines.map { it.lineNo })
        assertEquals(DocState.Downloaded, device.state("INV1", inventory))
    }

    // ── One file per operator ──────────────────────────────────────────────────

    @Test
    fun `each operator has the same document to themselves`(): Unit = runBlocking {
        device.signIn("ana")
        device.download(d1())
        device.scan("D1", 10000, "111")

        // The repository is the same object; only who is signed in has changed.
        device.signIn("bruno")
        assertNull(device.document("D1"))
        device.download(d1())
        assertTrue(device.recordings("D1").isEmpty())

        device.signIn("ana")
        assertEquals(1, device.recordings("D1").size)
    }
}
