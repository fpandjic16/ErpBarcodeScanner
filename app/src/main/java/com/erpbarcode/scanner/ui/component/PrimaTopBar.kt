package com.erpbarcode.scanner.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.ui.theme.PrimaPalette
import com.erpbarcode.scanner.ui.theme.monoLabel
import com.erpbarcode.scanner.ui.theme.uppercased
import com.erpbarcode.scanner.ui.theme.PrimaOnDark

/**
 * Height of a button in the top bar (back, and the recording screen's upload): 48 dp, the
 * smallest target that is hit reliably in gloves. 44 dp until 3.6.29.
 */
val TOP_BAR_BUTTON_HEIGHT = 48.dp

@Composable
fun PrimaTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
            .background(PrimaPalette.Slate)
            .statusBarsPadding()
            // 52, so the 48 dp buttons below keep their 2 dp of air. Was 48 with 44 dp buttons until
            // 3.6.29; 48 dp is the smallest target a gloved thumb reliably hits.
            .height(52.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> leading()
            onBack != null -> {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp, top = 2.dp, bottom = 2.dp, end = 4.dp)
                        .size(width = 60.dp, height = TOP_BAR_BUTTON_HEIGHT)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PrimaOnDark.Button)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = Color.White)
                }
            }
            else -> Spacer(Modifier.width(72.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = title.uppercased,
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it.uppercased,
                    style = monoLabel.copy(color = PrimaOnDark.TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}
