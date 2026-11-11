package com.erpbarcode.scanner.navigation

import com.erpbarcode.scanner.data.model.DocState
import com.erpbarcode.scanner.data.model.DocTypeFilterMode
import com.erpbarcode.scanner.data.model.Document
import com.erpbarcode.scanner.data.model.DocumentType
import com.erpbarcode.scanner.data.model.Item
import com.erpbarcode.scanner.data.model.Line
import com.erpbarcode.scanner.data.model.LineStatus
import com.erpbarcode.scanner.data.model.Location
import com.erpbarcode.scanner.data.model.OrphanedScan
import com.erpbarcode.scanner.data.model.ResponsibilityCenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ShellDataTest {

    private val here = Location("CS165", "Store 165", "RC1")
    private val centre = ResponsibilityCenter("RC1", "Centre 1")

    private fun line(expected: Double, scanned: Double) = Line(
        documentNo = "D", lineNo = 10000, item = Item("I", "Item"), barcodeNo = "B",
        expected = expected, scanned = scanned, destinationCode = "", sourceCode = "", unitOfMeasureCode = "KOM",
    )

    private fun doc(
        no: String,
        type: DocumentType = DocumentType.WAREHOUSE_SHIPMENT,
        source: String = "CS165",
        rc: String = "RC1",
        lines: List<Line> = listOf(line(1.0, 0.0)),
        state: DocState = DocState.Downloaded,
        scans: Int = 0,
        date: Instant = Instant.parse("2026-11-01T00:00:00Z"),
        orphaned: List<OrphanedScan> = emptyList(),
    ) = Document(
        documentNo = no, type = type, destinationCode = "", sourceCode = source, rcCode = rc,
        creationDateTime = date, documentDate = date, lines = lines, state = state,
        orphanedScans = orphaned, pendingScans = scans,
    )

    private fun data(
        documents: List<Document>,
        location: Location? = here,
        rc: ResponsibilityCenter? = centre,
        filters: Map<String, DocTypeFilterMode> = emptyMap(),
        disabled: Set<String> = emptySet(),
    ) = shellData(documents, location, rc, filters, disabled)

    @Test
    fun `a location-scoped type shows only the selected location, started or not`() {
        val elsewhere = doc("B", source = "CS175", lines = listOf(line(2.0, 1.0)), scans = 1)
        val d = data(listOf(doc("A"), elsewhere))
        assertEquals(listOf("A"), d.filteredDocs.map { it.documentNo })
    }

    @Test
    fun `nothing selected shows nothing and blocks the type`() {
        val d = data(listOf(doc("A")), location = null)
        assertTrue(d.filteredDocs.isEmpty())
        assertTrue(d.docTypes.first { it.type == DocumentType.WAREHOUSE_SHIPMENT }.blocked)
    }

    @Test
    fun `a type scoped by responsibility centre needs the centre, not the location`() {
        val complaint = doc("C", type = DocumentType.COMPLAINT_SHIPMENT, source = "CS999", rc = "RC1")
        assertEquals(listOf("C"), data(listOf(complaint)).filteredDocs.map { it.documentNo })
        val none = data(listOf(complaint), rc = null)
        assertTrue(none.filteredDocs.isEmpty())
        assertTrue(none.docTypes.first { it.type == DocumentType.COMPLAINT_SHIPMENT }.blocked)
        assertFalse(none.docTypes.first { it.type == DocumentType.WAREHOUSE_SHIPMENT }.blocked)
    }

    @Test
    fun `the scope of a type can be changed in the configuration`() {
        val shipment = doc("A", source = "CS999", rc = "RC1")
        val byCentre = mapOf(DocumentType.WAREHOUSE_SHIPMENT.key to DocTypeFilterMode.RESPONSIBILITY_CENTER)
        assertEquals(1, data(listOf(shipment), filters = byCentre).filteredDocs.size)
        assertEquals(0, data(listOf(shipment)).filteredDocs.size)
    }

    @Test
    fun `a switched-off type has no summary but keeps its recordings`() {
        val scanned = doc("A", lines = listOf(line(2.0, 1.0)), scans = 1, state = DocState.InProgress)
        val d = data(listOf(scanned), disabled = setOf(DocumentType.WAREHOUSE_SHIPMENT.key))
        assertTrue(d.docTypes.none { it.type == DocumentType.WAREHOUSE_SHIPMENT })
        assertEquals(listOf("A"), d.recordedDocs.map { it.documentNo })
    }

    @Test
    fun `recordings list every document with a scan, wherever it was made, newest first`() {
        val old = doc("OLD", source = "CS175", scans = 2, date = Instant.parse("2026-10-01T00:00:00Z"))
        val new = doc("NEW", scans = 1, date = Instant.parse("2026-11-05T00:00:00Z"))
        val untouched = doc("NONE")
        // A document whose only scans lost their line still counts: they add to no line at all.
        val orphanedOnly = doc("ORPH", source = "CS175", scans = 1, date = Instant.parse("2026-09-01T00:00:00Z"),
            orphaned = listOf(OrphanedScan("B", 1.0, "KOM", 10000, null, "ANA")))
        val d = data(listOf(old, untouched, new, orphanedOnly))
        assertEquals(listOf("NEW", "OLD", "ORPH"), d.recordedDocs.map { it.documentNo })
    }

    @Test
    fun `shift counters come from the selected scope only`() {
        val ready = doc("R", lines = listOf(line(2.0, 2.0)), state = DocState.Completed)
        val partial = doc("P", lines = listOf(line(2.0, 1.0)), state = DocState.InProgress)
        val over = doc("O", lines = listOf(line(2.0, 3.0)), state = DocState.InProgress)
        val failed = doc("F", lines = listOf(line(2.0, 2.0)), state = DocState.UploadFailed("x"))
        val elsewhere = doc("E", source = "CS175", lines = listOf(line(2.0, 2.0)), state = DocState.Completed)
        val d = data(listOf(ready, partial, over, failed, elsewhere))
        assertEquals(listOf("R"), d.readyDocs.map { it.documentNo })
        assertEquals(listOf("P"), d.partialDocs.map { it.documentNo })
        assertEquals(listOf("O"), d.overDocs.map { it.documentNo })
        assertEquals(listOf("F"), d.errorDocs.map { it.documentNo })
        assertEquals(4, d.shiftScans)
    }

    @Test
    fun `the type's status strip shows failures as empty and leaves untouched documents out`() {
        val untouched = doc("A")
        val failed = doc("F", lines = listOf(line(2.0, 2.0)), state = DocState.UploadFailed("x"))
        val partial = doc("P", lines = listOf(line(2.0, 1.0)), state = DocState.InProgress)
        val summary = data(listOf(untouched, failed, partial)).docTypes.first { it.type == DocumentType.WAREHOUSE_SHIPMENT }
        assertEquals(3, summary.count)
        assertEquals(listOf(LineStatus.EMPTY, LineStatus.PARTIAL), summary.statusMini)
    }
}
