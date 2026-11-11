package com.erpbarcode.scanner.ui.screen

import com.erpbarcode.scanner.data.model.Line
import kotlin.math.abs

/**
 * Which line of a document a scanned barcode lands on.
 *
 * Kept free of Compose and of the screen's state so it can be tested on its own: everything it
 * needs comes in as arguments.
 */
internal object ScanMatcher {

    /**
     * The line a scan of [barcode] is counted on, or null when no line carries it.
     *
     * Several lines can carry the same barcode (the same item twice on one shipment, or once per
     * package of a set). They are filled in line order: the first line still short of its expected
     * quantity takes the scan, and once every one of them is full, the last one takes the surplus,
     * so an over-scan shows where it belongs rather than on a line the operator has already
     * finished. NAV groups the scans by barcode and item anyway, so which of the lines holds a
     * scan changes nothing there; it is about what the operator sees.
     *
     * Line order is the ERP's line no.; lines added on the device (negative numbers) come after
     * the ERP's own, in the order they were added.
     *
     * On a stock count a quantity label (`BARCODE|UOM|QTY`) is matched on its plain barcode when
     * no line carries the whole label: the count's lines carry the plain barcode, and the label
     * prints whatever quantity sits on the shelf. [labelBarcode] is that plain part, or null when
     * the scan is no such label.
     *
     * [scannedSoFar] is what a line holds right now, including scans this screen has sent but the
     * database has not yet handed back.
     */
    fun match(
        lines: List<Line>,
        barcode: String,
        labelBarcode: String?,
        isStockCount: Boolean,
        scannedSoFar: (Line) -> Double,
    ): Line? {
        val candidates = lines.filter { it.barcodeNo == barcode }.ifEmpty {
            if (isStockCount && labelBarcode != null) lines.filter { it.barcodeNo == labelBarcode } else emptyList()
        }
        if (candidates.size <= 1) return candidates.firstOrNull()
        val ordered = candidates.sortedWith(compareBy<Line>({ it.isAddedOnDevice }, { abs(it.lineNo) }))
        return ordered.firstOrNull { scannedSoFar(it) < it.expected } ?: ordered.last()
    }
}
