package com.erpbarcode.scanner.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.erpbarcode.scanner.ui.theme.AppLine

private val BarColor = AppLine.Scrollbar

fun Modifier.verticalScrollbar(state: ScrollState): Modifier = drawWithContent {
    drawContent()
    val max = state.maxValue
    if (max <= 0) return@drawWithContent
    val viewH = size.height
    val thumbH = (viewH * viewH / (viewH + max)).coerceAtLeast(40f)
    val thumbY = (viewH - thumbH) * state.value.toFloat() / max
    drawRoundRect(
        color = BarColor,
        topLeft = Offset(size.width - 6f, thumbY),
        size = Size(4f, thumbH),
        cornerRadius = CornerRadius(2f),
    )
}

fun Modifier.verticalScrollbar(state: LazyListState): Modifier = drawWithContent {
    drawContent()
    val info = state.layoutInfo
    val totalItems = info.totalItemsCount
    val visibleItems = info.visibleItemsInfo
    if (totalItems == 0 || visibleItems.isEmpty()) return@drawWithContent
    val viewH = size.height
    val avgItemH = visibleItems.sumOf { it.size }.toFloat() / visibleItems.size
    val estimatedTotal = totalItems * avgItemH
    if (estimatedTotal <= viewH) return@drawWithContent
    val thumbH = (viewH * viewH / estimatedTotal).coerceAtLeast(40f)
    val scrolledPx = state.firstVisibleItemIndex * avgItemH + state.firstVisibleItemScrollOffset
    val maxScroll = estimatedTotal - viewH
    val thumbY = if (maxScroll > 0f)
        ((scrolledPx / maxScroll) * (viewH - thumbH)).coerceIn(0f, viewH - thumbH)
    else 0f
    drawRoundRect(
        color = BarColor,
        topLeft = Offset(size.width - 6f, thumbY),
        size = Size(4f, thumbH),
        cornerRadius = CornerRadius(2f),
    )
}
