package com.prima.barcode.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prima.barcode.data.model.DocState
import com.prima.barcode.data.model.DocTypeFilterMode
import com.prima.barcode.data.model.Document
import com.prima.barcode.data.model.DocumentFilter
import com.prima.barcode.data.model.LineStatus
import com.prima.barcode.data.model.color
import com.prima.barcode.data.model.scanStatus
import com.prima.barcode.ui.component.PrimaTopBar
import com.prima.barcode.ui.component.verticalScrollbar
import com.prima.barcode.ui.component.StatusProgressBar
import com.prima.barcode.ui.theme.LocalTextSizeOffset
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.PrimaStatus
import com.prima.barcode.ui.theme.monoLabel
import com.prima.barcode.ui.theme.uppercased
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import com.prima.barcode.R

private val dashDateFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault())

private fun Instant.toLocalDate(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
fun DocumentOverviewScreen(
    locationCode: String,
    rcCode: String,
    documents: List<Document>,
    onBack: () -> Unit,
    onDocTap: (Document) -> Unit,
    onClearErrors: () -> Unit,
    onUpload: (List<Document>) -> Unit = {},
    onErrorTap: (Document) -> Unit = {},
    filter: DocumentFilter = DocumentFilter(),
    onOpenFilter: (lockedSourceCode: String?, lockedRcCode: String?) -> Unit = { _, _ -> },
    docTypeFilters: Map<String, DocTypeFilterMode> = emptyMap(),
    initialTab: Int = 0,
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var showClearErrorsDialog by remember { mutableStateOf(false) }

    val filtered = remember(documents, filter) {
        documents.filter { doc ->
            val docDate = (doc.documentDate ?: doc.creationDateTime).toLocalDate()
            val dateOk  = (filter.dateFrom == null || !docDate.isBefore(filter.dateFrom)) &&
                          (filter.dateTo   == null || !docDate.isAfter(filter.dateTo))
            val stateOk = filter.states.isEmpty() || doc.scanStatus() in filter.states
            val typeOk  = filter.types.isEmpty() || doc.type in filter.types
            val destOk  = filter.destinationCode.isBlank() || doc.destinationCode.contains(filter.destinationCode, ignoreCase = true)
            val srcOk   = filter.sourceCode.isBlank() || doc.sourceCode.contains(filter.sourceCode, ignoreCase = true)
            val rcOk    = filter.rcCode.isBlank() || doc.rcCode.contains(filter.rcCode, ignoreCase = true)
            dateOk && stateOk && typeOk && destOk && srcOk && rcOk
        }
    }
    val errors     = remember(filtered) { filtered.filter { it.state is DocState.UploadFailed } }
    // The same rule as the document lists: nothing selected, nothing shown. A blank code used to
    // match documents whose own code happened to be blank, so with no centre selected this tab
    // showed exactly the complaints that had none.
    val atLocation = remember(filtered, locationCode, rcCode, docTypeFilters) {
        filtered.filter { doc ->
            when (docTypeFilters[doc.type.key] ?: doc.type.defaultFilterMode) {
                DocTypeFilterMode.LOCATION -> locationCode.isNotEmpty() && doc.sourceCode == locationCode
                DocTypeFilterMode.RESPONSIBILITY_CENTER -> rcCode.isNotEmpty() && doc.rcCode == rcCode
            }
        }
    }

    val tabs = listOf(
        stringResource(R.string.overview_tab_errors) to errors.size,
        stringResource(R.string.overview_tab_my_location) to atLocation.size,
        stringResource(R.string.overview_tab_all) to filtered.size,
    )
    val visibleDocs = when (selectedTab) { 0 -> errors; 1 -> atLocation; else -> filtered }
    val uploadableDocs = remember(visibleDocs) {
        visibleDocs.filter { doc -> doc.lines.any { it.scanned > 0.0 } || doc.needsReview }
    }
    val listState = rememberLazyListState()
    Column(modifier = Modifier.fillMaxSize().background(PrimaPalette.Cream)) {
        PrimaTopBar(
            title = stringResource(R.string.overview_title),
            subtitle = stringResource(R.string.overview_subtitle),
            onBack = onBack,
            actions = {
                IconButton(onClick = {
                    if (selectedTab == 1) {
                        onOpenFilter(locationCode.ifBlank { null }, rcCode.ifBlank { null })
                    } else {
                        onOpenFilter(null, null)
                    }
                }) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = stringResource(R.string.cd_filter),
                        tint = if (filter.isActive) PrimaPalette.Coral else Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )

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
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, style = monoLabel.copy(color = Color.White))
                            if (count > 0) {
                                Box(
                                    Modifier.clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x22FFFFFF))
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                ) {
                                    Text(count.toString(), style = monoLabel.copy(color = Color.White))
                                }
                            }
                        }
                    },
                )
            }
        }
        if (visibleDocs.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = PrimaStatus.Exact, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.overview_no_issues), style = monoLabel.copy(color = PrimaPalette.Ink3))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScrollbar(listState),
                state = listState,
            ) {
                items(visibleDocs, key = { it.documentNo }) { doc ->
                    DashboardDocRow(
                        doc = doc,
                        showErrorDetails = selectedTab == 0,
                        onClick = { if (selectedTab == 0 && doc.state is DocState.UploadFailed) onErrorTap(doc) else onDocTap(doc) },
                    )
                    HorizontalDivider(color = Color(0x0F000000), thickness = 1.dp)
                }
            }
        }
        when {
            selectedTab == 0 && errors.isNotEmpty() -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    OutlinedButton(
                        onClick = { showClearErrorsDialog = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(stringResource(R.string.overview_clear_errors), style = monoLabel.copy(fontWeight = FontWeight.Medium)) }
                }
            }
            selectedTab != 0 && uploadableDocs.isNotEmpty() -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth().height(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (uploadableDocs.isNotEmpty()) PrimaPalette.Coral else PrimaPalette.Coral.copy(alpha = 0.35f))
                            .clickable(enabled = uploadableDocs.isNotEmpty()) { onUpload(uploadableDocs) },
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

    if (showClearErrorsDialog) {
        AlertDialog(
            onDismissRequest = { showClearErrorsDialog = false },
            title = { Text(stringResource(R.string.overview_clear_errors_title), fontWeight = FontWeight.Bold) },
            text = { Text(if (errors.size == 1) stringResource(R.string.overview_clear_errors_text_single, errors.size) else stringResource(R.string.overview_clear_errors_text_plural, errors.size)) },
            confirmButton = {
                Button(
                    onClick = { showClearErrorsDialog = false; onClearErrors() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCE3A3A)),
                ) { Text(stringResource(R.string.btn_clear), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearErrorsDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }
}


@Composable
private fun DashboardDocRow(doc: Document, showErrorDetails: Boolean = false, onClick: () -> Unit) {
    val sizeOffset = LocalTextSizeOffset.current
    val status = doc.scanStatus()
    val statusColor = status.color
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
                        DashScanStatusChip(status)
                        if (doc.state is DocState.UploadFailed) DashStateErrorChip()
                    }
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(top = 2.dp)) {
                    Text(doc.sourceCode, style = monoLabel.copy(color = PrimaPalette.Ink3, fontSize = (12 + sizeOffset).sp))
                    Text(dashDateFmt.format(doc.documentDate ?: doc.creationDateTime), style = monoLabel.copy(color = PrimaPalette.Ink3, fontSize = (12 + sizeOffset).sp))
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
                    style = monoLabel.copy(color = Color(0xFFCE3A3A), fontSize = (12 + sizeOffset).sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DashScanStatusChip(status: LineStatus) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(status.color)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(status.localizedLabel().uppercased, style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun DashStateErrorChip() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFCE3A3A))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(stringResource(R.string.doc_chip_error).uppercased, style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium))
    }
}
