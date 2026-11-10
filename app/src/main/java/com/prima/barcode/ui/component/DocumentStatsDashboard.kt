package com.prima.barcode.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prima.barcode.ui.theme.GeistMono
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.monoLabel
import androidx.compose.ui.res.stringResource
import com.prima.barcode.R
import com.prima.barcode.ui.theme.PrimaSignal
import com.prima.barcode.ui.theme.PrimaOnDark
import com.prima.barcode.ui.theme.PrimaStatus

@Composable
fun DocumentStatsDashboard(
    totalScans: Int,
    errors: Int,
    readyForUpload: Int = 0,
    partial: Int = 0,
    over: Int = 0,
    onDocumentOverview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PrimaPalette.Slate)
            .border(1.dp, PrimaOnDark.Border, RoundedCornerShape(14.dp))
            .clickable(onClick = onDocumentOverview),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.dashboard_today), style = monoLabel.copy(color = PrimaOnDark.TextMuted))
                Spacer(Modifier.height(4.dp))
                Text(
                    totalScans.toString(),
                    fontSize = 32.sp,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    lineHeight = 32.sp,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                DocStatLine(count = readyForUpload, label = stringResource(R.string.dashboard_pill_ready),   color = PrimaStatus.Exact)
                DocStatLine(count = partial,        label = stringResource(R.string.dashboard_pill_partial), color = PrimaStatus.Partial)
                DocStatLine(count = over,           label = stringResource(R.string.dashboard_pill_over),    color = PrimaStatus.Over)
                DocStatLine(count = errors,         label = stringResource(R.string.dashboard_pill_error),   color = PrimaSignal.Danger)
            }
        }
    }
}

@Composable
private fun DocStatLine(count: Int, label: String, color: Color) {
    val active = count > 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            count.toString(),
            style = monoLabel.copy(
                color = if (active) color else PrimaOnDark.Track,
                fontWeight = FontWeight.Medium,
            ),
        )
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (active) color else PrimaOnDark.Subtle),
        )
        Text(
            label,
            style = monoLabel.copy(color = if (active) PrimaOnDark.TextSecondary else PrimaOnDark.Subtle),
        )
    }
}
