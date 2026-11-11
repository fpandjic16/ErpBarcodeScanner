package com.erpbarcode.scanner.ui.screen

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.data.model.LineStatus
import com.erpbarcode.scanner.data.model.formatQty
import com.erpbarcode.scanner.data.model.inkColor
import com.erpbarcode.scanner.ui.theme.AppSignal

/*
 * The recording screen's dialogs: what a scan or an edit can bring up. Each one is shown by
 * RecordingScreen from its own state and closed through onDismiss; none of them decides anything.
 * The order RecordingScreen composes them in is the order they stack in, which matters when one
 * scan raises two (see the over-scan dialog there).
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recording_barcode_not_found_title), fontWeight = FontWeight.Bold, color = AppSignal.Danger) },
        text = { Text(stringResource(R.string.recording_barcode_not_found_text, barcode)) },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.btn_ok), fontWeight = FontWeight.SemiBold) }
        },
    )
}

/** A stock count's scan of an item the batch does not list: [onCount] adds it, [onDismiss] drops the scan. */
@Composable
internal fun NotOnDocumentDialog(scan: NotOnDocumentScan, onCount: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recording_not_on_document_title), fontWeight = FontWeight.Bold, color = LineStatus.OVER.inkColor) },
        text = { Text(stringResource(R.string.recording_not_on_document_text, scan.barcode)) },
        confirmButton = {
            Button(onClick = onCount) { Text(stringResource(R.string.recording_not_on_document_add), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        },
    )
}

@Composable
internal fun UomMismatchDialog(info: UomMismatchInfo, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recording_uom_mismatch_title), fontWeight = FontWeight.Bold, color = AppSignal.Warning) },
        text = {
            Text(
                stringResource(
                    R.string.recording_uom_mismatch_body,
                    info.itemNo, info.itemName, info.barcode, info.expectedUom, info.scannedUom,
                )
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.btn_ok), fontWeight = FontWeight.SemiBold) }
        },
    )
}

@Composable
internal fun OverScanDialog(info: OverScanInfo, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recording_overscan_title), fontWeight = FontWeight.Bold, color = LineStatus.OVER.inkColor) },
        text = {
            Text(
                stringResource(
                    R.string.recording_overscan_body,
                    info.itemNo, info.itemName, info.barcode, info.expected.formatQty(), info.scanned.formatQty(),
                )
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.btn_ok), fontWeight = FontWeight.SemiBold) }
        },
    )
}

/** An edit that asked to go below what the ERP already holds for the line. */
@Composable
internal fun SentFloorDialog(info: SentFloorInfo, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recording_sent_floor_title), fontWeight = FontWeight.Bold) },
        text = {
            Text(
                stringResource(
                    R.string.recording_sent_floor_body,
                    info.itemNo,
                    "${info.sent.formatQty()} ${info.uom}".trim(),
                )
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.btn_ok), fontWeight = FontWeight.SemiBold) }
        },
    )
}
