package com.erpbarcode.scanner.ui.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.ui.theme.PrimaPalette
import com.erpbarcode.scanner.ui.theme.PrimaSignal

/*
 * External System Configuration's own dialogs. Split out of ExtSystemConfigScreen.kt; the screen
 * owns when they show and what their buttons do.
 */

/** While "Test connection" waits for the server; cannot be dismissed. */
@Composable
internal fun ConnectionTestingDialog() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.ext_config_testing_connection), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
internal fun ConnectionResultDialog(ok: Boolean, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (ok) stringResource(R.string.ext_config_connection_ok) else stringResource(R.string.ext_config_connection_failed),
                fontWeight = FontWeight.Bold,
                color = if (ok) PrimaPalette.Teal else PrimaSignal.Danger,
            )
        },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_ok)) }
        },
    )
}

/** Saving with a server address but no environment: [onPick] one, or [onSaveAnyway]. */
@Composable
internal fun EnvironmentMissingDialog(onPick: () -> Unit, onSaveAnyway: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ext_config_env_missing_title), fontWeight = FontWeight.Bold) },
        text = { Text(stringResource(R.string.ext_config_env_missing_text)) },
        confirmButton = {
            Button(onClick = onPick) {
                Text(stringResource(R.string.ext_config_env_missing_pick), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onSaveAnyway) {
                Text(stringResource(R.string.ext_config_env_missing_save_anyway))
            }
        },
    )
}
