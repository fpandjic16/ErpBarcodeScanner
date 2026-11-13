package com.erpbarcode.scanner.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.ui.component.AppAlertDialog
import com.erpbarcode.scanner.ui.theme.AppSignal

/*
 * The app shell's own dialogs: what an upload, a download, the debugger and a change of operator
 * can bring up over whichever screen is showing. BarcodeApp owns their state; these only draw.
 */

/** Blocks the screen while a blocking transfer runs; cannot be dismissed. */
@Composable
internal fun ProcessingDialog(@StringRes message: Int) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                CircularProgressIndicator()
                Text(stringResource(message), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
internal fun DownloadErrorDialog(message: String, onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.download_error_title),
        text = message,
        confirmLabel = stringResource(R.string.btn_ok),
        onConfirm = onDismiss,
    )
}

/** The debugger: the addresses a request is about to call, before it goes. */
@Composable
internal fun DebugUrlsDialog(urls: List<String>, onProceed: () -> Unit, onCancel: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onCancel,
        title = stringResource(R.string.debug_request_urls_title),
        text = urls.joinToString("\n\n"),
        confirmLabel = stringResource(R.string.btn_proceed),
        onConfirm = onProceed,
        dismissLabel = stringResource(R.string.btn_cancel),
    )
}

/** After a blocking upload with failures: go to the errors, or carry on. */
@Composable
internal fun SyncErrorDialog(onSeeErrors: () -> Unit, onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.sync_errors_title),
        text = stringResource(R.string.sync_errors_text),
        confirmLabel = stringResource(R.string.btn_see_errors),
        onConfirm = onSeeErrors,
        danger = true,
        dismissLabel = stringResource(R.string.btn_dismiss),
    )
}

/**
 * Before a saved configuration moves the device to another ERP installation: that signs the
 * operator out, and what they scanned stays with the installation it was scanned for.
 */
@Composable
internal fun ServerChangeDialog(hasUnsentScans: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.server_change_title),
        text = stringResource(R.string.server_change_text),
        confirmLabel = stringResource(R.string.server_change_confirm),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.btn_cancel),
        content = if (hasUnsentScans) {
            {
                Text(
                    stringResource(R.string.server_change_unsent),
                    color = AppSignal.Danger,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else null,
    )
}

/** Signing out, or switching operator, while a transfer is still running. */
@Composable
internal fun SwitchBlockedDialog(onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.signin_switch_blocked_title),
        text = stringResource(R.string.signin_switch_blocked_body),
        confirmLabel = stringResource(R.string.btn_ok),
        onConfirm = onDismiss,
    )
}
