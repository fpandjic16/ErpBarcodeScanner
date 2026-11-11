package com.prima.barcode.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prima.barcode.R
import com.prima.barcode.data.model.Document
import com.prima.barcode.data.model.Line
import com.prima.barcode.data.model.LineStatus
import com.prima.barcode.data.model.color
import com.prima.barcode.data.model.formatQty
import com.prima.barcode.data.model.inkColor
import com.prima.barcode.ui.theme.LocalTextSizeOffset
import com.prima.barcode.ui.theme.PrimaLine
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.monoCounter
import com.prima.barcode.ui.theme.monoLabel

/*
 * The recording screen's overview: the document's lines, one big-number row each, and the status
 * chip its top bar shows. Split out of RecordingScreen.kt; the screen owns the state and the scroll,
 * these only draw it.
 */

@Composable
internal fun OverviewContent(
    doc: Document,
    listState: LazyListState,
    highlightedLineNo: Int?,
    // A lambda, not the Float itself: read inside the item, the animation invalidates that one
    // row per frame instead of the whole list.
    highlightAlpha: () -> Float,
    onLineTap: (Line) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
        items(doc.lines, key = { it.lineNo }) { line ->
            BigNumberLineRow(
                line = line,
                highlight = if (line.lineNo == highlightedLineNo) highlightAlpha() else 0f,
                onClick = { onLineTap(line) },
            )
            HorizontalDivider(color = PrimaLine.Divider, thickness = 1.dp)
        }
    }
}

/**
 * @param highlight 0 for a normal row, 1 for one that was just scanned into.
 *
 * Deepens the status tint rather than introducing a colour of its own: the four-state status
 * language is the whole vocabulary of this screen, and a fifth colour on top of it would say
 * something the operator has no meaning for. It also means the fade lands on whatever the status
 * is *now* — a scan that completes a line fades into green, not back into orange.
 */
@Composable
internal fun BigNumberLineRow(line: Line, highlight: Float = 0f, onClick: () -> Unit) {
    val sizeOffset = LocalTextSizeOffset.current
    val statusColor = line.status.color
    val inkColor = line.status.inkColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(statusColor.copy(alpha = 0.10f + 0.32f * highlight))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(statusColor))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 16.dp),
        ) {
            val itemName = if (line.isAddedOnDevice) stringResource(R.string.recording_not_on_document_label) else line.item.name
            val itemText = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Normal)) {
                    append(line.item.no)
                }
                append(" - ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(itemName)
                }
            }
            Text(
                itemText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = (15 + sizeOffset).sp,
                    color = if (line.status == LineStatus.EMPTY) PrimaPalette.Ink2 else PrimaPalette.Ink,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.End),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    line.scanned.formatQty(),
                    style = monoCounter.copy(color = inkColor, fontSize = (25 + sizeOffset).sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    "/",
                    style = monoCounter.copy(color = inkColor.copy(alpha = 0.4f), fontSize = (25 + sizeOffset).sp),
                )
                Text(
                    line.expected.formatQty(),
                    style = monoCounter.copy(color = inkColor.copy(alpha = 0.7f), fontSize = (25 + sizeOffset).sp),
                )
            }
        }
    }
}

@Composable
internal fun StatusChip(status: LineStatus, sizeOffset: Int, label: String? = null) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(status.inkColor)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
        Text(
            label ?: when (status) {
                LineStatus.EMPTY   -> stringResource(R.string.status_chip_empty)
                LineStatus.PARTIAL -> stringResource(R.string.status_chip_partial)
                LineStatus.EXACT   -> stringResource(R.string.status_chip_exact)
                LineStatus.OVER    -> stringResource(R.string.status_chip_over)
            },
            style = monoLabel.copy(color = Color.White, fontSize = (11 + sizeOffset).sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium),
        )
    }
}
