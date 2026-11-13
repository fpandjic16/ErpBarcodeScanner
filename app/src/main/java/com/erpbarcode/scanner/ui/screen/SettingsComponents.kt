package com.erpbarcode.scanner.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.ui.component.AppAlertDialog
import com.erpbarcode.scanner.ui.theme.AppLine
import com.erpbarcode.scanner.ui.theme.AppPalette
import com.erpbarcode.scanner.ui.theme.AppSignal
import com.erpbarcode.scanner.ui.theme.monoLabel
import com.erpbarcode.scanner.ui.theme.uppercased

/*
 * The rows Settings is built from. Split out of SettingsScreen.kt; SettingsIcon and SettingsDivider
 * are also used by AppInfoCard. Every row is the same 20 dp gutter, 40 dp icon tile and 14 dp gap,
 * which is what lines the labels up down the whole screen.
 */

@Composable
internal fun SettingsSectionHeader(title: String) {
    Text(
        title.uppercased,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
        style = monoLabel.copy(color = AppPalette.Ink3),
    )
}

@Composable
internal fun SettingsIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppPalette.CreamAlt),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = AppPalette.Slate, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 74.dp),
        color = AppLine.Divider,
    )
}

/** A row's label and the line of explanation under it. */
@Composable
internal fun SettingsRowText(label: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = AppPalette.Ink,
                fontWeight = FontWeight.Normal,
            ),
        )
        Text(
            description,
            style = monoLabel.copy(color = AppPalette.Ink3),
        )
    }
}

/** Icon, label and explanation, with nothing to tap: the heading of a row whose control sits below it. */
@Composable
internal fun SettingsRowHeader(icon: ImageVector, label: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(14.dp))
        SettingsRowText(label, description, Modifier.weight(1f))
    }
}

/** A row that opens something else: a screen, a dialog, a file picker. */
@Composable
internal fun SettingsNavRow(icon: ImageVector, label: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(14.dp))
        SettingsRowText(label, description, Modifier.weight(1f))
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = AppPalette.Ink4,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** A destructive row, in red; what it destroys is asked about in a dialog first. */
@Composable
internal fun SettingsDangerRow(label: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppSignal.DangerBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = AppSignal.Danger, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AppSignal.Danger,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                description,
                style = monoLabel.copy(color = AppPalette.Ink3),
            )
        }
    }
}

/** One option of a small set (text size, debounce time); the picked one is dark. */
@Composable
internal fun SettingsChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AppPalette.Slate else AppPalette.CreamAlt)
            .clickable(onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = monoLabel.copy(
                color = if (selected) Color.White else AppPalette.Ink2,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            ),
        )
    }
}

@Composable
internal fun SettingsToggleRow(
    icon: ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    comingSoon: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (comingSoon) 0.45f else 1f)
            .then(if (!comingSoon) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AppPalette.Ink,
                        fontWeight = FontWeight.Normal,
                    ),
                )
                if (comingSoon) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AppPalette.CreamAlt)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(
                            stringResource(R.string.settings_coming_soon),
                            style = monoLabel.copy(
                                color = AppPalette.Ink3,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
            Text(description, style = monoLabel.copy(color = AppPalette.Ink3))
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = !comingSoon,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AppPalette.Slate,
                checkedThumbColor = Color.White,
            ),
        )
    }
}

/** "Are you sure?" before a destructive row does its work; the confirm button is red. */
@Composable
internal fun SettingsConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        text = text,
        confirmLabel = stringResource(R.string.btn_clear),
        onConfirm = onConfirm,
        danger = true,
        dismissLabel = stringResource(R.string.btn_cancel),
    )
}
