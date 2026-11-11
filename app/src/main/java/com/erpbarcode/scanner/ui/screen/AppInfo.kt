package com.erpbarcode.scanner.ui.screen

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erpbarcode.scanner.BuildConfig
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.data.auth.ExtSystemEnvironment
import com.erpbarcode.scanner.data.db.PRIMA_DB_VERSION
import com.erpbarcode.scanner.ui.component.PrimaTopBar
import com.erpbarcode.scanner.ui.theme.PrimaPalette
import com.erpbarcode.scanner.ui.theme.monoLabel

/**
 * What a support call asks first: which build, on which Android, against which ERP.
 *
 * One block, shown in two places — the System info section of Settings, and [AppInfoScreen] in
 * front of the sign-in. It is in its own file so the two cannot drift apart. Nothing here is
 * personal or secret, which is why the sign-in screen may show it to someone not signed in.
 *
 * The company comes from the configured endpoint address, see `ExtSystemConfig.companyName`.
 */
@Composable
internal fun AppInfoCard(environment: ExtSystemEnvironment?, companyName: String?) {
    val ctx = LocalContext.current
    val notSet = stringResource(R.string.app_info_not_set)
    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        InfoRow(
            Icons.Outlined.Info,
            stringResource(R.string.settings_app_version),
            stringResource(R.string.settings_app_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
        )
        SettingsDivider()
        InfoRow(
            Icons.Outlined.PhoneAndroid,
            stringResource(R.string.settings_android),
            "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")",
        )
        SettingsDivider()
        InfoRow(
            Icons.Outlined.Code,
            stringResource(R.string.settings_sdk),
            stringResource(
                R.string.settings_sdk_value,
                ctx.applicationInfo.minSdkVersion,
                apiToAndroid(ctx.applicationInfo.minSdkVersion),
                ctx.applicationInfo.targetSdkVersion,
                apiToAndroid(ctx.applicationInfo.targetSdkVersion),
            ),
        )
        SettingsDivider()
        InfoRow(Icons.Outlined.Storage, stringResource(R.string.settings_db_schema), "Room v$PRIMA_DB_VERSION")
        SettingsDivider()
        InfoRow(
            Icons.Outlined.Dns,
            stringResource(R.string.app_info_environment),
            environment?.let { stringResource(it.labelRes) } ?: notSet,
        )
        SettingsDivider()
        InfoRow(Icons.Outlined.Business, stringResource(R.string.app_info_company), companyName ?: notSet)
    }
}

/** The same block as a screen of its own, reachable from the sign-in screen. */
@Composable
fun AppInfoScreen(environment: ExtSystemEnvironment?, companyName: String?, onBack: () -> Unit) {
    // Shown in front of the sign-in gate rather than as a route, so the system back button has to
    // be caught here; left alone it would close the app.
    BackHandler(onBack = onBack)
    Column(modifier = Modifier.fillMaxSize().background(PrimaPalette.Cream)) {
        PrimaTopBar(title = stringResource(R.string.app_info_title), onBack = onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
            AppInfoCard(environment, companyName)
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(color = PrimaPalette.Ink, fontWeight = FontWeight.Normal),
            )
            Text(value, style = monoLabel.copy(color = PrimaPalette.Ink3))
        }
    }
}

private fun apiToAndroid(api: Int): String = when (api) {
    24 -> "7.0"
    25 -> "7.1"
    26 -> "8.0"
    27 -> "8.1"
    28 -> "9"
    29 -> "10"
    30 -> "11"
    31 -> "12"
    32 -> "12L"
    33 -> "13"
    34 -> "14"
    35 -> "15"
    36 -> "16"
    else -> "?"
}
