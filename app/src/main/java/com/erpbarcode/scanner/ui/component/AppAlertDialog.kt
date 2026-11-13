package com.erpbarcode.scanner.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.erpbarcode.scanner.ui.theme.AppSignal

/**
 * The least height of a button in a dialog. It is as wide as the dialog and at least this tall,
 * and grows when its label needs a second line. Until 3.6.75 most dialogs had the toolkit's
 * default button: 40 dp tall, as wide as its label, in the bottom right corner.
 */
val DIALOG_BUTTON_HEIGHT = 52.dp

private val DIALOG_ICON_SIZE = 36.dp

/**
 * The label of a dialog's button, in the size of the dialog's own text and a little heavier, so
 * that it follows the text-size setting. Named here rather than left to the theme's button label:
 * until 3.6.77 the theme had none, and the toolkit's own is 14 sp whatever that setting says,
 * which made the one thing to be tapped the smallest text in the dialog.
 */
@Composable
fun DialogButtonLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        textAlign = TextAlign.Center,
    )
}

/**
 * Every dialog that asks or tells the operator something: a title, what there is to say, and one
 * or two buttons across the whole width, the confirming one on top.
 *
 * [accent] colours the title and the [icon] above it; it says what kind of news this is, in the
 * same colours as the rest of the app. [danger] makes the confirming button red: it deletes, or
 * it leads to what went wrong. [onDismissRequest] is a tap outside or Back; [onDismiss] is the
 * second button, and is the same thing unless that button does something of its own ("No", which
 * discards, where Back merely stays). [content] goes under [text], for what a sentence cannot say.
 */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    text: String? = null,
    accent: Color = Color.Unspecified,
    icon: ImageVector? = null,
    danger: Boolean = false,
    confirmEnabled: Boolean = true,
    dismissLabel: String? = null,
    onDismiss: () -> Unit = onDismissRequest,
    content: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = if (icon != null) {
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (accent != Color.Unspecified) accent else LocalContentColor.current,
                    modifier = Modifier.size(DIALOG_ICON_SIZE),
                )
            }
        } else null,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = accent,
                // Under an icon the title is centred, and a second line has to follow it there.
                textAlign = if (icon != null) TextAlign.Center else TextAlign.Start,
            )
        },
        text = if (text != null || content != null) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (text != null) Text(text)
                    if (content != null) content()
                }
            }
        } else null,
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = DIALOG_BUTTON_HEIGHT),
                    colors = if (danger) {
                        ButtonDefaults.buttonColors(containerColor = AppSignal.Danger)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) { DialogButtonLabel(confirmLabel) }
                if (dismissLabel != null) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().heightIn(min = DIALOG_BUTTON_HEIGHT),
                    ) { DialogButtonLabel(dismissLabel) }
                }
            }
        },
    )
}
