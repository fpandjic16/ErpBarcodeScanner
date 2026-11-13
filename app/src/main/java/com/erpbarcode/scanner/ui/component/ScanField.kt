package com.erpbarcode.scanner.ui.component

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.KeyEvent
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erpbarcode.scanner.R
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.addTextChangedListener
import com.erpbarcode.scanner.ui.theme.AppPalette
import com.erpbarcode.scanner.ui.theme.AppLine
import com.erpbarcode.scanner.ui.theme.AppOnDark

/**
 * Manual barcode entry, plus the camera fallback, without ever auto-opening the on-screen
 * keyboard.
 *
 * On Zebra devices the scanner does **not** come through here. DataWedge delivers scans as an
 * intent and has keystroke output switched off (see `DataWedgeManager.configure`); this field
 * once received them as typed keystrokes too, which meant every scan arrived twice. The screens
 * hosting it register the intent receiver themselves.
 *
 * Backed by a native [EditText] with `showSoftInputOnFocus = false`. A burst that arrives without
 * the keyboard open — a keyboard-wedge scanner on a non-Zebra device — is still auto-submitted
 * once it settles, or immediately on an Enter/Tab terminator. Tapping the keyboard icon shows the
 * soft keyboard for manual entry; the camera icon, shown only on devices without a scanner, opens
 * the CameraX fallback.
 */
@Composable
fun ScanField(
    placeholder: String,
    onScan: (String) -> Unit,
    onCameraTap: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    // Some MC3300 configurations have no camera. Offering the button there gives the operator a
    // control that cannot do anything, which is indistinguishable from a broken one.
    showCamera: Boolean = true,
) {
    val context = LocalContext.current
    val imm = remember { context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }
    val currentOnScan by rememberUpdatedState(onScan)
    val handler = remember { Handler(Looper.getMainLooper()) }
    // Distinguishes deliberate manual typing (keyboard shown) from a scanner burst.
    val manualTyping = remember { mutableStateOf(false) }

    val textColor = (if (dark) Color.White else AppPalette.Ink).toArgb()
    val hintColor = (if (dark) AppOnDark.TextFaint else AppPalette.Ink4).toArgb()

    val editText = remember {
        EditText(context).apply {
            isSingleLine = true
            background = null
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER_VERTICAL
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            showSoftInputOnFocus = false
            isFocusableInTouchMode = true
        }
    }

    DisposableEffect(editText) {
        fun submit() {
            val code = editText.text.toString().trim()
            editText.text?.clear()
            handler.removeCallbacksAndMessages(null)
            if (manualTyping.value) {
                manualTyping.value = false
                editText.showSoftInputOnFocus = false
                imm.hideSoftInputFromWindow(editText.windowToken, 0)
            }
            if (code.isNotEmpty()) currentOnScan(code)
        }
        val idle = Runnable { if (!manualTyping.value && !editText.text.isNullOrEmpty()) submit() }

        editText.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_ENTER ||
                 keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                 keyCode == KeyEvent.KEYCODE_TAB)
            ) { submit(); true } else false
        }
        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }
        val watcher = editText.addTextChangedListener(afterTextChanged = {
            handler.removeCallbacks(idle)
            // Auto-accept a scanner burst once it goes idle; manual typing waits for Done.
            if (!manualTyping.value && !editText.text.isNullOrEmpty()) {
                handler.postDelayed(idle, 200)
            }
        })
        editText.requestFocus()

        onDispose {
            handler.removeCallbacksAndMessages(null)
            editText.removeTextChangedListener(watcher)
            editText.setOnKeyListener(null)
            editText.setOnEditorActionListener(null)
        }
    }

    // Re-assert focus whenever the field (re)enters composition so the scanner works
    // without the user tapping first.
    LaunchedEffect(Unit) { editText.requestFocus() }

    fun showKeyboard() {
        manualTyping.value = true
        editText.showSoftInputOnFocus = true
        editText.requestFocus()
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    val bg = if (dark) AppOnDark.Fill else AppPalette.Cream
    val borderColor = if (dark) AppOnDark.Border else AppLine.CardBorder

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            // 2 dp around the buttons' 48 dp slots. The text keeps the 10 dp it always had above
            // and below (8 here, 2 from this), so the field is exactly as tall as it was.
            .padding(start = 12.dp, top = 2.dp, end = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AndroidView(
            factory = { editText },
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            update = { field ->
                field.setTextColor(textColor)
                field.setHintTextColor(hintColor)
                field.hint = placeholder
            },
        )
        // With the 4 dp the slot keeps around its square, the 8 dp between text and button.
        Spacer(Modifier.width(4.dp))
        ScanFieldButton(
            icon = Icons.Outlined.Keyboard,
            description = stringResource(R.string.cd_show_keyboard),
            background = if (dark) AppOnDark.Button else AppPalette.CreamAlt,
            tint = if (dark) Color.White else AppPalette.Slate,
            onClick = { showKeyboard() },
        )
        if (showCamera) {
            ScanFieldButton(
                icon = Icons.Outlined.PhotoCamera,
                description = stringResource(R.string.cd_open_camera),
                background = if (dark) AppPalette.Coral else AppPalette.Slate,
                tint = Color.White,
                onClick = onCameraTap,
            )
        }
    }
}

/**
 * One of the field's buttons: a 40 dp square in a 48 dp slot.
 *
 * The slot is the target, the square is what is drawn. Until 3.6.74 the squares were 32 dp with
 * 8 dp between them and no slot, so the two targets met halfway across that gap: 44 dp wide at
 * best, and a thumb landing between them got whichever happened to be nearer. The slots now
 * stand side by side, which leaves the same 8 dp between the squares.
 */
@Composable
private fun ScanFieldButton(
    icon: ImageVector,
    description: String,
    background: Color,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = tint)
    }
}
