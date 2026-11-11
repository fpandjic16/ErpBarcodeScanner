package com.erpbarcode.scanner.ui.screen

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.erpbarcode.scanner.data.model.DocumentFilter
import com.erpbarcode.scanner.data.model.DocumentType
import com.erpbarcode.scanner.data.model.LineStatus
import com.erpbarcode.scanner.data.model.bgColor
import com.erpbarcode.scanner.data.model.color
import com.erpbarcode.scanner.data.model.inkColor
import com.erpbarcode.scanner.ui.component.AppTopBar
import com.erpbarcode.scanner.ui.component.verticalScrollbar
import com.erpbarcode.scanner.ui.theme.AppPalette
import com.erpbarcode.scanner.ui.theme.monoLabel
import com.erpbarcode.scanner.ui.theme.uppercased
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.ui.theme.AppLine

private val filterDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private fun Instant.toLocalDate(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()

private enum class FilterDateTarget { FROM, TO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentFilterScreen(
    initialFilter: DocumentFilter = DocumentFilter(),
    lockedSourceCode: String? = null,
    lockedRcCode: String? = null,
    showDocTypeFilter: Boolean = true,
    onApply: (DocumentFilter) -> Unit,
    onBack: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var dateFrom        by remember { mutableStateOf(initialFilter.dateFrom) }
    var dateTo          by remember { mutableStateOf(initialFilter.dateTo) }
    var selectedStates  by remember { mutableStateOf(initialFilter.states) }
    var selectedTypes   by remember { mutableStateOf(initialFilter.types) }
    var destinationCode by remember { mutableStateOf(initialFilter.destinationCode) }
    var sourceCode      by remember { mutableStateOf(lockedSourceCode ?: initialFilter.sourceCode) }
    var rcCode          by remember { mutableStateOf(lockedRcCode ?: initialFilter.rcCode) }
    var dateTarget      by remember { mutableStateOf<FilterDateTarget?>(null) }

    LaunchedEffect(Unit) {
        // Same fix as DownloadFilterScreen: the doc-list screen's ScanField deliberately holds
        // focus (and sometimes the IME) for the hardware scanner, which can carry over onto this
        // screen's first field once ScanField's EditText is disposed. Reset on entry.
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    fun buildFilter() = DocumentFilter(
        dateFrom        = dateFrom,
        dateTo          = dateTo,
        states          = selectedStates,
        types           = selectedTypes,
        destinationCode = destinationCode.trim(),
        sourceCode      = sourceCode.trim(),
        rcCode          = rcCode.trim(),
    )

    Column(modifier = Modifier
        .fillMaxSize()
        .background(AppPalette.Cream)
        .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus(); keyboardController?.hide() } }
    ) {
        AppTopBar(
            title    = stringResource(R.string.doc_filter_title),
            subtitle = stringResource(R.string.doc_filter_subtitle),
            onBack   = onBack,
        )

        val filterScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScrollbar(filterScrollState)
                .verticalScroll(filterScrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {

            // ── Status ────────────────────────────────────────────────────
            FilterSection(label = stringResource(R.string.doc_filter_status)) {
                // Two rows of two rather than one row of four — the longest label ("Over-qty",
                // "PREKORAČENJE" in Croatian) doesn't fit four across and used to wrap the row
                // raggedly over several lines. Equal weights keep the four chips a tidy grid.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LineStatus.entries.chunked(2).forEach { rowStatuses ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            rowStatuses.forEach { ls ->
                                val selected = ls in selectedStates
                                val bg = if (selected) ls.inkColor else ls.bgColor
                                val fg = if (selected) Color.White else ls.inkColor
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(bg)
                                        .clickable {
                                            selectedStates = if (selected) selectedStates - ls else selectedStates + ls
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        ls.localizedLabel().uppercased,
                                        style = monoLabel.copy(color = fg, fontWeight = FontWeight.Medium),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Document type ─────────────────────────────────────────────────
            if (showDocTypeFilter) {
                FilterSection(label = stringResource(R.string.doc_filter_type)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .border(1.dp, AppLine.CardBorder, RoundedCornerShape(8.dp)),
                    ) {
                        DocumentType.entries.forEachIndexed { idx, type ->
                            val selected = type in selectedTypes
                            if (idx > 0) HorizontalDivider(color = AppLine.Divider, thickness = 1.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTypes = if (selected) selectedTypes - type else selectedTypes + type
                                    }
                                    .padding(start = 8.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = AppPalette.Slate),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    type.localizedDisplay(),
                                    style = monoLabel.copy(
                                        color = if (selected) AppPalette.Ink else AppPalette.Ink3,
                                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            // ── Date downloaded ───────────────────────────────────────────────────
            FilterSection(label = stringResource(R.string.filter_document_date)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterDateChip(
                        label   = stringResource(R.string.filter_date_from),
                        value   = dateFrom?.format(filterDateFmt),
                        onClick = { dateTarget = FilterDateTarget.FROM },
                        onClear = { dateFrom = null },
                        modifier = Modifier.weight(1f),
                    )
                    FilterDateChip(
                        label   = stringResource(R.string.filter_date_to),
                        value   = dateTo?.format(filterDateFmt),
                        onClick = { dateTarget = FilterDateTarget.TO },
                        onClear = { dateTo = null },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

        }

        // ── Bottom action bar ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f).height(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, AppLine.Outline, RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .clickable {
                        dateFrom = null; dateTo = null
                        selectedStates  = emptySet()
                        selectedTypes   = emptySet()
                        destinationCode = ""
                        sourceCode      = lockedSourceCode ?: ""
                        rcCode          = lockedRcCode ?: ""
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.btn_reset).uppercased, style = monoLabel.copy(color = AppPalette.Ink, fontWeight = FontWeight.Medium))
            }
            Box(
                modifier = Modifier
                    .weight(1f).height(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppPalette.Coral)
                    .clickable { onApply(buildFilter()) },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.btn_apply).uppercased, style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium))
            }
        }
    }

    // ── Date picker dialogs ─────────────────────────────────────────────────────────────
    dateTarget?.let { target ->
        val state = rememberDatePickerState(
            initialSelectedDateMillis = when (target) {
                FilterDateTarget.FROM -> dateFrom?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                FilterDateTarget.TO   -> dateTo?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            }
        )
        DatePickerDialog(
            onDismissRequest = { dateTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).toLocalDate()
                        when (target) {
                            FilterDateTarget.FROM -> {
                                dateFrom = picked
                                if (dateTo != null && dateTo!!.isBefore(picked)) dateTo = picked
                            }
                            FilterDateTarget.TO -> {
                                dateTo = picked
                                if (dateFrom != null && dateFrom!!.isAfter(picked)) dateFrom = picked
                            }
                        }
                    }
                    dateTarget = null
                }) { Text(stringResource(R.string.btn_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { dateTarget = null }) { Text(stringResource(R.string.btn_cancel)) }
            },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun FilterSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label.uppercased, style = monoLabel.copy(color = AppPalette.Ink3, fontWeight = FontWeight.Medium))
        content()
    }
}

@Composable
private fun FilterDateChip(
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

