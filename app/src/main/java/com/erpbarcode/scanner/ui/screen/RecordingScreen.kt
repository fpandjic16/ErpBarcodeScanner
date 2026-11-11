package com.erpbarcode.scanner.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.erpbarcode.scanner.data.haptic.HapticEngine
import com.erpbarcode.scanner.data.sound.rememberSoundEngine
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.erpbarcode.scanner.data.model.Document
import com.erpbarcode.scanner.data.model.Line
import com.erpbarcode.scanner.data.model.LineStatus
import com.erpbarcode.scanner.data.model.color
import com.erpbarcode.scanner.data.model.inkColor
import com.erpbarcode.scanner.data.model.formatQty
import com.erpbarcode.scanner.data.model.scanStatus
import com.erpbarcode.scanner.ui.component.AppTopBar
import com.erpbarcode.scanner.ui.component.TOP_BAR_BUTTON_HEIGHT
import com.erpbarcode.scanner.ui.component.ScanBar
import com.erpbarcode.scanner.ui.component.StatusProgressBar
import com.erpbarcode.scanner.ui.theme.LocalTextSizeOffset
import com.erpbarcode.scanner.ui.theme.AppPalette
import com.erpbarcode.scanner.ui.theme.AppStatus
import com.erpbarcode.scanner.ui.theme.monoCounter
import com.erpbarcode.scanner.ui.theme.monoLabel
import kotlinx.coroutines.delay
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.erpbarcode.scanner.data.barcode.DataWedgeManager
import com.erpbarcode.scanner.ui.component.CameraPreview
import androidx.compose.ui.res.stringResource
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.ui.theme.AppSignal
import com.erpbarcode.scanner.ui.theme.AppLine
import com.erpbarcode.scanner.ui.theme.AppOnDark

private enum class RecordingView { OVERVIEW, ACTIVE_LINE, KEYPAD }

/** How long a just-scanned line stays marked before it starts fading back. */
private const val HIGHLIGHT_HOLD_MS = 5_000L

/** Long enough to read as a fade rather than a flicker, short enough not to trail a fast scanner. */
private const val HIGHLIGHT_FADE_MS = 700

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    doc: Document,
    docTypeCode: String = "",
    onBack: () -> Unit,
    /** [lineNo] is the line the screen matched the scan to; [barcode] is the full scanned value. */
    onScan: (lineNo: Int, barcode: String, quantity: Double) -> Unit,
    /** A stock count's scan of an item the document does not list, once the operator confirms it. */
    onScanNotOnDocument: (barcode: String, unitOfMeasureCode: String, quantity: Double) -> Unit = { _, _, _ -> },
    /**
     * [onRefused] fires when the edit asked to go below what the ERP already holds for the line,
     * and is handed that quantity. The screen blocks most of those itself — the steppers stop at
     * the floor — but the keypad can type any number, and a background upload can raise the floor
     * while this screen is open, so the authoritative refusal still has to come back from below.
     */
    onLineUpdate: (lineNo: Int, newScanned: Double, onRefused: (Double) -> Unit) -> Unit,
    onUpload: () -> Unit = {},
    /**
     * True while this document is being sent behind the "Uploading…" dialog. The hardware trigger
     * still reaches this screen then, and a scan taken now would be written under a dialog, onto
     * a document the operator expects to be gone when the dialog closes. So it is refused, with
     * the warning signal, and nothing is recorded.
     */
    scanLocked: Boolean = false,
    hapticEnabled: Boolean = true,
    soundEnabled: Boolean = true,
    debounceTime: Int = 200,
    warnOnOver: Boolean = true,
) {
    var view by remember { mutableStateOf(RecordingView.OVERVIEW) }
    var activeLineNo by remember { mutableStateOf<Int?>(null) }
    val activeLine = activeLineNo?.let { no -> doc.lines.find { it.lineNo == no } }
    var typedQty by remember { mutableStateOf("") }
    var scanErrorFlash by remember { mutableStateOf(false) }
    var localScanned by remember(activeLineNo) { mutableStateOf<Double?>(null) }
    var overScanWarning by remember { mutableStateOf<OverScanInfo?>(null) }
    var sentFloorWarning by remember { mutableStateOf<SentFloorInfo?>(null) }
    var barcodeNotFoundError by remember { mutableStateOf<String?>(null) }
    var notOnDocumentScan by remember { mutableStateOf<NotOnDocumentScan?>(null) }
    var uomMismatchWarning by remember { mutableStateOf<UomMismatchInfo?>(null) }
    // The hardware trigger (DataWedge) and the camera can both be "live" at the same time —
    // nothing stops a user from pulling the trigger while the camera is also pointed at a
    // barcode. Without this, that single physical gesture reaches handleScan twice (once per
    // input path) and records the quantity twice. Debounced on the raw scanned value itself so
    // a genuine re-scan of the same barcode after this window still counts normally.
    var lastHandledBarcode by remember { mutableStateOf<String?>(null) }
    var lastHandledAtMs by remember { mutableStateOf(0L) }
    // Per-line running totals, used only to judge the warnings below. `onScan` writes to the
    // database asynchronously, so `doc.lines` still carries the pre-scan total when the next scan
    // arrives. Two quick trigger pulls therefore measured against the same base and the over-scan
    // warning never fired — easy to hit with BARCODE|UOM|QTY labels, where one scan can carry a
    // large quantity and two of them pass `expected` long before the flow catches up. The
    // recordings themselves were always correct; only the operator's feedback was wrong.
    // maxOf keeps this safe against a lagging emission: the database wins as soon as it is ahead.
    val scannedRunningTotal = remember { mutableStateMapOf<Int, Double>() }
    val sizeOffset = LocalTextSizeOffset.current
    val showUpload = doc.lines.any { it.scanned > 0.0 }
    val context = LocalContext.current
    val hapticEngine = remember { HapticEngine(context) }
    // Sound marks the outcome of a scan and nothing else — not the keypad, not the steppers. A
    // tone on every key press is noise the operator learns to ignore, which costs the tones that
    // matter their meaning.
    val soundEngine = rememberSoundEngine()
    // No camera on some MC3300 configurations — hide the button rather than offer one that
    // opens an empty preview.
    val hasCamera = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    // The scanner is how this app reads barcodes; the camera is only for devices without one.
    // Offering both on a device that has a scanner invites the slow path for no reason.
    val hasScanner = remember { DataWedgeManager.isAvailable(context) }
    var cameraOpen by remember { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) cameraOpen = true }

    LaunchedEffect(scanErrorFlash) {
        if (scanErrorFlash) { delay(600); scanErrorFlash = false }
    }

    // Which line the last scan landed on, so the list can go to it and mark it.
    //
    // On a thirty-line document the only acknowledgement a scan used to get was a tone, and a tone
    // does not say *which* line moved — the line itself could be far off screen, leaving the
    // operator to hunt for it.
    //
    // Hoisted here rather than left inside OverviewContent because the scroll is driven from the
    // scan, not from the list.
    val overviewListState = rememberLazyListState()
    val highlight = remember { Animatable(0f) }
    var highlightedLineNo by remember { mutableStateOf<Int?>(null) }
    // A counter, not the line number. Scanning the same line twice does not change
    // highlightedLineNo, so keying the effect on that alone would leave the second scan
    // unacknowledged — the worst case, since repeat scans of one item are the common case.
    var highlightTick by remember { mutableStateOf(0) }

    LaunchedEffect(highlightTick) {
        val lineNo = highlightedLineNo ?: return@LaunchedEffect
        // Only in the overview: the other two views do not compose the list at all, so there is
        // nothing to scroll. The mark is still set, so coming back within the hold still shows it.
        if (view == RecordingView.OVERVIEW) {
            val target = doc.lines.indexOfFirst { it.lineNo == lineNo }
            // Already on screen is already found. Jumping the list under a line the operator is
            // looking at is a jolt that buys nothing.
            if (target >= 0 && overviewListState.layoutInfo.visibleItemsInfo.none { it.index == target }) {
                overviewListState.animateScrollToItem(target)
            }
        }
        highlight.snapTo(1f)
        delay(HIGHLIGHT_HOLD_MS)
        highlight.animateTo(0f, tween(HIGHLIGHT_FADE_MS))
        highlightedLineNo = null
    }

    // Whether going over this line's expected quantity is worth a dialog. Not on a line added on
    // the device: it expects nothing, so every scan of it is "over", and the operator confirmed
    // that when adding it. Not on a stock count's line that expects 0 or less either: the
    // system's stock is already wrong there, and a dialog on every scan would say only that.
    fun warnsOnOver(line: Line): Boolean =
        !line.isAddedOnDevice && !(doc.type.isStockCount && line.expected <= 0.0)

    fun handleScan(rawInput: String) {
        // Before the debounce, so that every pull of the trigger during an upload is answered.
        if (scanLocked) {
            if (hapticEnabled) hapticEngine.error()
            if (soundEnabled) soundEngine.warning()
            return
        }
        val now = System.currentTimeMillis()
        if (rawInput == lastHandledBarcode && now - lastHandledAtMs < debounceTime) return
        lastHandledBarcode = rawInput
        lastHandledAtMs = now

        // The scanned/matched barcode is always the full raw input, e.g. "NTR1234|M|5.6"
        // in its entirety — that's the literal value on the label and what line.barcodeNo
        // matches against. UOM and quantity are additionally parsed out of it for
        // recording purposes when there are exactly two "|" separators and the last
        // part is a valid number; they never change what's searched for or stored as
        // the barcode.
        val barcode = rawInput
        val pipeParts = rawInput.split("|")
        val parsedQty = if (pipeParts.size == 3) pipeParts[2].toDoubleOrNull() else null
        val parsedUom = if (parsedQty != null) pipeParts[1] else null

        // A stock count's line carries the plain barcode, because a quantity label on the shelf
        // prints whatever quantity it holds. Such a label lands on the line for the part before
        // the first "|", and its printed quantity is what counts. Several lines with one barcode
        // are filled in line order; see ScanMatcher.
        val matchedLine = ScanMatcher.match(
            lines        = doc.lines,
            barcode      = barcode,
            labelBarcode = if (parsedQty != null) pipeParts[0] else null,
            isStockCount = doc.type.isStockCount,
            scannedSoFar = { line -> maxOf(line.scanned, scannedRunningTotal[line.lineNo] ?: 0.0) },
        )
        if (matchedLine == null) {
            scanErrorFlash = true
            if (hapticEnabled) hapticEngine.error()
            // The same warning sound as an over-scan: every scan that brings up a warning sounds
            // alike, so the operator learns one signal for "look at the screen".
            if (soundEnabled) soundEngine.warning()
            // On a stock count an unlisted item is a finding, not a mistake, but it is also what a
            // stray scan of the wrong label looks like. So it is asked about rather than counted
            // straight away. Only the first scan of a barcode asks: after that it has its line.
            if (doc.type.isStockCount) {
                notOnDocumentScan = NotOnDocumentScan(barcode, parsedUom.orEmpty(), parsedQty ?: 1.0)
            } else {
                barcodeNotFoundError = barcode
            }
        } else {
            val qty = parsedQty ?: matchedLine.scanningQty
            onScan(matchedLine.lineNo, barcode, qty)
            highlightedLineNo = matchedLine.lineNo
            highlightTick++
            val base = maxOf(matchedLine.scanned, scannedRunningTotal[matchedLine.lineNo] ?: 0.0)
            val newScanned = base + qty
            scannedRunningTotal[matchedLine.lineNo] = newScanned
            val newStatus = LineStatus.of(newScanned, matchedLine.expected)
            val overScan = warnOnOver && newStatus == LineStatus.OVER && warnsOnOver(matchedLine)
            // Every accepted scan makes a sound, not only the one that completes a line — this is
            // the operator's confirmation that the pull counted; the haptic below marks something
            // else, that the line is now exact.
            //
            // An over-scan gets the warning tone instead of the confirm. The scan did count, but a
            // "fine" beep followed by a warning dialog says two opposite things, and the beep is
            // the one the operator hears while looking at the shelf rather than the screen. Tied
            // to warnOnOver exactly as the dialog is: no warning shown, no warning sound. Decided
            // after the status is known, which is why this sits below the arithmetic.
            if (soundEnabled) {
                if (overScan) soundEngine.warning() else soundEngine.confirm()
            }
            // Judged from the same running base, so the "line just became exact" pulse fires on
            // the scan that actually completes the line rather than on a stale reading.
            val wasExactNow = LineStatus.of(base, matchedLine.expected) == LineStatus.EXACT
            if (!wasExactNow && newStatus == LineStatus.EXACT && hapticEnabled) hapticEngine.confirm()
            if (overScan) {
                overScanWarning = OverScanInfo(matchedLine.item.no, matchedLine.item.name, barcode, matchedLine.expected, newScanned)
            }
            if (parsedUom != null && parsedUom != matchedLine.unitOfMeasureCode) {
                uomMismatchWarning = UomMismatchInfo(matchedLine.item.no, matchedLine.item.name, barcode, matchedLine.unitOfMeasureCode, parsedUom)
            }
        }
    }

    val latestHandleScan = rememberUpdatedState(::handleScan)
    DisposableEffect(Unit) {
        // The receiver is registered once for the screen's lifetime, so it must call through
        // latestHandleScan rather than closing over handleScan directly — otherwise it would
        // keep calling the very first composition's handleScan forever, evaluating every scan
        // after the first against a stale `doc` snapshot (wrong over-scan/UoM warnings).
        val receiver = DataWedgeManager.createReceiver { barcode -> latestHandleScan.value(barcode) }
        DataWedgeManager.register(context, receiver)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val scanBarBg by animateColorAsState(
        targetValue = if (scanErrorFlash) AppSignal.DangerDeep else AppPalette.Slate,
        animationSpec = tween(durationMillis = 300),
        label = "scanBarBg",
    )

    fun handleBack() {
        when (view) {
            RecordingView.OVERVIEW -> onBack()
            RecordingView.ACTIVE_LINE -> { view = RecordingView.OVERVIEW; activeLineNo = null }
            RecordingView.KEYPAD -> view = RecordingView.ACTIVE_LINE
        }
    }

    BackHandler { handleBack() }

    // A line added on the device disappears once its quantity is set to 0. Without this the
    // screen would stay on a line that no longer exists, showing nothing.
    LaunchedEffect(activeLine == null, view) {
        if (activeLine == null && view != RecordingView.OVERVIEW) {
            view = RecordingView.OVERVIEW
            activeLineNo = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(AppPalette.Cream)) {
        AppTopBar(
            title = when (view) {
                // The document number alone: it is what the operator is working on, and the
                // screen they came from already said this is recording.
                RecordingView.OVERVIEW -> doc.documentNo
                RecordingView.ACTIVE_LINE, RecordingView.KEYPAD -> activeLine?.item?.no ?: doc.documentNo
            },
            subtitle = when (view) {
                RecordingView.OVERVIEW -> buildString {
                    append(stringResource(R.string.recording_lines_progress, doc.linesExact, doc.linesTotal))
                    if (docTypeCode.isNotBlank()) append(" · $docTypeCode")
                }
                RecordingView.ACTIVE_LINE -> null
                RecordingView.KEYPAD -> null
            },
            onBack = { handleBack() },
            actions = {
                if (showUpload && view == RecordingView.OVERVIEW) {
                    // Same footprint as AppTopBar's back button (60 x TOP_BAR_BUTTON_HEIGHT, 8dp
                    // radius, 24dp icon), just mirrored padding and the Coral fill — so the two
                    // top-bar controls read as a matching pair across the bar.
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp, end = 8.dp)
                            .size(width = 60.dp, height = TOP_BAR_BUTTON_HEIGHT)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppPalette.Coral)
                            .clickable(onClick = onUpload),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.ArrowUpward,
                            contentDescription = stringResource(R.string.btn_upload),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                if (view == RecordingView.ACTIVE_LINE) {
                    activeLine?.let { line ->
                        StatusChip(status = line.status, sizeOffset = sizeOffset)
                        Spacer(Modifier.width(8.dp))
                    }
                }
                if (view == RecordingView.KEYPAD) {
                    activeLine?.let { line ->
                        val parsedTypedQty = typedQty.toDoubleOrNull()
                        val previewStatus = if (parsedTypedQty != null && typedQty.isNotEmpty()) LineStatus.of(parsedTypedQty, line.expected) else line.status
                        val previewLabel = when (previewStatus) {
                            LineStatus.EMPTY   -> if (typedQty.isEmpty()) stringResource(R.string.keypad_is_empty) else stringResource(R.string.keypad_becomes_empty)
                            LineStatus.PARTIAL -> if (typedQty.isEmpty()) stringResource(R.string.keypad_is_partial) else stringResource(R.string.keypad_becomes_partial)
                            LineStatus.EXACT   -> if (typedQty.isEmpty()) stringResource(R.string.keypad_is_exact) else stringResource(R.string.keypad_becomes_exact)
                            LineStatus.OVER    -> if (typedQty.isEmpty()) stringResource(R.string.keypad_is_over) else stringResource(R.string.keypad_becomes_over)
                        }
                        StatusChip(status = previewStatus, sizeOffset = sizeOffset, label = previewLabel.uppercase())
                        Spacer(Modifier.width(8.dp))
                    }
                }
            },
        )

        if (view == RecordingView.OVERVIEW) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppPalette.SlateAlt)
                    .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${doc.sourceCode} · ${doc.destinationCode}",
                        style = monoLabel.copy(color = AppOnDark.TextSecondary, fontSize = (11 + sizeOffset).sp),
                    )
                    val allDone = doc.linesExact == doc.linesTotal && doc.linesTotal > 0
                    Text(
                        "${doc.scannedQty.formatQty()}/${doc.expectedQty.formatQty()}",
                        style = monoLabel.copy(
                            color = if (allDone) LineStatus.EXACT.color else Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = (11 + sizeOffset).sp,
                        ),
                    )
                }
                Spacer(Modifier.height(8.dp))
                StatusProgressBar(segments = doc.lines.map { it.status })
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (view) {
                RecordingView.OVERVIEW -> OverviewContent(
                    doc = doc,
                    listState = overviewListState,
                    highlightedLineNo = highlightedLineNo,
                    highlightAlpha = { highlight.value },
                    onLineTap = { line -> activeLineNo = line.lineNo; localScanned = null; typedQty = ""; view = RecordingView.ACTIVE_LINE },
                )
                RecordingView.ACTIVE_LINE -> activeLine?.let { line ->
                    val displayLine = localScanned?.let { line.copy(scanned = it) } ?: line
                    ItemQtyDetails(
                        line = displayLine,
                        onIncrement = { if (hapticEnabled) hapticEngine.bump(); localScanned = ((localScanned ?: line.scanned) + 1.0).coerceAtLeast(line.sentQuantity) },
                        // Stops at the ERP-accepted quantity, not at zero. Below that there is
                        // nothing the device can give back, so the stepper simply doesn't go
                        // there — the operator finds the floor by feel instead of by rejection.
                        onDecrement = {
                            val next = (localScanned ?: line.scanned) - 1.0
                            if (next < line.sentQuantity && line.hasSentQuantity) {
                                sentFloorWarning = SentFloorInfo(line.item.no, line.sentQuantity, line.unitOfMeasureCode)
                            } else {
                                if (hapticEnabled) hapticEngine.bump()
                                localScanned = next.coerceAtLeast(line.sentQuantity)
                            }
                        },
                        onTypeQuantity = { typedQty = ""; view = RecordingView.KEYPAD },
                        // A manual edit replaces the line's queued total outright and can move it
                        // down, so the running base has to go with it.
                        onApply = {
                            if (hapticEnabled) hapticEngine.confirm()
                            scannedRunningTotal.remove(line.lineNo)
                            onLineUpdate(line.lineNo, localScanned ?: line.scanned) { sent ->
                                sentFloorWarning = SentFloorInfo(line.item.no, sent, line.unitOfMeasureCode)
                            }
                            view = RecordingView.OVERVIEW
                            activeLineNo = null
                        },
                    )
                }
                RecordingView.KEYPAD -> activeLine?.let { line ->
                    val displayLine = localScanned?.let { line.copy(scanned = it) } ?: line
                    ItemQtyExtraDetails(
                        line = displayLine,
                        typed = typedQty,
                        onKey = { k ->
                            if (hapticEnabled) hapticEngine.tick()
                            when (k) {
                                "C" -> typedQty = ""
                                "X" -> typedQty = typedQty.dropLast(1)
                                "." -> if (!typedQty.contains('.')) typedQty += if (typedQty.isEmpty()) "0." else "."
                                else -> {
                                    val next = typedQty + k
                                    val cleaned = if (next.startsWith("0") && next.length > 1 && next[1] != '.') next.trimStart('0').ifEmpty { "0" } else next
                                    if (cleaned.substringAfter('.', "").length <= 5) typedQty = cleaned
                                }
                            }
                        },
                        onConfirm = {
                            val qty = typedQty.toDoubleOrNull()?.coerceAtLeast(0.0) ?: line.scanned
                            // The keypad can type any number, so this is where a below-floor edit
                            // is actually caught. Refused, not clamped: quietly writing a number
                            // the operator didn't type would read as the app agreeing with them.
                            if (qty < line.sentQuantity) {
                                sentFloorWarning = SentFloorInfo(line.item.no, line.sentQuantity, line.unitOfMeasureCode)
                            } else {
                                if (hapticEnabled) hapticEngine.confirm()
                                scannedRunningTotal.remove(line.lineNo)
                                onLineUpdate(line.lineNo, qty) { sent ->
                                    sentFloorWarning = SentFloorInfo(line.item.no, sent, line.unitOfMeasureCode)
                                }
                                if (warnOnOver && qty > line.expected && warnsOnOver(line)) {
                                    overScanWarning = OverScanInfo(line.item.no, line.item.name, line.barcodeNo, line.expected, qty)
                                }
                                localScanned = null
                                activeLineNo = null
                                view = RecordingView.OVERVIEW
                            }
                        },
                        onConfirmRequired = {
                            if (line.expected < line.sentQuantity) {
                                sentFloorWarning = SentFloorInfo(line.item.no, line.sentQuantity, line.unitOfMeasureCode)
                            } else {
                                if (hapticEnabled) hapticEngine.confirm()
                                scannedRunningTotal.remove(line.lineNo)
                                onLineUpdate(line.lineNo, line.expected) { sent ->
                                    sentFloorWarning = SentFloorInfo(line.item.no, sent, line.unitOfMeasureCode)
                                }
                                localScanned = null
                                activeLineNo = null
                                view = RecordingView.OVERVIEW
                            }
                        },
                    )
                }
            }
        }

        if (view == RecordingView.OVERVIEW) {
            ScanBar(
                onScan = { handleScan(it) },
                onCameraTap = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED) cameraOpen = true
                    else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                showCamera = hasCamera && !hasScanner,
                containerColor = scanBarBg,
            )
        }
    }
    if (cameraOpen) {
        CameraPreview(
            onBarcode = { barcode -> handleScan(barcode) },
            onClose = { cameraOpen = false },
        )
    }
    } // end Box


    barcodeNotFoundError?.let { barcode ->
        BarcodeNotFoundDialog(barcode, onDismiss = { barcodeNotFoundError = null })
    }

    notOnDocumentScan?.let { scan ->
        NotOnDocumentDialog(
            scan = scan,
            onCount = {
                notOnDocumentScan = null
                onScanNotOnDocument(scan.barcode, scan.unitOfMeasureCode, scan.quantity)
            },
            onDismiss = { notOnDocumentScan = null },
        )
    }

    uomMismatchWarning?.let { info ->
        UomMismatchDialog(info, onDismiss = { uomMismatchWarning = null })
    }

    // Composed last, so it sits on top when a scan raises more than one warning at once. Only a
    // BARCODE|UOM|QTY label can do that — it carries a unit of measure to disagree about and a
    // quantity large enough to overshoot in the same scan — and previously the UoM dialog was
    // composed after this one and covered it, which read as "over-scan is never reported".
    // Over-scan is the more consequential of the two, so it gets the front.
    overScanWarning?.let { info ->
        OverScanDialog(info, onDismiss = { overScanWarning = null })
    }

    // Composed after the over-scan dialog: a single edit cannot raise both, so nothing is being
    // ranked here — it just has to sit above the keypad it was raised from.
    sentFloorWarning?.let { info ->
        SentFloorDialog(info, onDismiss = { sentFloorWarning = null })
    }

}
