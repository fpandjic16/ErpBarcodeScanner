package com.erpbarcode.scanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun PrimaBarcodeTheme(
    textSizeOffset: Int = 0,
    uppercaseEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalTextSizeOffset provides textSizeOffset,
        LocalUppercaseEnabled provides uppercaseEnabled,
    ) {
        MaterialTheme(
            colorScheme = PrimaLightColors,
            typography = scaledTypography(textSizeOffset),
            shapes = PrimaShapes,
            content = content,
        )
    }
}
