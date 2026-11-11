package com.erpbarcode.scanner.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erpbarcode.scanner.data.model.DocState
import com.erpbarcode.scanner.data.model.Document
import com.erpbarcode.scanner.data.model.formatQty
import com.erpbarcode.scanner.ui.component.AppTopBar
import com.erpbarcode.scanner.ui.theme.LocalTextSizeOffset
import com.erpbarcode.scanner.ui.theme.AppPalette
import com.erpbarcode.scanner.ui.theme.monoLabel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.ui.theme.AppSignal
import com.erpbarcode.scanner.ui.theme.AppLine

private val errDateFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault())

@Composable
fun UploadErrorScreen(
    document: Document,
    onBack: () -> Unit,
    onRetryUpload: () -> Unit,
    onDiscardOrphans: () -> Unit = {},
    onDiscardFailed: () -> Unit = {},
) {
    val sizeOffset = LocalTextSizeOffset.current
    val errorReason = (document.state as? DocState.UploadFailed)?.reason ?: stringResource(R.string.upload_error_unknown)
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDiscardFailed by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(AppPalette.Cream)) {
        AppTopBar(
            title = document.documentNo,
            subtitle = stringResource(R.string.upload_error_subtitle),
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Error header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppSignal.DangerTint)
                    .border(1.dp, AppSignal.DangerBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = AppSignal.Danger,
                    modifier = Modifier.size(32.dp),
                )
                Column {
                    Text(
                        stringResource(R.string.upload_error_header),
                        style = monoLabel.copy(
                            color = AppSignal.Danger,
                            fontWeight = FontWeight.Bold,
                            fontSize = (14 + sizeOffset).sp,
                        ),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        document.documentNo,
                        style = monoLabel.copy(
                            color = AppSignal.DangerMuted,
                            fontSize = (12 + sizeOffset).sp,
                        ),
                    )
                }
            }

            // Document info card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .border(1.dp, AppLine.CardBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.upload_error_doc_section),
                    style = monoLabel.copy(color = AppPalette.Ink3, fontSize = (11 + sizeOffset).sp),
                )
                HorizontalDivider(color = AppLine.Divider, thickness = 1.dp)
                ErrorInfoRow(stringResource(R.string.upload_error_row_type),   document.type.localizedDisplay(), sizeOffset)
                ErrorInfoRow(stringResource(R.string.upload_error_row_source), document.sourceCode,   sizeOffset)
                if (document.destinationCode.isNotBlank())
                    ErrorInfoRow(stringResource(R.string.upload_error_row_destination), document.destinationCode, sizeOffset)
                if (document.rcCode.isNotBlank())
                    ErrorInfoRow(stringResource(R.string.upload_error_row_rc), document.rcCode, sizeOffset)
                ErrorInfoRow(stringResource(R.string.upload_error_row_lines), (if (document.lines.size == 1) stringResource(R.string.upload_error_line_single, document.lines.size) else stringResource(R.string.upload_error_line_plural, document.lines.size)), sizeOffset)
                document.documentDate?.let {
                    ErrorInfoRow(stringResource(R.string.upload_error_row_date), errDateFmt.format(it), sizeOffset)
                }
            }

            // Full error message card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .border(1.dp, AppLine.CardBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.upload_error_details),
                    style = monoLabel.copy(color = AppPalette.Ink3, fontSize = (11 + sizeOffset).sp),
                )
                HorizontalDivider(color = AppLine.Divider, thickness = 1.dp)
                Text(
                    errorReason,
                    style = monoLabel.copy(
                        color = AppPalette.Ink,
                        fontSize = (13 + sizeOffset).sp,
                        lineHeight = (20 + sizeOffset).sp,
                    ),
                )
            }

            // Says plainly that part of the work already reached the ERP. Without it a partly
            // sent document reads as a total failure, and the natural reaction — send it all
            // again — is the one that duplicates what already went.
            if (document.sentScans > 0) {
                Text(
                    stringResource(R.string.upload_sent_progress, document.sentScans, document.totalScans),
                    style = monoLabel.copy(color = AppPalette.Ink3, fontSize = (12 + sizeOffset).sp),
                )
            }

            // Reported, not offered for action. These already reached the ERP, so there is no
            // button: discarding them here would change nothing there, and offering one would
            // suggest otherwise. Neutral styling on purpose — it is information, not a problem
            // the operator is being asked to fix on this device.
            if (document.sentOrphanedScans.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.dp, AppLine.CardBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.sent_orphan_title),
                        style = monoLabel.copy(
                            color = AppPalette.Ink3,
                            fontWeight = FontWeight.Bold,
                            fontSize = (12 + sizeOffset).sp,
                        ),
                    )
                    Text(
                        stringResource(R.string.sent_orphan_intro),
                        style = monoLabel.copy(
                            color = AppPalette.Ink3,
                            fontSize = (11 + sizeOffset).sp,
                            lineHeight = (17 + sizeOffset).sp,
                        ),
                    )
                    HorizontalDivider(color = AppLine.Divider, thickness = 1.dp)
                    document.sentOrphanedScans.forEach { scan ->
                        ErrorInfoRow(
                            label = scan.barcodeNo,
                            value = "${scan.quantity.formatQty()} ${scan.unitOfMeasureCode}".trim() +
                                " · " + stringResource(R.string.review_row_line, scan.lineNo),
                            sizeOffset = sizeOffset,
                        )
                    }
                }
            }

            // Rows the ERP refused. Separate from the orphans below: these still belong to a real
            // line, the ERP simply would not take them, and they fail the same way every retry.
            if (document.failedScans.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.dp, AppSignal.DangerBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.failed_title),
                        style = monoLabel.copy(
                            color = AppSignal.Danger,
                            fontWeight = FontWeight.Bold,
                            fontSize = (12 + sizeOffset).sp,
                        ),
                    )
                    Text(
                        stringResource(R.string.failed_intro),
                        style = monoLabel.copy(
                            color = AppPalette.Ink3,
                            fontSize = (11 + sizeOffset).sp,
                            lineHeight = (17 + sizeOffset).sp,
                        ),
                    )
                    HorizontalDivider(color = AppLine.Divider, thickness = 1.dp)
                    document.failedScans.forEach { scan ->
                        ErrorInfoRow(
                            label = scan.barcodeNo,
                            value = "${scan.quantity.formatQty()} ${scan.unitOfMeasureCode}".trim() +
                                " · " + stringResource(R.string.review_row_line, scan.lineNo) +
                                "\n" + scan.error,
                            sizeOffset = sizeOffset,
                        )
                    }
                }
            }

            // The scans the operator has to decide about. Shown here rather than on a screen of
            // its own because this is where the block is explained, and the two belong together.
            if (document.orphanedScans.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.dp, AppSignal.WarningBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.review_title),
                        style = monoLabel.copy(
                            color = AppSignal.Warning,
                            fontWeight = FontWeight.Bold,
                            fontSize = (12 + sizeOffset).sp,
                        ),
                    )
                    Text(
                        stringResource(R.string.review_intro),
                        style = monoLabel.copy(
                            color = AppPalette.Ink3,
                            fontSize = (11 + sizeOffset).sp,
                            lineHeight = (17 + sizeOffset).sp,
                        ),
                    )
                    HorizontalDivider(color = AppLine.Divider, thickness = 1.dp)
                    document.orphanedScans.forEach { scan ->
                        ErrorInfoRow(
                            label = scan.barcodeNo,
                            // The item number and name lived on the line that is gone, so the
                            // barcode and the quantity are all the scan itself can tell us.
                            value = "${scan.quantity.formatQty()} ${scan.unitOfMeasureCode}".trim() +
                                " · " + stringResource(R.string.review_row_line, scan.lineNo),
                            sizeOffset = sizeOffset,
                        )
                    }
                }
            }
        }

        // Bottom action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // Retry cannot succeed while surplus scans remain — the upload check would raise the
            // same error again — so the only action offered is the one that can actually move
            // this forward. It returns on its own once the scans are resolved.
            if (document.orphanedScans.isNotEmpty()) {
                Button(
                    onClick = { confirmDiscard = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppSignal.Warning),
                ) {
                    Text(stringResource(R.string.review_discard), style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium))
                }
            } else {
                // Retry stays available alongside the discard: a refused row might have been
                // refused by a server that was misbehaving, and the operator should be able to
                // try again before giving up on it.
                Button(
                    onClick = onRetryUpload,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppPalette.Coral),
                ) {
                    Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_retry_upload), style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium))
                }
                if (document.failedScans.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { confirmDiscardFailed = true },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppSignal.Danger),
                    ) {
                        Text(stringResource(R.string.failed_discard), style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }
    }

    if (confirmDiscardFailed) {
        AlertDialog(
            onDismissRequest = { confirmDiscardFailed = false },
            title = { Text(stringResource(R.string.failed_discard_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.failed_discard_text, document.failedScans.size)) },
            confirmButton = {
                Button(
                    onClick = { confirmDiscardFailed = false; onDiscardFailed() },
                    colors = ButtonDefaults.buttonColors(containerColor = AppSignal.Danger),
                ) { Text(stringResource(R.string.failed_discard), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Button(onClick = { confirmDiscardFailed = false }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.review_discard_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.review_discard_text, document.orphanedScans.size)) },
            confirmButton = {
                Button(
                    onClick = { confirmDiscard = false; onDiscardOrphans() },
                    colors = ButtonDefaults.buttonColors(containerColor = AppSignal.Danger),
                ) { Text(stringResource(R.string.review_discard), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Button(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }
}

@Composable
private fun ErrorInfoRow(label: String, value: String, sizeOffset: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = monoLabel.copy(color = AppPalette.Ink3, fontSize = (12 + sizeOffset).sp),
            modifier = Modifier.weight(0.35f),
        )
        Text(
            value,
            style = monoLabel.copy(color = AppPalette.Ink, fontWeight = FontWeight.Medium, fontSize = (12 + sizeOffset).sp),
            modifier = Modifier.weight(0.65f),
        )
    }
}
