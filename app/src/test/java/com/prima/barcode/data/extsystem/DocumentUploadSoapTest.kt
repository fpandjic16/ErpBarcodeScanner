package com.prima.barcode.data.extsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentUploadSoapTest {

    private fun row(barcode: String = "3850000000017", qty: Double = 1.0, retail: Boolean? = null) = NavBarcodeAppRecording(
        documentType = "INVENTORY",
        retailLocation = retail,
        documentNo = "INV-001",
        documentLineNo = 0,
        recordingLineNo = 3,
        recordingGuid = "6f1c2a8e-1111-2222-3333-444455556666",
        barcode = barcode,
        scannedQuantity = qty,
        unitOfMeasureCode = "KOM",
        sourceCreationDateTime = "2026-11-10T08:15:30.123456789Z",
        sourceUserId = "PRIMA\\ana",
        sourceCode = "MP01",
        destinationCode = "",
    )

    @Test
    fun `envelope carries every row in the XMLport namespace`() {
        val xml = DocumentUploadSoap.envelope(listOf(row(), row(barcode = "A")))
        assertEquals(2, Regex("<Recording xmlns=\"${DocumentUploadSoap.XMLPORT_NAMESPACE}\">").findAll(xml).count())
        assertTrue(xml.contains("<UploadDocument xmlns=\"${DocumentUploadSoap.CODEUNIT_NAMESPACE}\"><recordings>"))
        assertTrue(xml.contains("<RecordingGuid>{6f1c2a8e-1111-2222-3333-444455556666}</RecordingGuid>"))
    }

    @Test
    fun `values are escaped and quantities never use an exponent`() {
        val xml = DocumentUploadSoap.envelope(listOf(row(barcode = "A&B<C>|KOM|1", qty = 0.0001)))
        assertTrue(xml.contains("<Barcode>A&amp;B&lt;C&gt;|KOM|1</Barcode>"))
        assertTrue(xml.contains("<ScannedQuantity>0.0001</ScannedQuantity>"))
        assertTrue(xml.contains("<SourceUserID>PRIMA\\ana</SourceUserID>"))
    }

    @Test
    fun `scan time is cut to milliseconds`() {
        val xml = DocumentUploadSoap.envelope(listOf(row()))
        assertTrue(xml.contains("<SourceCreationDateTime>2026-11-10T08:15:30.123Z</SourceCreationDateTime>"))
    }

    @Test
    fun `retail location is sent only when the document type has one`() {
        assertFalse(DocumentUploadSoap.envelope(listOf(row())).contains("RetailLocation"))
        assertTrue(DocumentUploadSoap.envelope(listOf(row(retail = true))).contains("<RetailLocation>true</RetailLocation>"))
    }

    @Test
    fun `fault string is read and unescaped`() {
        val body = """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><s:Fault>
            <faultcode>a:Microsoft.Dynamics.Nav.Types.Exceptions.NavNCLDialogException</faultcode>
            <faultstring xml:lang="hr-HR">Serija temeljnice &apos;INV&apos; ne postoji &amp; ne može se skenirati.</faultstring>
            </s:Fault></s:Body></s:Envelope>"""
        assertEquals("Serija temeljnice 'INV' ne postoji & ne može se skenirati.", DocumentUploadSoap.faultMessage(body))
        assertNull(DocumentUploadSoap.faultMessage("<html>Bad gateway</html>"))
    }
}
