package com.erpbarcode.scanner.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
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
import com.erpbarcode.scanner.data.model.DocumentType
import com.erpbarcode.scanner.data.model.DownloadFilter
import com.erpbarcode.scanner.data.model.InventoryBatch
import com.erpbarcode.scanner.data.model.Location
import com.erpbarcode.scanner.data.model.ResponsibilityCenter
import com.erpbarcode.scanner.ui.component.AppTopBar
import com.erpbarcode.scanner.ui.component.verticalScrollbar
import com.erpbarcode.scanner.ui.theme.AppPalette
import com.erpbarcode.scanner.ui.theme.monoLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.ui.theme.AppLine

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
    var showBatchSheet  by remember { mutableStateOf(false) }
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

    // Asked again whenever the scope changes: the list is the batches of this location or centre.
    LaunchedEffect(sourceCode, rcCode) {
        if (stockCount && signedIn) loadBatches(null, null)
    }

    fun onOkClick() {
        if (!signedIn) showLogin = true
        else if (!stockCount || selectedBatchNo != null) onConfirm(currentFilter(), null, null)
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(AppPalette.Cream)
        .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus(); keyboardController?.hide() } }
    ) {
        AppTopBar(
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
                InventoryBatchSelector(
                    selected = batches?.find { it.documentNo == selectedBatchNo },
                    loaded = batches != null,
                    loading = batchesLoading,
                    error = batchesError,
                    onOpen = { showBatchSheet = true },
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
                                    .background(if (selectedLocation != null) AppPalette.Teal else AppPalette.CreamAlt),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = if (selectedLocation != null) Color.White else AppPalette.Ink3,
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
                                    .background(if (selectedRc != null) AppPalette.Slate else AppPalette.CreamAlt),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = selectedRc?.let { it.short ?: it.code.take(2) } ?: "—",
                                    style = monoLabel.copy(
                                        color = if (selectedRc != null) Color.White else AppPalette.Ink3,
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
                colors = ButtonDefaults.buttonColors(containerColor = AppPalette.Coral),
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

    if (showBatchSheet) {
        InventoryBatchPickerSheet(
            batches            = batches.orEmpty(),
            selectedDocumentNo = selectedBatchNo,
            loading            = batchesLoading,
            error              = batchesError,
            onRefresh          = { loadBatches(null, null) },
            onSelect           = { batch -> selectedBatchNo = batch.documentNo; showBatchSheet = false },
            onDismiss          = { showBatchSheet = false },
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
        Text(label, style = monoLabel.copy(color = AppPalette.Ink3, fontWeight = FontWeight.Medium))
        content()
    }
}

/** Journal template · batch, or the document no. when the ERP sends neither. */
private val InventoryBatch.title: String
    get() = listOf(journalTemplateName, journalBatchName)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifEmpty { documentNo }

/** Description · document no. · date · number of lines. */
@Composable
private fun InventoryBatch.details(): String = listOfNotNull(
    description.takeIf { it.isNotBlank() },
    documentNo,
    documentDate?.format(dlDateFmt),
    LocalContext.current.resources.getQuantityString(R.plurals.download_inventory_batch_lines, lineCount, lineCount),
).joinToString(" · ")

@Composable
private fun InventoryBatchAvatar(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AppPalette.Teal else AppPalette.CreamAlt),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Inventory,
            contentDescription = null,
            tint = if (selected) Color.White else AppPalette.Ink3,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The batch picker row, built like the location row above it: it shows the pick and opens a
 * sheet to change it.
 *
 * [loaded] false means the list has not come back yet, which is not the same as an empty list.
 * Tapping then asks for it again, which also covers an operator who is not signed in yet.
 */
@Composable
private fun InventoryBatchSelector(
    selected: InventoryBatch?,
    loaded: Boolean,
    loading: Boolean,
    error: String?,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White),
        ) {
            LrcSelectionRow(
                avatar = {
                    if (loading) {
                        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        InventoryBatchAvatar(selected = selected != null)
                    }
                },
                title = when {
                    selected != null -> selected.title
                    loading -> stringResource(R.string.download_inventory_batches_loading)
                    else -> stringResource(R.string.download_inventory_batch_placeholder)
                },
                subtitle = selected?.details(),
                hasValue = selected != null,
                onClick = {
                    when {
                        loading -> Unit
                        error != null || !loaded -> onRetry()
                        else -> onOpen()
                    }
                },
            )
        }
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodyMedium.copy(color = AppPalette.Coral))
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.download_inventory_batches_retry)) }
        }
    }
}

/**
 * The same sheet as [LocationPickerSheet]: search on top, the batches below, the pick ticked.
 *
 * A refresh button beside the search asks the ERP again without leaving the sheet, for a batch
 * the office has just sent while the operator was already looking at the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryBatchPickerSheet(
    batches: List<InventoryBatch>,
    selectedDocumentNo: String?,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onSelect: (InventoryBatch) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, batches) {
        if (query.isBlank()) batches
        else batches.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.documentNo.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppPalette.Cream,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    LrcSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(R.string.lrc_search_placeholder),
                    )
                }
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.lrc_refresh_list),
                                tint = AppPalette.Ink2,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            if (error != null) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium.copy(color = AppPalette.Coral),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (batches.isEmpty()) stringResource(R.string.download_inventory_batches_empty)
                        else stringResource(R.string.lrc_no_results),
                        style = monoLabel.copy(color = AppPalette.Ink3),
                    )
                }
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.White)
                        .verticalScrollbar(listState),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    state = listState,
                ) {
                    itemsIndexed(filtered, key = { _, batch -> batch.documentNo }) { index, batch ->
                        InventoryBatchSelectRow(
                            batch = batch,
                            selected = batch.documentNo == selectedDocumentNo,
                            onClick = { onSelect(batch) },
                        )
                        if (index < filtered.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                color = AppLine.Divider,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryBatchSelectRow(batch: InventoryBatch, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InventoryBatchAvatar(selected = selected)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                batch.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = AppPalette.Ink,
                ),
            )
            Text(batch.details(), style = monoLabel.copy(color = AppPalette.Ink3))
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = AppPalette.Teal,
                modifier = Modifier.size(20.dp),
            )
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
            .border(1.dp, AppLine.InputBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            // 48 dp tall, and no right padding: the clear button below brings its own 48 dp.
            // Until 3.6.29 this was 10 dp all round, about 36 dp tall, with a 14 dp clear icon.
            .heightIn(min = 48.dp)
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.CalendarMonth, null, tint = AppPalette.Ink3, modifier = Modifier.size(15.dp))
        Text(
            text = value ?: label,
            style = monoLabel.copy(color = if (value != null) AppPalette.Ink else AppPalette.Ink3),
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            // The icon stays small; the target around it is 48 dp, which is what a gloved thumb
            // needs to hit it rather than the chip behind it.
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Clear,
                    contentDescription = stringResource(R.string.cd_clear),
                    tint = AppPalette.Ink3,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Spacer(Modifier.width(4.dp))
        }
    }
}


