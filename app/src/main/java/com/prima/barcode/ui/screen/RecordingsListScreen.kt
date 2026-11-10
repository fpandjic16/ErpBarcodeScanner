package com.prima.barcode.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prima.barcode.R
import com.prima.barcode.data.model.Document
import com.prima.barcode.data.model.color
import com.prima.barcode.data.model.scanStatus
import com.prima.barcode.ui.component.PrimaTopBar
import com.prima.barcode.ui.component.verticalScrollbar
import com.prima.barcode.ui.theme.GeistMono
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.PrimaStatus
import com.prima.barcode.ui.theme.monoLabel
import com.prima.barcode.ui.theme.PrimaLine

/**
 * Every document on this device that carries a scan, newest first.
 *
 * This list used to sit inline on the main menu. One good shift is dozens of rows, and they push
 * the document types — the thing the menu exists for — off the top of the screen. The menu now
 * carries a single row with the count, and that row opens this.
 *
 * The ordering and the membership rule are decided by the caller, not here: see `recordedDocs` in
 * `MainActivity`, which deliberately counts recordings rather than scanned lines so a document
 * whose scans the ERP has orphaned still appears.
 */
@Composable
fun RecordingsListScreen(
    docs: List<Document>,
    onBack: () -> Unit,
    onDocumentTap: (Document) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(PrimaPalette.Cream)) {
        PrimaTopBar(title = stringResource(R.string.main_recordings_header), onBack = onBack)

        if (docs.isEmpty()) {
            // Reachable two ways: the row that opens this screen stays on the main menu at zero,
            // and an upload started elsewhere can empty the list while it is already open.
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.recordings_list_empty),
                    style = monoLabel.copy(color = PrimaPalette.Ink3),
                )
            }
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScrollbar(listState),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(docs, key = { "rec-${it.type.key}-${it.documentNo}" }) { doc ->
                    RecordedDocumentRow(doc = doc, onClick = { onDocumentTap(doc) })
                }
            }
        }
    }
}

/** One document: what it is, and how much of it has already gone to the ERP. */
@Composable
private fun RecordedDocumentRow(doc: Document, onClick: () -> Unit) {
    val status = doc.scanStatus()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, PrimaLine.CardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(status.color))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                doc.documentNo,
                style = monoLabel.copy(color = PrimaPalette.Ink, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                doc.type.localizedDisplay(),
                style = monoLabel.copy(color = PrimaPalette.Ink3),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                doc.totalScans.toString(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = GeistMono,
                    color = PrimaPalette.Ink,
                    fontWeight = FontWeight.Medium,
                ),
            )
            if (doc.sentScans > 0) {
                Text(
                    stringResource(R.string.recordings_sent_chip, doc.sentScans),
                    style = monoLabel.copy(color = PrimaStatus.Exact),
                )
            }
        }
    }
}
