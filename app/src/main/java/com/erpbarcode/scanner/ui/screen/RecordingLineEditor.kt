package com.erpbarcode.scanner.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.data.model.Line
import com.erpbarcode.scanner.data.model.LineStatus
import com.erpbarcode.scanner.data.model.color
import com.erpbarcode.scanner.data.model.formatQty
import com.erpbarcode.scanner.data.model.inkColor
import com.erpbarcode.scanner.ui.theme.LocalTextSizeOffset
import com.erpbarcode.scanner.ui.theme.AppLine
import com.erpbarcode.scanner.ui.theme.AppPalette
import com.erpbarcode.scanner.ui.theme.AppStatus
import com.erpbarcode.scanner.ui.theme.monoCounter
import com.erpbarcode.scanner.ui.theme.monoLabel
import kotlinx.coroutines.delay

/*
 * The recording screen's two editing views of one line: the steppers (ItemQtyDetails) and the keypad
 * (ItemQtyExtraDetails). Split out of RecordingScreen.kt; the screen decides what an edit does and
 * passes it in as callbacks, these only draw and report keys and taps.
 */

@Composable
internal fun ItemQtyDetails(
    line: Line,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onTypeQuantity: () -> Unit,
    onApply: () -> Unit,
) {
    val sizeOffset = LocalTextSizeOffset.current
    val statusColor = line.status.color
    val inkColor = line.status.inkColor
    val bgColor by animateColorAsState(
        targetValue = statusColor.copy(alpha = 0.08f),
        animationSpec = tween(durationMillis = 300),
        label = "activeLineBg",
    )
    val initialScanned = remember(line.lineNo) { line.scanned }
    val hasChanged = line.scanned != initialScanned

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppPalette.Cream)
            .background(bgColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            val metaStyle = monoLabel.copy(color = AppPalette.Ink3, fontSize = (13 + sizeOffset).sp)
            Text(stringResource(R.string.recording_barcode_prefix) + line.barcodeNo, style = metaStyle)
            // Says the floor out loud, so the stepper refusing to go lower isn't a mystery.
            if (line.hasSentQuantity) {
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.recording_sent_floor_hint, line.sentQuantity.formatQty()),
                    style = metaStyle.copy(color = AppStatus.ExactInk),
                )
            }
            Spacer(Modifier.height(4.dp))

            Text(
                if (line.isAddedOnDevice) stringResource(R.string.recording_not_on_document_label) else line.item.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    color = AppPalette.Ink,
                    fontSize = (16 + sizeOffset).sp,
                ),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(onClick = onTypeQuantity),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        line.scanned.formatQty(),
                        style = monoCounter.copy(color = inkColor, fontSize = (40 + sizeOffset).sp, fontWeight = FontWeight.Medium),
                    )
                    Text(
                        "/",
                        style = monoCounter.copy(color = inkColor.copy(alpha = 0.35f), fontSize = (18 + sizeOffset).sp),
                    )
                    Text(
                        line.expected.formatQty(),
                        style = monoCounter.copy(color = inkColor.copy(alpha = 0.55f), fontSize = (40 + sizeOffset).sp),
                    )

                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (hasChanged) 1f else 0f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(inkColor)
                    .clickable(enabled = hasChanged, onClick = onApply)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.btn_apply),
                        style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium, fontSize = (15 + sizeOffset).sp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f).height(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, AppLine.Outline, RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .clickable(onClick = onDecrement),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "-1",
                        style = monoCounter.copy(color = AppPalette.Ink, fontSize = (26 + sizeOffset).sp, fontWeight = FontWeight.Medium),
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f).height(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppPalette.Coral)
                        .clickable(onClick = onIncrement),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "+1",
                        style = monoCounter.copy(color = Color.White, fontSize = (26 + sizeOffset).sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
    }
}


@Composable
internal fun ItemQtyExtraDetails(
    line: Line,
    typed: String,
    onKey: (String) -> Unit,
    onConfirm: () -> Unit,
    onConfirmRequired: () -> Unit,
) {
    val sizeOffset = LocalTextSizeOffset.current
    val parsedQty = typed.toDoubleOrNull()
    val previewStatus = if (parsedQty != null && typed.isNotEmpty()) LineStatus.of(parsedQty, line.expected) else line.status
    val previewColor by animateColorAsState(
        targetValue = previewStatus.inkColor,
        animationSpec = tween(durationMillis = 200),
        label = "keypadPreviewColor",
    )

    var caretVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) { delay(500); caretVisible = !caretVisible }
    }

    val confirmEnabled = typed.isNotEmpty() && parsedQty != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppPalette.Cream)
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        // Hero card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(2.dp, previewColor.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        typed.ifEmpty { line.scanned.formatQty() },
                        style = monoCounter.copy(
                            color = if (typed.isEmpty()) previewColor.copy(alpha = 0.38f) else previewColor,
                            fontSize = (40 + sizeOffset).sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 3.dp, bottom = 5.dp)
                            .width(3.dp).height(30.dp)
                            .alpha(if (caretVisible) 1f else 0f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(AppPalette.Coral),
                    )
                }
                Text(
                    "/",
                    style = monoCounter.copy(color = previewColor.copy(alpha = 0.35f), fontSize = (18 + sizeOffset).sp),
                )
                Text(
                    line.expected.formatQty(),
                    style = monoCounter.copy(color = previewColor.copy(alpha = 0.55f), fontSize = (40 + sizeOffset).sp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // Keypad — phone layout (1-9 top, C/0/backspace bottom)
        val keyRows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(".", "0", "X"),
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            keyRows.forEach { row ->
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { key ->
                        val isMuted = key == "X"
                        Box(
                            modifier = Modifier
                                .weight(1f).fillMaxHeight()
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, AppLine.CardBorder, RoundedCornerShape(14.dp))
                                .background(if (isMuted) AppPalette.CreamAlt else Color.White)
                                .clickable { onKey(key) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (key == "X") {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Backspace,
                                    contentDescription = null,
                                    tint = AppPalette.Ink,
                                    modifier = Modifier.size((26 + sizeOffset).dp),
                                )
                            } else {
                                Text(
                                    if (key == ".") "," else key,
                                    style = monoCounter.copy(color = AppPalette.Ink, fontSize = (26 + sizeOffset).sp, fontWeight = FontWeight.Medium),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        val btnText = if (typed.isEmpty()) "Set to ${line.expected.formatQty()}" else "Set to $typed"
        val btnColor = when {
            typed.isEmpty()  -> LineStatus.EXACT.inkColor
            confirmEnabled   -> previewColor
            else             -> AppPalette.CreamAlt
        }
        val btnEnabled = typed.isEmpty() || confirmEnabled
        val btnAction: () -> Unit = if (typed.isEmpty()) onConfirmRequired else onConfirm
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(btnColor)
                .clickable(enabled = btnEnabled, onClick = btnAction),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    btnText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Medium,
                        color = if (btnEnabled) Color.White else AppPalette.Ink4,
                        fontSize = (18 + sizeOffset).sp,
                    ),
                )
                if (btnEnabled) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
