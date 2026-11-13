package com.erpbarcode.scanner.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.erpbarcode.scanner.R

val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
)

val GeistMono = FontFamily(
    Font(R.font.geistmono_regular, FontWeight.Normal),
    Font(R.font.geistmono_medium, FontWeight.Medium),
)

/** Drives the +2 / +4 sp text-size preference. Provided by [AppTheme]. */
val LocalTextSizeOffset = compositionLocalOf { 0 }

/** Drives the uppercase-text preference. Provided by [AppTheme]. */
val LocalUppercaseEnabled = compositionLocalOf { false }

/** Transforms [this] to uppercase when the uppercase-text preference is active. */
val String.uppercased: String
    @Composable
    @ReadOnlyComposable
    get() = if (LocalUppercaseEnabled.current) this.uppercase() else this

// No label here — it's user-facing text, so it's resolved from string resources at the call
// site (SettingsScreen.localizedLabel).
enum class TextSize(val spOffset: Int) {
    NORMAL(2),
    LARGER(4),
}

/**
 * Base typography — consumed by [scaledTypography] in the theme.
 * Never use directly in composables; go through [MaterialTheme.typography] instead.
 */
internal val BaseAppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.1).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = GeistMono, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.1.sp,
    ),
)

/*
 * A style left out here is not absent: the toolkit fills it in with its own, in the system
 * typeface and at a fixed size, and its components go on using it. Until 3.6.77 that was the case
 * for the two below that the app meets most. headlineSmall is a dialog's title. labelLarge is the
 * label of every button, menu item and date-picker control: 14 sp whatever the text-size setting
 * said, under body text of 16 or 18 sp.
 */
internal fun scaledTypography(offset: Int) = Typography(
    displayLarge = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = (40 + offset).sp, lineHeight = (44 + offset).sp, letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.SemiBold,
        fontSize = (22 + offset).sp, lineHeight = (28 + offset).sp, letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = (22 + offset).sp, lineHeight = (28 + offset).sp, letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = (18 + offset).sp, lineHeight = (24 + offset).sp, letterSpacing = (-0.1).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = (15 + offset).sp, lineHeight = (20 + offset).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Normal,
        fontSize = (16 + offset).sp, lineHeight = (24 + offset).sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Normal,
        fontSize = (14 + offset).sp, lineHeight = (20 + offset).sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Normal,
        fontSize = (13 + offset).sp, lineHeight = (18 + offset).sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = (14 + offset).sp, lineHeight = (20 + offset).sp, letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = GeistMono, fontWeight = FontWeight.Medium,
        fontSize = (11 + offset).sp, lineHeight = (14 + offset).sp, letterSpacing = 1.1.sp,
    ),
)

/**
 * Counter style — large mono digits for x/Y on Recording.
 * Reads [LocalTextSizeOffset] automatically; use inside any @Composable.
 */
val monoCounter: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = TextStyle(
        fontFamily = GeistMono, fontWeight = FontWeight.Medium,
        fontSize = (17 + LocalTextSizeOffset.current).sp, letterSpacing = 0.4.sp,
    )

/**
 * Inline mono label — codes, timestamps.
 * Reads [LocalTextSizeOffset] automatically; use inside any @Composable.
 */
val monoLabel: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = TextStyle(
        fontFamily = GeistMono, fontWeight = FontWeight.Normal,
        fontSize = (12 + LocalTextSizeOffset.current).sp, letterSpacing = 0.4.sp,
    )
