package com.erpbarcode.scanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun AppTheme(
    textSizeOffset: Int = 0,
    uppercaseEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalTextSizeOffset provides textSizeOffset,
        LocalUppercaseEnabled provides uppercaseEnabled,
    ) {
        MaterialTheme(
            colorScheme = AppLightColors,
            typography = scaledTypography(textSizeOffset),
            shapes = AppShapes,
            content = content,
        )
    }
}
