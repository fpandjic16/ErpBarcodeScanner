package com.erpbarcode.scanner.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import androidx.compose.material.icons.outlined.AssignmentReturned
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erpbarcode.scanner.data.auth.ExtSystemEnvironment
import com.erpbarcode.scanner.data.model.Document
import com.erpbarcode.scanner.data.model.DocumentType
import com.erpbarcode.scanner.data.model.LineStatus
import com.erpbarcode.scanner.data.model.Location
import com.erpbarcode.scanner.data.model.ResponsibilityCenter
import com.erpbarcode.scanner.data.model.User
import com.erpbarcode.scanner.ui.component.DocumentStatsDashboard
import com.erpbarcode.scanner.ui.component.AppTopBar
import com.erpbarcode.scanner.ui.component.verticalScrollbar
import com.erpbarcode.scanner.ui.component.StatusProgressBar
import com.erpbarcode.scanner.ui.theme.AppPalette
import com.erpbarcode.scanner.ui.theme.monoLabel
import com.erpbarcode.scanner.ui.theme.uppercased
import androidx.compose.ui.res.stringResource
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.ui.theme.AppSignal
import com.erpbarcode.scanner.ui.theme.AppLine
import com.erpbarcode.scanner.ui.theme.AppOnDark

data class DocTypeSummary(
    val type: DocumentType,
    val count: Int,
    val statusMini: List<LineStatus>,
    val blocked: Boolean = false,
)

@Composable
fun MainMenuScreen(
    user: User?,
    location: Location?,
    rc: ResponsibilityCenter?,
    /** Null until a configuration has said. Hidden rather than guessed at when unknown. */
    environment: ExtSystemEnvironment? = null,
    docTypes: List<DocTypeSummary>,
    /**
     * Documents that already carry scans. Only their number and their scan totals are used here —
     * the list itself lives on its own screen, behind [onOpenRecordings]. Empty is a valid state:
     * the row stays and shows zero.
     */
    recordedDocs: List<Document> = emptyList(),
    shiftScans: Int = 0,
    shiftErrors: Int = 0,
    shiftReady: Int = 0,
    shiftPartial: Int = 0,
    shiftOver: Int = 0,
    onChangeLocationRc: () -> Unit,
    onOpenSettings: () -> Unit,
    /** Where document types are switched on; offered when none is. */
    onOpenExtSystemConfig: () -> Unit = {},
    onTypeTap: (DocumentType) -> Unit,
    onDocumentOverview: () -> Unit,
    onShowErrors: () -> Unit = {},
    onUserInfoTap: () -> Unit = {},
    onOpenRecordings: () -> Unit = {},
) {
    var showBlockedError by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(AppPalette.Cream)) {
        AppTopBar(
            title = user?.displayName ?: stringResource(R.string.settings_not_signed_in),
            leading = {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp, top = 2.dp, bottom = 2.dp, end = 4.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (user != null) AppPalette.Coral else AppOnDark.Button)
                        .clickable(onClick = onUserInfoTap),
                    contentAlignment = Alignment.Center,
                ) {
                    if (user != null) {
                        Text(user.initials, style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Bold))
                    } else {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            },
            actions = {
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Settings, stringResource(R.string.settings_title), tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        )

        val listState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScrollbar(listState),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            state = listState,
        ) {
            item {
                DocumentStatsDashboard(
                    totalScans = shiftScans,
                    errors = shiftErrors,
                    readyForUpload = shiftReady,
                    partial = shiftPartial,
                    over = shiftOver,
                    onDocumentOverview = onDocumentOverview,
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppPalette.SlateAlt)
                        .border(1.dp, AppOnDark.Border, RoundedCornerShape(14.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(onClick = onChangeLocationRc)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            rc?.code ?: "—",
                            style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(AppOnDark.Subtle),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(onClick = onChangeLocationRc)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            location?.code ?: "—",
                            style = monoLabel.copy(
                                color = if (location != null) Color.White else AppOnDark.TextFaint,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Which ERP this device is talking to, where the operator cannot help seeing
                    // it. The two mistakes it guards against are both silent and both expensive:
                    // real work scanned into the test company, and test work scanned into
                    // production. Not tappable — it is not something you switch from here.
                    //
                    // TEST is drawn in the warning amber deliberately, breaking the rule that
                    // those colours mean line status. Nothing else on this screen is coloured to
                    // mean "look at this", and that is exactly what is wanted here.
                    environment?.let { env ->
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(AppOnDark.Subtle),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(env.labelRes),
                                style = monoLabel.copy(
                                    color = when (env) {
                                        ExtSystemEnvironment.TEST -> AppSignal.WarningOnDark
                                        ExtSystemEnvironment.PRODUCTION -> Color.White
                                    },
                                    fontWeight = FontWeight.Medium,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.main_documents_header), style = monoLabel.copy(color = AppPalette.Ink3))
            }
            // Every type starts switched off on a new installation, so this is the first thing a
            // fresh device shows. An empty list under the header would only look broken.
            if (docTypes.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.dp, AppLine.CardBorder, RoundedCornerShape(14.dp))
                            .padding(16.dp),
                    ) {
                        Text(
                            stringResource(R.string.main_no_doc_types_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.main_no_doc_types_text),
                            style = MaterialTheme.typography.bodyMedium.copy(color = AppPalette.Ink3),
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onOpenExtSystemConfig,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text(stringResource(R.string.ext_config_title), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            items(docTypes) { dt ->
                DocumentTypeList(
                    summary = dt,
                    // Blocked types are still tappable — the tap is what earns the explanation.
                    onClick = { if (dt.blocked) showBlockedError = true else onTypeTap(dt.type) },
                )
            }
            // Work in progress across every type, as one row rather than the list itself: a good
            // shift is dozens of documents, and rendering them here pushed the document types —
            // what this screen is for — off the top of the screen.
            //
            // Its own header, the same as DOCUMENTS above it. Without one the row read as an
            // eighth document type, which is the one thing it is not.
            //
            // Shown at zero rather than hidden, so the screen keeps one shape and the row keeps
            // one place on it. "0 scans" is also an answer worth having at the end of a shift —
            // a hidden section says the same thing, but only to someone who knows it can hide.
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.main_recordings_section_header),
                    style = monoLabel.copy(color = AppPalette.Ink3),
                )
            }
            item {
                RecordingsEntryRow(
                    documentCount = recordedDocs.size,
                    scanCount = recordedDocs.sumOf { it.totalScans },
                    onClick = onOpenRecordings,
                )
            }
        }
    }

    if (showBlockedError) {
        AlertDialog(
            onDismissRequest = { showBlockedError = false },
            title = { Text(stringResource(R.string.main_blocked_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.main_blocked_text)) },
            confirmButton = {
                Button(onClick = { showBlockedError = false }) { Text(stringResource(R.string.btn_ok)) }
            },
        )
    }
}

/**
 * The one ZAPISI row. Deliberately shaped like a document-type row, because that is what it is
 * from the operator's side: a thing you tap to get a list.
 */
@Composable
private fun RecordingsEntryRow(documentCount: Int, scanCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, AppLine.CardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppPalette.CreamAlt),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Checklist, contentDescription = null, tint = AppPalette.Slate) }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.main_recordings_header),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.main_recordings_scans, scanCount),
                style = monoLabel.copy(color = AppPalette.Ink3),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            documentCount.toString(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = com.erpbarcode.scanner.ui.theme.GeistMono,
                // Greyed at zero, same as a document-type row, so "nothing here" reads as nothing
                // rather than as a number worth looking at.
                color = if (documentCount > 0) AppPalette.Ink else AppPalette.Ink4,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun DocumentTypeList(summary: DocTypeSummary, onClick: () -> Unit) {
    val icon: ImageVector = when (summary.type) {
        DocumentType.WAREHOUSE_SHIPMENT, DocumentType.RETAIL_SHIPMENT -> Icons.Outlined.LocalShipping
        DocumentType.WAREHOUSE_RECEIPT                                -> Icons.Outlined.MoveToInbox
        DocumentType.RETAIL_RECEIPT                                   -> Icons.Outlined.Store
        DocumentType.TRANSPORT_SHEET                                  -> Icons.Outlined.Description
        DocumentType.COMPLAINT_SHIPMENT                               -> Icons.AutoMirrored.Outlined.AssignmentReturn
        DocumentType.COMPLAINT_RECEIPT                                -> Icons.Outlined.AssignmentReturned
        DocumentType.INVENTORY                                        -> Icons.Outlined.Inventory
    }

    // A type with no location or responsibility centre behind it looks and behaves like any
    // other row here: it stays tappable, and the caller answers the tap with an explanation.
    // Greying it out only told the operator "no" without telling them why or what to do.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, AppLine.CardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppPalette.CreamAlt),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = AppPalette.Slate) }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(summary.type.localizedDisplay().uppercased, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium))
            if (summary.statusMini.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                StatusProgressBar(segments = summary.statusMini, height = 4.dp, gap = 2.dp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            summary.count.toString(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = com.erpbarcode.scanner.ui.theme.GeistMono,
                color = if (summary.count > 0) AppPalette.Ink else AppPalette.Ink4,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}
