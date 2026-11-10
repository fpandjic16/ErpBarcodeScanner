package com.prima.barcode.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prima.barcode.data.model.DocState
import com.prima.barcode.data.model.DocumentFilter
import com.prima.barcode.data.model.Document
import com.prima.barcode.data.model.DocumentType
import com.prima.barcode.data.model.LineStatus
import com.prima.barcode.data.model.bgColor
import com.prima.barcode.data.model.color
import com.prima.barcode.data.model.scanStatus
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.prima.barcode.data.barcode.DataWedgeManager
import com.prima.barcode.data.haptic.HapticEngine
import com.prima.barcode.data.sound.rememberSoundEngine
import com.prima.barcode.ui.component.CameraPreview
import com.prima.barcode.ui.component.PrimaTopBar
import com.prima.barcode.ui.component.verticalScrollbar
import com.prima.barcode.ui.component.ScanField
import com.prima.barcode.ui.component.StatusProgressBar
import com.prima.barcode.ui.theme.LocalTextSizeOffset
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.monoLabel
import com.prima.barcode.ui.theme.uppercased
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import com.prima.barcode.R
import com.prima.barcode.ui.theme.PrimaSignal
import com.prima.barcode.ui.theme.PrimaLine
import com.prima.barcode.ui.theme.PrimaOnDark

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    .withZone(ZoneId.systemDefault())
private fun Instant.toLocalDate(): LocalDate =
    atZone(ZoneId.systemDefault()).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(
    docType: DocumentType,
    locationCode: String,
    docTypeCode: String = "",
    documents: List<Document>,
    onBack: () -> Unit,
    onDocTap: (Document) -> Unit,
    onDownload: () -> Unit,
    onUpload: (List<Document>) -> Unit,
    onErrorTap: (Document) -> Unit = {},
    onClearErrors: () -> Unit = {},
    filter: DocumentFilter = DocumentFilter(),
    onOpenFilter: () -> Unit = {},
    hapticEnabled: Boolean = true,
    soundEnabled: Boolean = true,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var docNotFoundError by remember { mutableStateOf<String?>(null) }
    var showClearErrorsDialog by remember { mutableStateOf(false) }
    // Location is not re-checked here. The caller already filtered by the type's
    // DocTypeFilterMode, and this line only ever compared sourceCode — so on a type scoped by
    // responsibility centre it cut by location as well, which is the opposite of what that
    // setting says. `locationCode` is still taken, for the subtitle.
    val filtered = remember(documents, filter) {
        documents.filter { doc ->
            val docDate = (doc.documentDate ?: doc.creationDateTime).toLocalDate()
            val dateOk  = (filter.dateFrom == null || !docDate.isBefore(filter.dateFrom)) &&
                          (filter.dateTo   == null || !docDate.isAfter(filter.dateTo))
            val stateOk = filter.states.isEmpty() || doc.scanStatus() in filter.states
            val destOk  = filter.destinationCode.isBlank() || doc.destinationCode.contains(filter.destinationCode, ignoreCase = true)
            val srcOk   = filter.sourceCode.isBlank() || doc.sourceCode.contains(filter.sourceCode, ignoreCase = true)
            val rcOk    = filter.rcCode.isBlank() || doc.rcCode.contains(filter.rcCode, ignoreCase = true)
            dateOk && stateOk && destOk && srcOk && rcOk
        }
    }

    // PendingUpload belongs here too. It is set the moment a background upload starts, and
    // without it the document matched no tab at all and simply vanished from this screen while
    // it was being sent — with no way back if the process died mid-upload.
    val orders     = remember(filtered) { filtered.filter { it.state == DocState.Downloaded || it.state == DocState.InProgress || it.state == DocState.PendingUpload || it.state is DocState.UploadFailed } }
    val errors     = remember(filtered) { filtered.filter { it.state is DocState.UploadFailed } }

    // The RECORDINGS tab is gone: it listed the same documents this tab already holds, and what it
    // was really for — seeing what has been scanned — now lives in the ZAPISI section of the main
    // menu, which shows the individual scans rather than just the documents carrying them.
    val tabs = listOf(
        stringResource(R.string.doc_list_tab_orders) to orders.size,
        stringResource(R.string.doc_list_tab_errors) to errors.size,
    )

    val visibleDocs = when (selectedTab) {
        0    -> orders
        1    -> errors
        else -> emptyList()
    }

    // Only documents with real progress may be uploaded. The Orders tab lists Downloaded
    // documents too, and handing one of those to runUpload is destructive: it finds zero
    // recordings, the send loop never runs, nothing reports a failure, and the document is
    // deleted from the device as if it had been uploaded. DocumentOverviewScreen has always
    // filtered this way; this screen did not, so UPLOAD here silently discarded freshly
    // downloaded work.
    // A document needing review counts as uploadable so that pressing UPLOAD is what surfaces it:
    // runUpload turns it into a visible error. Left out, a document whose scanned lines were all
    // removed in the ERP would sit here looking ordinary with no way to reach the review at all.
    val uploadableDocs = remember(visibleDocs) {
        visibleDocs.filter { doc -> doc.lines.any { it.scanned > 0.0 } || doc.needsReview }
    }
    // Nothing recorded means nothing to send. Showing the button live in that state invited the
    // tap that used to delete the documents, and still promises an action that would do nothing.
    val canUpload = uploadableDocs.isNotEmpty()

    val context = LocalContext.current
    var cameraOpen by remember { mutableStateOf(false) }
    val hasCamera = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    // Scanner first, camera only for devices without one.
    val hasScanner = remember { DataWedgeManager.isAvailable(context) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) cameraOpen = true }
    val hapticEngine = remember { HapticEngine(context) }
    val soundEngine = rememberSoundEngine()

    // Declared after the engines it uses, and it has to stay that way: a local function can only
    // capture locals that already exist above it.
    fun handleDocScan(barcode: String) {
        val found = documents.firstOrNull { it.documentNo == barcode }
        if (found != null) {
            if (hapticEnabled) hapticEngine.confirm()
            if (soundEnabled) soundEngine.confirm()
            onDocTap(found)
        } else {
            if (hapticEnabled) hapticEngine.error()
            // Same warning sound as a scan that warns on the recording screen.
            if (soundEnabled) soundEngine.warning()
            docNotFoundError = barcode
        }
    }

    // The scanner's way in. This screen used to read the trigger only as keystrokes typed into the
    // scan field; DataWedge no longer types, so it listens for the intent like every other screen
    // that scans. The receiver calls through rememberUpdatedState so it always sees the current
    // document list rather than the one from the first composition.
    val latestDocScan = rememberUpdatedState(::handleDocScan)
    DisposableEffect(Unit) {
        val receiver = DataWedgeManager.createReceiver { barcode -> latestDocScan.value(barcode) }
        DataWedgeManager.register(context, receiver)
        onDispose { context.unregisterReceiver(receiver) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(PrimaPalette.Cream)) {
        PrimaTopBar(
            title = docType.localizedDisplay(),
            subtitle = if (docTypeCode.isNotBlank()) "$locationCode · $docTypeCode" else locationCode,
            onBack = onBack,
            actions = {
                IconButton(onClick = onOpenFilter) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = stringResource(R.string.cd_filter),
                        tint = if (filter.isActive) PrimaPalette.Coral else Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PrimaPalette.SlateAlt)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            ScanField(
                placeholder = stringResource(R.string.doc_list_scan_placeholder),
                onScan = { handleDocScan(it) },
                onCameraTap = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED) cameraOpen = true
                    else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                showCamera = hasCamera && !hasScanner,
                dark = true,
            )
        }

        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = PrimaPalette.SlateAlt,
            contentColor = Color.White,
            indicator = {
                Box(
                    Modifier
                        .tabIndicatorOffset(selectedTab)
                        .height(2.dp)
                        .background(PrimaPalette.Coral)
                )
            },
        ) {
            tabs.forEachIndexed { index, (label, count) ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Row(
                            modifier = if (index == 1) Modifier.padding(horizontal = 1.dp) else Modifier,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label.uppercased, style = monoLabel.copy(color = Color.White))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(PrimaOnDark.Button)
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            ) {
                                Text(count.toString(), style = monoLabel.copy(color = Color.White))
                            }
                        }
                    },
                )
            }
        }

        val listState = rememberLazyListState()
        if (visibleDocs.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.doc_list_empty), style = monoLabel.copy(color = PrimaPalette.Ink3))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScrollbar(listState),
                state = listState,
            ) {
                items(visibleDocs, key = { it.documentNo }) { doc ->
                    DocRow(
                        doc = doc,
                        showErrorDetails = selectedTab == 1,
                        onClick = {
                            if (selectedTab == 1 && doc.state is DocState.UploadFailed) onErrorTap(doc)
                            else onDocTap(doc)
                        },
                    )
                    HorizontalDivider(color = PrimaLine.Divider, thickness = 1.dp)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (selectedTab) {
                1 -> {
                    Box(
                        modifier = Modifier
                            .weight(1f).height(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, PrimaLine.Outline, RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .clickable { showClearErrorsDialog = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.overview_clear_errors).uppercased, style = monoLabel.copy(color = PrimaPalette.Ink, fontWeight = FontWeight.Medium))
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f).height(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (canUpload) PrimaPalette.Coral else PrimaPalette.Coral.copy(alpha = 0.35f))
                            .clickable(enabled = canUpload) { onUpload(uploadableDocs) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.CloudUpload, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.btn_upload).uppercased, style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium))
                        }
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .weight(1f).height(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, PrimaLine.Outline, RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .clickable(onClick = onDownload),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.CloudDownload, null, tint = PrimaPalette.Ink, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.btn_download).uppercased, style = monoLabel.copy(color = PrimaPalette.Ink, fontWeight = FontWeight.Medium))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f).height(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (canUpload) PrimaPalette.Coral else PrimaPalette.Coral.copy(alpha = 0.35f))
                            .clickable(enabled = canUpload) { onUpload(uploadableDocs) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.CloudUpload, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.btn_upload).uppercased, style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium))
                        }
                    }
                }
            }
        }
    }

    // Drawn last so it covers the list. One read closes the camera and takes the operator
    // straight into the document, which is the whole point of scanning here.
    if (cameraOpen) {
        CameraPreview(
            onBarcode = { raw ->
                cameraOpen = false
                handleDocScan(raw)
            },
            onClose = { cameraOpen = false },
        )
    }
    } // end Box

    docNotFoundError?.let { no ->
        AlertDialog(
            onDismissRequest = { docNotFoundError = null },
            title = { Text(stringResource(R.string.doc_list_create_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.doc_list_create_text, no)) },
            confirmButton = {
                Button(onClick = { docNotFoundError = null }) { Text(stringResource(R.string.btn_ok)) }
            },
        )
    }

    if (showClearErrorsDialog) {
        AlertDialog(
            onDismissRequest = { showClearErrorsDialog = false },
            title = { Text(stringResource(R.string.overview_clear_errors_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (errors.size == 1) stringResource(R.string.overview_clear_errors_text_single, errors.size)
                    else stringResource(R.string.overview_clear_errors_text_plural, errors.size)
                )
            },
            confirmButton = {
                Button(
                    onClick = { showClearErrorsDialog = false; onClearErrors() },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaSignal.Danger),
                ) { Text(stringResource(R.string.btn_clear), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearErrorsDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }
}




@Composable
private fun DocRow(
    doc: Document,
    showErrorDetails: Boolean = false,
    onClick: () -> Unit,
) {
    val sizeOffset = LocalTextSizeOffset.current
    val status = doc.scanStatus()
    val statusColor = status.color

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(statusColor.copy(alpha = 0.10f))
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(statusColor))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            doc.documentNo,
                            style = monoLabel.copy(
                                color = PrimaPalette.Ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = (14 + sizeOffset).sp,
                            ),
                        )
                        Spacer(Modifier.height(5.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DocScanStatusChip(status)
                            if (doc.state is DocState.UploadFailed) DocStateErrorChip()
                            if (doc.state == DocState.PendingUpload) DocStateSendingChip()
                        }
                    }
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(top = 2.dp)) {
                        Text(
                            doc.sourceCode,
                            style = monoLabel.copy(color = PrimaPalette.Ink3, fontSize = (12 + sizeOffset).sp),
                        )
                        Text(
                            doc.destinationCode,
                            style = monoLabel.copy(color = PrimaPalette.Ink3, fontSize = (12 + sizeOffset).sp),
                        )
                        Text(
                            dateFmt.format(doc.documentDate ?: doc.creationDateTime),
                            style = monoLabel.copy(color = PrimaPalette.Ink3, fontSize = (12 + sizeOffset).sp),
                        )
                    }
                }
                if (doc.state == DocState.InProgress || doc.state == DocState.Completed) {
                    Spacer(Modifier.height(10.dp))
                    StatusProgressBar(segments = doc.lines.map { it.status }, height = 4.dp, gap = 2.dp)
                }
                if (doc.state is DocState.UploadFailed && showErrorDetails) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        doc.state.reason,
                        style = monoLabel.copy(color = PrimaSignal.Danger, fontSize = (12 + sizeOffset).sp),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DocStateErrorChip() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(PrimaSignal.Danger)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(stringResource(R.string.doc_chip_error).uppercased, style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium))
    }
}

/** Shown while a background upload is in flight, so the document doesn't look idle. */
@Composable
private fun DocStateSendingChip() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(PrimaPalette.Teal)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(stringResource(R.string.doc_chip_sending).uppercased, style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun DocScanStatusChip(status: LineStatus) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(status.color)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(status.localizedLabel().uppercased, style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium))
    }
}
