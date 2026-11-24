package com.erpbarcode.scanner.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.erpbarcode.scanner.ui.theme.AppLine
import com.erpbarcode.scanner.ui.theme.AppPalette
import com.erpbarcode.scanner.ui.theme.monoLabel
import com.erpbarcode.scanner.ui.theme.uppercased

/**
 * The least height of a button in a screen's bottom action bar. A button there is at least this
 * tall and grows when its label needs more lines than fit.
 */
val ACTION_BAR_BUTTON_HEIGHT = 64.dp

/**
 * A button of a screen's bottom action bar: Download and Upload under the document list, Reset and
 * OK under a filter.
 *
 * [primary] is the one the screen is there for, filled in coral with white text, and paler while
 * not [enabled]; the other is white with an outline. [color] is the primary one's fill where coral
 * would say the wrong thing: red for what discards or signs out, orange for a warning, slate for
 * a plain confirmation. The label follows the uppercase and the text-size settings. The caller
 * says how wide it is, usually `Modifier.weight(1f)` in a row of two.
 *
 * Written once here in 3.6.86. Until then each screen built it from a Box of its own, and the
 * download screen had two Material buttons instead, 52 dp tall and pill-shaped beside 64 dp
 * rounded rectangles everywhere else.
 */
@Composable
fun ActionBarButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    color: Color = AppPalette.Coral,
) {
    val shape = RoundedCornerShape(14.dp)
    val ink = if (primary) Color.White else AppPalette.Ink
    val fill = when {
        !primary -> Color.White
        enabled -> color
        else -> color.copy(alpha = 0.35f)
    }
    Row(
        modifier = modifier
            .heightIn(min = ACTION_BAR_BUTTON_HEIGHT)
            .clip(shape)
            .then(if (primary) Modifier else Modifier.border(1.dp, AppLine.Outline, shape))
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        }
        Text(
            label.uppercased,
            style = monoLabel.copy(color = ink, fontWeight = FontWeight.Medium),
            textAlign = TextAlign.Center,
            // Takes what the label needs and no more, so a short one stays in the middle beside
            // its icon and a long one wraps instead of pushing the icon out.
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
