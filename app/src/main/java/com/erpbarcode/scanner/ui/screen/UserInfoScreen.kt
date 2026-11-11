package com.erpbarcode.scanner.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erpbarcode.scanner.data.model.Location
import com.erpbarcode.scanner.data.model.ResponsibilityCenter
import com.erpbarcode.scanner.data.model.User
import com.erpbarcode.scanner.ui.component.AppTopBar
import com.erpbarcode.scanner.ui.theme.AppPalette
import com.erpbarcode.scanner.ui.theme.monoLabel
import androidx.compose.ui.res.stringResource
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.ui.theme.AppSignal
import com.erpbarcode.scanner.ui.theme.AppLine

private val SignOutRed = AppSignal.Danger

@Composable
fun UserInfoScreen(
    user: User?,
    location: Location?,
    rc: ResponsibilityCenter?,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
) {
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SignOutRed),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_sign_out), fontWeight = FontWeight.SemiBold)
            }
        }
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
