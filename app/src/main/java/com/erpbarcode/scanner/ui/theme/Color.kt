package com.erpbarcode.scanner.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The brand palette: Coral, Slate, Cream and Teal, with an ink scale for text.
 *
 * Each color has a role; do not reuse outside its role unless extending
 * the system deliberately.
 */
object AppPalette {
    // Chrome / data surfaces
    val Slate    = Color(0xFF2E3539)
    val SlateAlt = Color(0xFF3A4146)
    val Cream    = Color(0xFFF2EBDE)
    val CreamAlt = Color(0xFFE7DECF)

    // Brand accent
    val Coral     = Color(0xFFC95B4D)
    val CoralDeep = Color(0xFFB04638)

    // Supporting
    val Teal    = Color(0xFF2F5455)
    val Mustard = Color(0xFFB89A3A)
    val Pink    = Color(0xFFE89AAA)
    val Oak     = Color(0xFFB49880)

    // Ink scale
    val Ink  = Color(0xFF1A1C1F)
    val Ink2 = Color(0xFF3D4046)
    val Ink3 = Color(0xFF6F7378)
    val Ink4 = Color(0xFFA4A6AC)
}

/**
 * The four-state status language. Utility colors, *not* brand colors.
 * Read these via [com.erpbarcode.scanner.data.model.LineStatus.color] — never
 * hard-code outside that mapping.
 */
object AppStatus {
    // Fill: bars, dots, progress segments, tints. Reaches 3:1 against Cream and White (WCAG
    // non-text contrast). Only Partial had to change for that: it was #C7943A, 2.3:1 on Cream.
    val Empty   = Color(0xFFCE3A3A)
    val Partial = Color(0xFFAC8032)
    val Exact   = Color(0xFF2E8C5E)
    val Over    = Color(0xFF2D6CE0)

    // Ink: text and counters in a status colour, and solid badges with white text on them.
    // Reaches 4.5:1 on the state's own 10% tint over Cream, and white on it 4.5:1. Read through
    // LineStatus.inkColor. Before 3.6.31 text used the fill colours (Partial 2.1:1 on its tint).
    val EmptyInk   = Color(0xFFB33232)
    val PartialInk = Color(0xFF816026)
    val ExactInk   = Color(0xFF25704B)
    val OverInk    = Color(0xFF275DC2)

    /** Partial on the dark chrome, where the darker fill would lose contrast (4.6:1 on Slate). */
    val PartialOnDark = Color(0xFFC7943A)

    val EmptyBg   = Color(0x1ACE3A3A) // 10% alpha
    val PartialBg = Color(0x1FC7943A)
    val ExactBg   = Color(0x1A2E8C5E)
    val OverBg    = Color(0x1A2D6CE0)
}

/*
 * Every colour a screen or component uses comes from one of the objects in this file; a literal
 * `Color(0x…)` outside it is a bug (CLAUDE.md). Until 3.6.30 the screens carried 90 such literals.
 * Moving them here kept every value except these near-duplicates, which were made one:
 *   black 0x0A (list dividers)          -> AppLine.Divider    0x0F
 *   black 0x14, 0x1A (card borders)     -> AppLine.CardBorder 0x18
 *   black 0x18 (search field outline)   -> AppLine.InputBorder 0x28
 *   white 0x22 (buttons on Slate)       -> AppOnDark.Button   0x20
 *   white 0x1A (scan field border dark) -> AppOnDark.Border   0x18
 *   white 0xC5 (recording subtitle)     -> AppOnDark.TextSecondary 0xB3
 * The state before is tagged pre-design-tokens in git.
 */

/** Signals that are not a line's status: errors, warnings, the test-environment badge. */
object AppSignal {
    /** The same red as [AppStatus.Empty], in its other role: something went wrong. */
    val Danger        = Color(0xFFCE3A3A)
    /** The recording bar's flash behind a scan that matched nothing. */
    val DangerDeep    = Color(0xFF7A1A1A)
    /** Opaque light red behind a destructive row (Settings). */
    val DangerBg      = Color(0xFFFFEAEA)
    val DangerTint    = Color(0x12CE3A3A)
    val DangerBorder  = Color(0x28CE3A3A)
    /** Secondary text on [DangerTint]. */
    val DangerMuted   = Color(0x99CE3A3A)
    /**
     * Partial's orange in its other role: look before going on. The ink shade, because it is
     * used as text and as a button with white text (#C7943A until 3.6.31, 2.7:1 with white).
     */
    val Warning       = Color(0xFF816026)
    /** The same on the dark chrome (the main menu's TEST badge). */
    val WarningOnDark = Color(0xFFC7943A)
    val WarningBorder = Color(0x28C7943A)
}

/** Hairlines, borders and fills on the light surfaces (Cream, White). */
object AppLine {
    val Divider      = Color(0x0F000000)
    val CardBorder   = Color(0x18000000)
    val Outline      = Color(0x24000000)
    val InputBorder  = Color(0x28000000)
    val ReadOnlyFill = Color(0x08000000)
    /** Darkens a card while something happens on it. */
    val Overlay      = Color(0x28000000)
    val Scrollbar    = Color(0x55000000)
    /** An avatar with nobody behind it. */
    val Vacant       = Color(0xFFBBBBBB)
}

/** The same on the dark chrome (Slate). */
object AppOnDark {
    val Fill          = Color(0x14FFFFFF)
    val Border        = Color(0x18FFFFFF)
    val Button        = Color(0x20FFFFFF)
    val Subtle        = Color(0x33FFFFFF)
    val Track         = Color(0x40FFFFFF)
    val TextFaint     = Color(0x66FFFFFF)
    val TextMuted     = Color(0x88FFFFFF)
    val TextSecondary = Color(0xB3FFFFFF)
}

/** The camera scanner's viewfinder. */
object AppCamera {
    val Scrim     = Color(0x66000000)
    val Mask      = Color(0x99000000)
    val Laser     = Color(0xFFFF2D2D)
    val LaserGlow = Color(0x55FF2D2D)
}

val AppLightColors = lightColorScheme(
    primary        = AppPalette.Coral,
    onPrimary      = Color.White,
    primaryContainer = AppPalette.CreamAlt,
    onPrimaryContainer = AppPalette.Ink,

    secondary      = AppPalette.Slate,
    onSecondary    = Color.White,

    tertiary       = AppPalette.Teal,
    onTertiary     = Color.White,

    background     = AppPalette.Cream,
    onBackground   = AppPalette.Ink,

    surface        = Color.White,
    onSurface      = AppPalette.Ink,
    surfaceVariant = AppPalette.CreamAlt,
    onSurfaceVariant = AppPalette.Ink2,

    error          = AppStatus.Empty,
    onError        = Color.White,

    outline        = Color(0x24000000),
    outlineVariant = Color(0x12000000),
)

val AppDarkColors = darkColorScheme(
    primary        = AppPalette.Coral,
    onPrimary      = Color.White,
    secondary      = Color(0xFFCBD0D6),
    onSecondary    = AppPalette.Slate,
    background     = Color(0xFF0F1113),
    onBackground   = Color(0xFFE7E8EA),
    surface        = Color(0xFF15181C),
    onSurface      = Color(0xFFE7E8EA),
    surfaceVariant = Color(0xFF1F2429),
    onSurfaceVariant = Color(0xFFB6B8BC),
    error          = AppStatus.Empty,
    onError        = Color.White,
    outline        = Color(0x40FFFFFF),
    outlineVariant = Color(0x14FFFFFF),
)
