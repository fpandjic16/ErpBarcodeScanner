package com.erpbarcode.scanner.ui.screen

import com.erpbarcode.scanner.data.model.Item
import com.erpbarcode.scanner.data.model.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanMatcherTest {

    private fun line(no: Int, barcode: String, expected: Double, scanned: Double = 0.0) = Line(
        documentNo = "DOC1",
        lineNo = no,
        item = Item("ITEM$no", "Item $no"),
        barcodeNo = barcode,
        expected = expected,
        scanned = scanned,
        destinationCode = "",
        sourceCode = "",
        unitOfMeasureCode = "KOM",
        isAddedOnDevice = no < 0,
    )

    private fun match(lines: List<Line>, barcode: String, label: String? = null, stockCount: Boolean = false) =
        ScanMatcher.match(lines, barcode, label, stockCount) { it.scanned }

    @Test
    fun `no line carries the barcode`() {
        assertNull(match(listOf(line(10000, "A", 1.0)), "B"))
    }

    @Test
    fun `a single line takes every scan, over or not`() {
        val only = line(10000, "A", 1.0, scanned = 5.0)
        assertEquals(10000, match(listOf(only), "A")?.lineNo)
    }

    @Test
    fun `lines with one barcode fill in line order`() {
        val lines = listOf(
            line(30000, "A", 2.0),
            line(10000, "A", 2.0, scanned = 2.0),
            line(20000, "A", 2.0, scanned = 1.0),
        )
        assertEquals(20000, match(lines, "A")?.lineNo)
    }

    @Test
    fun `when every line is full the last one takes the surplus`() {
        val lines = listOf(
            line(10000, "A", 1.0, scanned = 1.0),
            line(20000, "A", 1.0, scanned = 1.0),
        )
        assertEquals(20000, match(lines, "A")?.lineNo)
    }

    @Test
    fun `lines added on the device come after the ERP's own`() {
        val lines = listOf(
            line(-1, "A", 0.0),
            line(10000, "A", 3.0),
        )
        assertEquals(10000, match(lines, "A")?.lineNo)
    }

    @Test
    fun `a quantity label matches its plain barcode only on a stock count`() {
        val lines = listOf(line(10000, "A", 5.0))
        assertEquals(10000, match(lines, "A|KOM|3", label = "A", stockCount = true)?.lineNo)
        assertNull(match(lines, "A|KOM|3", label = "A", stockCount = false))
    }

    @Test
    fun `a line carrying the whole label wins over the plain barcode`() {
        val lines = listOf(line(10000, "A", 5.0), line(20000, "A|KOM|3", 5.0))
        assertEquals(20000, match(lines, "A|KOM|3", label = "A", stockCount = true)?.lineNo)
    }
}
