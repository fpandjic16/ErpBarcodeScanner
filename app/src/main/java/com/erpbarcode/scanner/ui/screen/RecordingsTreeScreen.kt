package com.erpbarcode.scanner.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.data.model.Document
import com.erpbarcode.scanner.data.model.LineScans
import com.erpbarcode.scanner.data.model.OrphanedScan
import com.erpbarcode.scanner.data.model.ScanRecord
import com.erpbarcode.scanner.data.model.color
import com.erpbarcode.scanner.data.model.inkColor
import com.erpbarcode.scanner.data.model.formatQty
import com.erpbarcode.scanner.ui.component.AppTopBar
import com.erpbarcode.scanner.ui.component.verticalScrollbar
import com.erpbarcode.scanner.ui.theme.LocalTextSizeOffset
import com.erpbarcode.scanner.ui.theme.AppPalette
import com.erpbarcode.scanner.ui.theme.AppStatus
import com.erpbarcode.scanner.ui.theme.monoLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.erpbarcode.scanner.ui.theme.AppSignal
import com.erpbarcode.scanner.ui.theme.AppLine
import com.erpbarcode.scanner.ui.theme.AppOnDark

private val scanTimeFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneId.systemDefault())

/**
 * Every scan recorded against one document, as a tree: the document's lines, and under each line
 * the individual scans that make up its quantity.
 *
 * Replaces the scan tape, which showed the last few reads and forgot them the moment the operator
 * left the recording screen. Sent scans are shown but never deletable — they are already in the
 * ERP, and removing them here would only make the device forget what it had sent.
 */
@Composable
fun RecordingsTreeScreen(
    document: Document,
    tree: List<LineScans>,
    onBack: () -> Unit,
    onDeleteScan: (ScanRecord) -> Unit,
    onDeleteAllQueued: () -> Unit,
) {
    val sizeOffset = LocalTextSizeOffset.current
    var confirmDelete by remember { mutableStateOf<ScanRecord?>(null) }
    var showSentLocked by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    // Scans whose line the ERP has since removed. They have no branch to hang from, and leaving
    // them out would make this screen quietly disagree with the count it shows at the top. Not
    // deletable here: discarding an orphan is a decision the upload review asks for explicitly,
    // with the whole picture in front of it.
    val orphans = remember(document) {
        document.orphanedScans.map { it to false } + document.sentOrphanedScans.map { it to true }
    }

    Column(modifier = Modifier.fillMaxSize().background(AppPalette.Cream)) {
        AppTopBar(
            title = document.documentNo,
            subtitle = document.type.localizedDisplay(),
            onBack = onBack,
        )

        DocumentScanSummary(
            document = document,
            onLongHold = { confirmDeleteAll = true },
        )

        if (tree.isEmpty() && orphans.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.recordings_tree_empty),
                    style = monoLabel.copy(color = AppPalette.Ink3),
                )
            }
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScrollbar(listState),
                state = listState,
            ) {
                tree.forEach { branch ->
                    item(key = "line-${branch.line.lineNo}") {
                        LineBranchRow(branch)
                    }
                    items(
                        branch.scans,
                        key = { scan -> "scan-${branch.line.lineNo}-${scan.recordingLineNo}" },
                    ) { scan ->
                        ScanLeafRow(
                            scan = scan,
                            unitOfMeasureCode = branch.line.unitOfMeasureCode,
                            onDelete = { confirmDelete = scan },
                            onSentTap = { showSentLocked = true },
                        )
                    }
                    if (branch.scans.isEmpty()) {
                        item(key = "empty-${branch.line.lineNo}") {
                            Text(
                                stringResource(R.string.recordings_tree_no_scans),
                                style = monoLabel.copy(
                                    color = AppPalette.Ink4,
                                    fontSize = (11 + sizeOffset).sp,
                                ),
                                modifier = Modifier.padding(start = 38.dp, top = 6.dp, bottom = 8.dp),
                            )
                        }
                    }
                    item(key = "div-${branch.line.lineNo}") {
                        HorizontalDivider(color = AppLine.Divider, thickness = 1.dp)
                    }
                }

                if (orphans.isNotEmpty()) {
                    item(key = "orphans-header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AppStatus.EmptyBg)
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        ) {
                            Text(
                                stringResource(R.string.recordings_tree_orphans),
                                style = monoLabel.copy(
                                    color = AppStatus.EmptyInk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (13 + sizeOffset).sp,
                                ),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.recordings_tree_orphans_desc),
                                style = monoLabel.copy(color = AppPalette.Ink3, fontSize = (11 + sizeOffset).sp),
                            )
                        }
                    }
                    items(
                        orphans,
                        key = { (orphan, sent) -> "orphan-$sent-${orphan.lineNo}-${orphan.barcodeNo}-${orphan.at}" },
                    ) { (orphan, sent) ->
                        OrphanLeafRow(orphan = orphan, sent = sent)
                    }
                }
            }
        }
    }

    confirmDelete?.let { scan ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.recordings_tree_delete_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(stringResource(R.string.recordings_tree_delete_text, scan.barcodeNo, scan.quantity.formatQty()))
            },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = null; onDeleteScan(scan) },
                    colors = ButtonDefaults.buttonColors(containerColor = AppSignal.Danger),
                ) { Text(stringResource(R.string.btn_clear), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }

    if (showSentLocked) {
        AlertDialog(
            onDismissRequest = { showSentLocked = false },
            title = { Text(stringResource(R.string.recordings_tree_sent), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.recordings_tree_sent_locked)) },
            confirmButton = {
                Button(onClick = { showSentLocked = false }) { Text(stringResource(R.string.btn_ok)) }
            },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.doc_delete_recordings_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.doc_delete_recordings_text, document.documentNo))
                    if (document.sentScans > 0) {
                        Text(
                            stringResource(R.string.doc_delete_recordings_kept, document.sentScans),
                            color = AppPalette.Ink3,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { confirmDeleteAll = false; onDeleteAllQueued() },
                    colors = ButtonDefaults.buttonColors(containerColor = AppSignal.Danger),
                ) { Text(stringResource(R.string.btn_clear), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }
}

/**
 * Totals for the document, and the only way to clear its scans.
 *
 * The 5-second hold used to live on the RECORDINGS tab of the document list; that tab is gone, so
 * the gesture moved here, where the operator can see exactly what it would remove. Unlike the old
 * one it takes only the queued scans — see `DocumentRepository.deleteDocumentRecordings`.
 */
@Composable
private fun DocumentScanSummary(document: Document, onLongHold: () -> Unit) {
    val sizeOffset = LocalTextSizeOffset.current
    val coroutineScope = rememberCoroutineScope()
    var holdProgress by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(1.dp, AppLine.CardBorder, RoundedCornerShape(14.dp))
                .pointerInput(onLongHold) {
                    detectTapGestures(
                        onPress = {
                            val job = coroutineScope.launch {
                                var elapsed = 0L
                                while (elapsed < 5000L) {
                                    delay(16L)
                                    elapsed += 16L
                                    holdProgress = elapsed / 5000f
                                }
                                holdProgress = 0f
                                onLongHold()
                            }
                            tryAwaitRelease()
                            job.cancel()
                            holdProgress = 0f
                        },
                    )
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    document.totalScans.toString(),
                    style = monoLabel.copy(
                        color = AppPalette.Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = (20 + sizeOffset).sp,
                    ),
                )
                Spacer(Modifier.width(10.dp))
                if (document.sentScans > 0) {
                    SentChip(document.sentScans)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.recordings_tree_hold_hint),
                style = monoLabel.copy(color = AppPalette.Ink4, fontSize = (11 + sizeOffset).sp),
            )
        }
        if (holdProgress > 0f) {
            Box(
                modifier = Modifier.matchParentSize().background(AppLine.Overlay, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { holdProgress },
                    color = AppPalette.Coral,
                    trackColor = AppOnDark.Track,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

@Composable
private fun SentChip(count: Int) {
    val sizeOffset = LocalTextSizeOffset.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AppStatus.ExactBg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            stringResource(R.string.recordings_sent_chip, count),
            style = monoLabel.copy(
                color = AppStatus.ExactInk,
                fontWeight = FontWeight.Medium,
                fontSize = (11 + sizeOffset).sp,
            ),
        )
    }
}

/** One document line — the trunk of a branch. Shows scanned/expected, coloured by line status. */
@Composable
private fun LineBranchRow(branch: LineScans) {
    val sizeOffset = LocalTextSizeOffset.current
    val line = branch.line
    val statusColor = line.status.color
    val inkColor = line.status.inkColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(statusColor.copy(alpha = 0.08f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(statusColor))
        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 12.dp),
        ) {
            Text(
                line.item.no,
                style = monoLabel.copy(
                    color = AppPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = (13 + sizeOffset).sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (line.item.name.isNotBlank()) {
                Text(
                    line.item.name,
                    style = monoLabel.copy(color = AppPalette.Ink3, fontSize = (11 + sizeOffset).sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            "${line.scanned.formatQty()}/${line.expected.formatQty()}",
            style = monoLabel.copy(
                color = inkColor,
                fontWeight = FontWeight.Bold,
                fontSize = (15 + sizeOffset).sp,
            ),
            modifier = Modifier.padding(end = 14.dp),
        )
    }
}

/**
 * A scan with no line left to sit under.
 *
 * Shows the barcode rather than an item number: the line that carried the item name is gone, and
 * the barcode is all the scan itself recorded.
 */
@Composable
private fun OrphanLeafRow(orphan: OrphanedScan, sent: Boolean) {
    val sizeOffset = LocalTextSizeOffset.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(start = 22.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(AppStatus.Empty.copy(alpha = 0.35f)))
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Text(
                orphan.barcodeNo,
                style = monoLabel.copy(color = AppPalette.Ink, fontSize = (13 + sizeOffset).sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                orphan.at?.let { scanTimeFmt.format(it) }.orEmpty(),
                style = monoLabel.copy(color = AppPalette.Ink4, fontSize = (11 + sizeOffset).sp),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${orphan.quantity.formatQty()} ${orphan.unitOfMeasureCode}".trim(),
                style = monoLabel.copy(
                    color = AppPalette.Ink,
                    fontWeight = FontWeight.Medium,
                    fontSize = (13 + sizeOffset).sp,
                ),
            )
            Text(
                if (sent) stringResource(R.string.recordings_tree_sent)
                else stringResource(R.string.recordings_tree_queued),
                style = monoLabel.copy(
                    color = if (sent) AppStatus.ExactInk else AppPalette.Ink3,
                    fontSize = (11 + sizeOffset).sp,
                ),
            )
        }
    }
}

/** One recorded scan, indented under its line. */
@Composable
private fun ScanLeafRow(
    scan: ScanRecord,
    unitOfMeasureCode: String,
    onDelete: () -> Unit,
    onSentTap: () -> Unit,
) {
    val sizeOffset = LocalTextSizeOffset.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(start = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The connector that makes this read as a child of the line above it.
        Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(AppLine.CardBorder))
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    scan.quantity.formatQty(),
                    style = monoLabel.copy(
                        color = AppPalette.Ink,
                        fontWeight = FontWeight.Medium,
                        fontSize = (13 + sizeOffset).sp,
                    ),
                )
                if (unitOfMeasureCode.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        unitOfMeasureCode,
                        style = monoLabel.copy(color = AppPalette.Ink3, fontSize = (11 + sizeOffset).sp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (scan.sent) stringResource(R.string.recordings_tree_sent)
                    else stringResource(R.string.recordings_tree_queued),
                    style = monoLabel.copy(
                        color = if (scan.sent) AppStatus.ExactInk else AppPalette.Ink3,
                        fontSize = (11 + sizeOffset).sp,
                    ),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                scan.at?.let { scanTimeFmt.format(it) }.orEmpty(),
                style = monoLabel.copy(color = AppPalette.Ink4, fontSize = (11 + sizeOffset).sp),
            )
        }
        // A sent scan keeps the slot but answers the tap with an explanation instead of a delete;
        // dropping the control entirely just left the operator wondering why this one row differs.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = if (scan.sent) onSentTap else onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (scan.sent) Icons.Outlined.Lock else Icons.Outlined.Delete,
                contentDescription = null,
                tint = if (scan.sent) AppPalette.Ink4 else AppSignal.Danger,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
