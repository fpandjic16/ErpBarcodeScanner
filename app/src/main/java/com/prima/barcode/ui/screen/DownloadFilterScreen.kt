package com.prima.barcode.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.prima.barcode.data.model.DocumentType
import com.prima.barcode.data.model.DownloadFilter
import com.prima.barcode.data.model.InventoryBatch
import com.prima.barcode.data.model.Location
import com.prima.barcode.data.model.ResponsibilityCenter
import com.prima.barcode.ui.component.PrimaTopBar
import com.prima.barcode.ui.component.verticalScrollbar
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.monoLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.prima.barcode.R

private val dlDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private fun Instant.toLocalDateDl(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()

private enum class DlDateTarget { FROM, TO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadFilterScreen(
    hasCredentials: Boolean = false,
    loginQrKey: String = "",
    hapticEnabled: Boolean = true,
    soundEnabled: Boolean = true,
    docType: DocumentType? = null,
    fixedSourceCode: String? = null,
    fixedRcCode: String? = null,
    locations: List<Location> = emptyList(),
    rcs: List<ResponsibilityCenter> = emptyList(),
    onConfirm: (filter: DownloadFilter, username: String?, password: String?) -> Unit,
    onTestConnection: (username: String, password: String, onResult: (success: Boolean, error: String?) -> Unit) -> Unit = { _, _, cb -> cb(true, null) },
    /** A stock count's batches for the filter's scope: the list, or else an error message. */
    onLoadInventoryBatches: (
        filter: DownloadFilter,
        username: String?,
        password: String?,
        onResult: (batches: List<InventoryBatch>?, error: String?) -> Unit,
    ) -> Unit = { _, _, _, cb -> cb(emptyList(), null) },
    onCancel: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // A stock count downloads one batch the operator picks, so a date range means nothing here.
    val stockCount = docType?.isStockCount == true
    // Local, because hasCredentials is read once: after signing in on the sheet below, this
    // screen would otherwise keep asking for the sign-in again.
    var signedIn        by remember { mutableStateOf(hasCredentials) }
    var batches         by remember { mutableStateOf<List<InventoryBatch>?>(null) }
    var batchesLoading  by remember { mutableStateOf(false) }
    var batchesError    by remember { mutableStateOf<String?>(null) }
    var selectedBatchNo by remember { mutableStateOf<String?>(null) }

    var dateFrom        by remember { mutableStateOf<LocalDate?>(null) }
    var dateTo          by remember { mutableStateOf<LocalDate?>(null) }
    var sourceCode      by remember { mutableStateOf(fixedSourceCode ?: "") }
    var rcCode          by remember { mutableStateOf(fixedRcCode ?: "") }
    var dateTarget      by remember { mutableStateOf<DlDateTarget?>(null) }
    var showLogin       by remember { mutableStateOf(false) }
    var showSourceSheet by remember { mutableStateOf(false) }
    var showRcPickSheet by remember { mutableStateOf(false) }
    val selectedLocation = remember(sourceCode, locations) { locations.find { it.code == sourceCode } }
    val selectedRc       = remember(rcCode, rcs) { rcs.find { it.code == rcCode } }

    LaunchedEffect(Unit) {
        // The doc-list screen's ScanField deliberately holds focus (and, if the user tapped
        // its keyboard icon, the IME) so the hardware scanner works without tapping first. If
        // the user leaves it mid-edit by tapping DOWNLOAD, that focus/keyboard state can carry
        // over onto this screen's first field once ScanField's EditText is disposed. Clear both
        // explicitly on entry so the form always opens with nothing focused and no keyboard up.
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (!hasCredentials) showLogin = true
    }

    // No destinationCode — the destination filter was dropped from this screen, so the field
    // stays at DownloadFilter's default and no Destination_No clause is sent.
    fun currentFilter() = DownloadFilter(
        dateFrom   = if (stockCount) null else dateFrom,
        dateTo     = if (stockCount) null else dateTo,
        sourceCode = sourceCode.trim(),
        rcCode     = rcCode.trim(),
        documentNo = if (stockCount) selectedBatchNo.orEmpty() else "",
    )

    fun loadBatches(username: String?, password: String?) {
        batchesLoading = true
        batchesError = null
        onLoadInventoryBatches(currentFilter(), username, password) { loaded, error ->
            batchesLoading = false
            batches = loaded
            batchesError = error
            if (loaded?.none { it.documentNo == selectedBatchNo } != false) selectedBatchNo = null
        }
    }

    LaunchedEffect(Unit) {
        if (stockCount && hasCredentials) loadBatches(null, null)
    }

    fun onOkClick() {
        if (!signedIn) showLogin = true
        else if (!stockCount || selectedBatchNo != null) onConfirm(currentFilter(), null, null)
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(PrimaPalette.Cream)
        .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus(); keyboardController?.hide() } }
    ) {
        PrimaTopBar(
            title    = stringResource(R.string.download_title),
            subtitle = docType?.localizedDisplay() ?: stringResource(R.string.download_subtitle),
            onBack   = onCancel,
        )

        val dlScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScrollbar(dlScrollState)
                .verticalScroll(dlScrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (stockCount) DlFilterSection(label = stringResource(R.string.download_inventory_batch)) {
                InventoryBatchList(
                    batches = batches,
                    loading = batchesLoading,
                    error = batchesError,
                    selectedDocumentNo = selectedBatchNo,
                    onSelect = { selectedBatchNo = it.documentNo },
                    onRetry = { if (signedIn) loadBatches(null, null) else showLogin = true },
                )
            } else DlFilterSection(label = stringResource(R.string.filter_document_date)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DlDateChip(
                        label    = stringResource(R.string.filter_date_from),
                        value    = dateFrom?.format(dlDateFmt),
                        onClick  = { dateTarget = DlDateTarget.FROM },
                        onClear  = { dateFrom = null },
                        modifier = Modifier.weight(1f),
                    )
                    DlDateChip(
                        label    = stringResource(R.string.filter_date_to),
                        value    = dateTo?.format(dlDateFmt),
                        onClick  = { dateTarget = DlDateTarget.TO },
                        onClear  = { dateTo = null },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (fixedRcCode == null) DlFilterSection(label = stringResource(R.string.filter_source_code)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .then(if (fixedSourceCode != null) Modifier.alpha(0.5f) else Modifier),
                ) {
                    LrcSelectionRow(
                        avatar = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selectedLocation != null) PrimaPalette.Teal else PrimaPalette.CreamAlt),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = if (selectedLocation != null) Color.White else PrimaPalette.Ink3,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        title = selectedLocation?.code ?: stringResource(R.string.lrc_location_placeholder),
                        subtitle = selectedLocation?.name,
                        hasValue = sourceCode.isNotEmpty(),
                        showChevron = fixedSourceCode == null,
                        onClick = { if (fixedSourceCode == null) showSourceSheet = true },
                    )
                }
            }

            if (fixedSourceCode == null) DlFilterSection(label = stringResource(R.string.filter_responsibility_center)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .then(if (fixedRcCode != null) Modifier.alpha(0.5f) else Modifier),
                ) {
                    LrcSelectionRow(
                        avatar = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selectedRc != null) PrimaPalette.Slate else PrimaPalette.CreamAlt),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = selectedRc?.let { it.short ?: it.code.take(2) } ?: "—",
                                    style = monoLabel.copy(
                                        color = if (selectedRc != null) Color.White else PrimaPalette.Ink3,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                            }
                        },
                        title = selectedRc?.name ?: stringResource(R.string.lrc_rc_placeholder),
                        subtitle = selectedRc?.code,
                        hasValue = rcCode.isNotEmpty(),
                        showChevron = fixedRcCode == null,
                        onClick = { if (fixedRcCode == null) showRcPickSheet = true },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = {
                    dateFrom = null; dateTo = null
                    sourceCode = fixedSourceCode ?: ""
                    rcCode = fixedRcCode ?: ""
                    selectedBatchNo = null
                },
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text(stringResource(R.string.btn_reset), style = monoLabel.copy(fontWeight = FontWeight.Medium)) }
            Button(
                onClick = ::onOkClick,
                // Signed out, OK is how the sign-in is reached, so it stays enabled.
                enabled = !stockCount || !signedIn || selectedBatchNo != null,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaPalette.Coral),
            ) { Text(stringResource(R.string.btn_ok), style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium)) }
        }
    }

    dateTarget?.let { target ->
        val state = rememberDatePickerState(
            initialSelectedDateMillis = when (target) {
                DlDateTarget.FROM -> dateFrom?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                DlDateTarget.TO   -> dateTo?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            }
        )
        DatePickerDialog(
            onDismissRequest = { dateTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).toLocalDateDl()
                        when (target) {
                            DlDateTarget.FROM -> {
                                dateFrom = picked
                                if (dateTo != null && dateTo!!.isBefore(picked)) dateTo = picked
                            }
                            DlDateTarget.TO -> {
                                dateTo = picked
                                if (dateFrom != null && dateFrom!!.isAfter(picked)) dateFrom = picked
                            }
                        }
                    }
                    dateTarget = null
                }) { Text(stringResource(R.string.btn_ok)) }
            },
            dismissButton = { TextButton(onClick = { dateTarget = null }) { Text(stringResource(R.string.btn_cancel)) } },
        ) { DatePicker(state = state) }
    }

    if (showSourceSheet) {
        LocationPickerSheet(
            locations            = locations,
            selectedLocationCode = sourceCode,
            onSelect             = { loc -> sourceCode = loc.code; showSourceSheet = false },
            onDismiss            = { showSourceSheet = false },
        )
    }

    if (showRcPickSheet) {
        RcPickerSheet(
            availableRcs   = rcs,
            selectedRcCode = rcCode,
            onSelect       = { rc -> rcCode = rc.code; showRcPickSheet = false },
            onClear        = { rcCode = ""; showRcPickSheet = false },
            onDismiss      = { showRcPickSheet = false },
        )
    }

    if (showLogin) {
        LoginSheet(
            loginQrKey = loginQrKey,
            hapticEnabled = hapticEnabled,
            soundEnabled = soundEnabled,
            onTestConnection = onTestConnection,
            onSubmit = { username, password ->
                showLogin = false
                signedIn = true
                // A stock count cannot download yet: the batch has to be picked first, from a
                // list only the sign-in makes available.
                if (stockCount) loadBatches(username, password)
                else onConfirm(currentFilter(), username, password)
            },
            onDismiss = { showLogin = false },
        )
    }
}

@Composable
private fun DlFilterSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = monoLabel.copy(color = PrimaPalette.Ink3, fontWeight = FontWeight.Medium))
        content()
    }
}

/** null [batches] means not loaded yet, which is not the same as an empty list. */
@Composable
private fun InventoryBatchList(
    batches: List<InventoryBatch>?,
    loading: Boolean,
    error: String?,
    selectedDocumentNo: String?,
    onSelect: (InventoryBatch) -> Unit,
    onRetry: () -> Unit,
) {
    val messageStyle = MaterialTheme.typography.bodyMedium.copy(color = PrimaPalette.Ink3)
    val resources = LocalContext.current.resources
    when {
        loading -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.download_inventory_batches_loading), style = messageStyle)
        }
        error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(error, style = messageStyle.copy(color = PrimaPalette.Coral))
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.download_inventory_batches_retry)) }
        }
        batches == null -> OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.download_inventory_batches_retry)) }
        batches.isEmpty() -> Text(stringResource(R.string.download_inventory_batches_empty), style = messageStyle)
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            batches.forEach { batch ->
                val selected = batch.documentNo == selectedDocumentNo
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) PrimaPalette.Teal else Color.White)
                        .border(1.dp, if (selected) PrimaPalette.Teal else Color(0x28000000), RoundedCornerShape(8.dp))
                        .clickable { onSelect(batch) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        listOf(batch.journalTemplateName, batch.journalBatchName)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                            .ifEmpty { batch.documentNo },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) Color.White else PrimaPalette.Ink,
                        ),
                    )
                    Text(
                        listOfNotNull(
                            batch.documentNo,
                            batch.documentDate?.format(dlDateFmt),
                            resources.getQuantityString(
                                R.plurals.download_inventory_batch_lines, batch.lineCount, batch.lineCount,
                            ),
                        ).joinToString(" · "),
                        style = monoLabel.copy(color = if (selected) Color(0xC5FFFFFF) else PrimaPalette.Ink3),
                    )
                }
            }
        }
    }
}

@Composable
private fun DlDateChip(
    label: String,
    value: String?,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color(0x28000000), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.CalendarMonth, null, tint = PrimaPalette.Ink3, modifier = Modifier.size(15.dp))
        Text(
            text = value ?: label,
            style = monoLabel.copy(color = if (value != null) PrimaPalette.Ink else PrimaPalette.Ink3),
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Icon(
                Icons.Outlined.Clear,
                contentDescription = stringResource(R.string.cd_clear),
                tint = PrimaPalette.Ink3,
                modifier = Modifier.size(14.dp).clickable(onClick = onClear),
            )
        }
    }
}


