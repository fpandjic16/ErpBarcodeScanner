package com.erpbarcode.scanner.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erpbarcode.scanner.data.model.Location
import com.erpbarcode.scanner.data.model.ResponsibilityCenter
import com.erpbarcode.scanner.data.model.User
import com.erpbarcode.scanner.ui.component.ActionBarButton
import com.erpbarcode.scanner.ui.component.AppAlertDialog
import com.erpbarcode.scanner.ui.component.AppTopBar
import com.erpbarcode.scanner.ui.theme.AppPalette
import com.erpbarcode.scanner.ui.theme.monoLabel
import androidx.compose.ui.res.stringResource
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.ui.theme.AppSignal
import com.erpbarcode.scanner.ui.theme.AppLine

private val SignOutRed = AppSignal.Danger

/**
 * Who is signed in and where they are working, and the two ways out.
 *
 * **Sign out** closes the session and keeps everything, the saved sign-in included, so the same
 * operator is let in again by their sign-in code.
 *
 * **Sign out and delete my profile** removes the operator from the device: their documents, their
 * scans, the saved sign-in and their settings. It is for someone who will not be using this
 * device again. Scans the ERP has never had go with it, so it asks first, with the counts in front
 * of the operator and a plain Yes or No; No leaves everything as it was, and they can upload
 * first. The counting and the deletion are `AppViewModel.profileFootprint` and
 * `signOutAndDeleteProfile`.
 */
@Composable
fun UserInfoScreen(
    user: User?,
    location: Location?,
    rc: ResponsibilityCenter?,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    // Counts what deleting the signed-in operator's own profile would destroy. Asked for only
    // when the deletion is proposed, never merely to draw the screen.
    loadOwnFootprint: (onResult: (documents: Int, scans: Int, unsentScans: Int) -> Unit) -> Unit = { it(0, 0, 0) },
    onDeleteOwnProfile: () -> Unit = {},
) {
    var askingToDelete by remember { mutableStateOf(false) }
    // Null while it is still being counted.
    var footprint by remember { mutableStateOf<ProfileFootprint?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(AppPalette.Cream)) {
        AppTopBar(
            title = stringResource(R.string.user_info_title),
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .border(1.dp, AppLine.CardBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                UserInfoRow(stringResource(R.string.user_info_id), user?.id ?: "—")
                HorizontalDivider(color = AppLine.Divider, thickness = 1.dp)
                UserInfoRow(stringResource(R.string.user_info_name), user?.displayName ?: "—")
                HorizontalDivider(color = AppLine.Divider, thickness = 1.dp)
                UserInfoRow(stringResource(R.string.lrc_section_rc), rc?.let { "${it.code} · ${it.name}" } ?: "—")
                HorizontalDivider(color = AppLine.Divider, thickness = 1.dp)
                UserInfoRow(stringResource(R.string.lrc_section_location), location?.let { "${it.code} · ${it.name}" } ?: "—")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionBarButton(
                label = stringResource(R.string.settings_sign_out),
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
                primary = true,
                color = SignOutRed,
                icon = Icons.AutoMirrored.Outlined.Logout,
            )
            // Outlined and second: the everyday way out is the one above, and this one is not
            // to be pressed in passing.
            ActionBarButton(
                label = stringResource(R.string.user_info_delete_profile),
                onClick = {
                    footprint = null
                    askingToDelete = true
                    loadOwnFootprint { documents, scans, unsent ->
                        footprint = ProfileFootprint(documents, scans, unsent)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Outlined.DeleteForever,
            )
        }
    }

    if (askingToDelete) {
        val counts = footprint
        AppAlertDialog(
            onDismissRequest = { askingToDelete = false },
            title = stringResource(R.string.own_profile_delete_title),
            confirmLabel = stringResource(R.string.btn_yes),
            onConfirm = {
                askingToDelete = false
                onDeleteOwnProfile()
            },
            // Not until the counts are in: Yes must be an answer to a number, not to an unknown.
            confirmEnabled = counts != null,
            danger = true,
            dismissLabel = stringResource(R.string.btn_no),
            content = {
                if (counts == null) {
                    ProfileFootprintCounting()
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.own_profile_delete_text))
                        ProfileDeletionWarning(
                            counts,
                            unsentHint = stringResource(R.string.own_profile_delete_unsent_hint),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun UserInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = monoLabel.copy(color = AppPalette.Ink3),
            modifier = Modifier.weight(0.4f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(color = AppPalette.Ink, fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(0.6f),
        )
    }
}
