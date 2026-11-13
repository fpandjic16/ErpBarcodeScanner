package com.erpbarcode.scanner.ui.screen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.data.model.LineStatus
import com.erpbarcode.scanner.data.model.formatQty
import com.erpbarcode.scanner.data.model.inkColor
import com.erpbarcode.scanner.ui.component.AppAlertDialog
import com.erpbarcode.scanner.ui.theme.AppSignal

/*
 * The recording screen's dialogs: what a scan or an edit can bring up. Each one is shown by
 * RecordingScreen from its own state and closed through onDismiss; none of them decides anything.
 * The order RecordingScreen composes them in is the order they stack in, which matters when one
 * scan raises two (see the over-scan dialog there).
 *
 * The four a scan can raise carry the same warning triangle, as they share one warning sound: the
 * sign for "look at the screen" is one, and its colour says which it is. Red, nothing matched;
 * orange, the unit of measure differs; blue, more than expected, or an item the count does not
 * list.
 */

internal data class NotOnDocumentScan(
    val barcode: String,
    val unitOfMeasureCode: String,
    val quantity: Double,
)

internal data class SentFloorInfo(
    val itemNo: String,
    val sent: Double,
    val uom: String,
)

internal data class OverScanInfo(
    val itemNo: String,
    val itemName: String,
    val barcode: String,
    val expected: Double,
    val scanned: Double,
)

internal data class UomMismatchInfo(
    val itemNo: String,
    val itemName: String,
    val barcode: String,
    val expectedUom: String,
    val scannedUom: String,
)

@Composable
internal fun BarcodeNotFoundDialog(barcode: String, onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.recording_barcode_not_found_title),
        text = stringResource(R.string.recording_barcode_not_found_text, barcode),
        accent = AppSignal.Danger,
        icon = Icons.Outlined.Warning,
        confirmLabel = stringResource(R.string.btn_ok),
        onConfirm = onDismiss,
    )
}

/** A stock count's scan of an item the batch does not list: [onCount] adds it, [onDismiss] drops the scan. */
@Composable
internal fun NotOnDocumentDialog(scan: NotOnDocumentScan, onCount: () -> Unit, onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.recording_not_on_document_title),
        text = stringResource(R.string.recording_not_on_document_text, scan.barcode),
        accent = LineStatus.OVER.inkColor,
        icon = Icons.Outlined.Warning,
        confirmLabel = stringResource(R.string.recording_not_on_document_add),
        onConfirm = onCount,
        dismissLabel = stringResource(R.string.btn_cancel),
    )
}

@Composable
internal fun UomMismatchDialog(info: UomMismatchInfo, onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.recording_uom_mismatch_title),
        text = stringResource(
            R.string.recording_uom_mismatch_body,
            info.itemNo, info.itemName, info.barcode, info.expectedUom, info.scannedUom,
        ),
        accent = AppSignal.Warning,
        icon = Icons.Outlined.Warning,
        confirmLabel = stringResource(R.string.btn_ok),
        onConfirm = onDismiss,
    )
}

@Composable
internal fun OverScanDialog(info: OverScanInfo, onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.recording_overscan_title),
        text = stringResource(
            R.string.recording_overscan_body,
            info.itemNo, info.itemName, info.barcode, info.expected.formatQty(), info.scanned.formatQty(),
        ),
        accent = LineStatus.OVER.inkColor,
        icon = Icons.Outlined.Warning,
        confirmLabel = stringResource(R.string.btn_ok),
        onConfirm = onDismiss,
    )
}

/** An edit that asked to go below what the ERP already holds for the line. */
@Composable
internal fun SentFloorDialog(info: SentFloorInfo, onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.recording_sent_floor_title),
        text = stringResource(
            R.string.recording_sent_floor_body,
            info.itemNo,
            "${info.sent.formatQty()} ${info.uom}".trim(),
        ),
        // The padlock a sent scan carries in the recordings tree: this much has gone to the ERP.
        icon = Icons.Outlined.Lock,
        confirmLabel = stringResource(R.string.btn_ok),
        onConfirm = onDismiss,
    )
}
