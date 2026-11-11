package com.erpbarcode.scanner.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erpbarcode.scanner.data.model.LineStatus
import com.erpbarcode.scanner.data.model.color

/**
 * Segmented bar — one cell per line, colored by status.
 * Used on document headers and document-type cards.
 */
@Composable
fun StatusProgressBar(
    segments: List<LineStatus>,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    gap: Dp = 3.dp,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        segments.forEach { s ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .clip(RoundedCornerShape(height / 2))
                    .background(s.color),
            )
        }
    }
}
