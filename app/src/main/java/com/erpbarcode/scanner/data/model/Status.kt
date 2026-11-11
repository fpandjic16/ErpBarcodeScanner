package com.erpbarcode.scanner.data.model

import androidx.compose.ui.graphics.Color
import com.erpbarcode.scanner.ui.theme.AppStatus

/**
 * The four-state status language used across every screen that
 * compares scanned vs expected quantity.
 *
 * Cannot go negative — repository must floor at 0 before computing.
 */
enum class LineStatus {
    EMPTY, PARTIAL, EXACT, OVER;

    companion object {
        fun of(scanned: Double, expected: Double): LineStatus = when {
            scanned == 0.0     -> EMPTY
            scanned < expected -> PARTIAL
            scanned == expected -> EXACT
            else               -> OVER
        }
    }
}

/** Foreground / accent color for each state. */
val LineStatus.color: Color
    get() = when (this) {
        LineStatus.EMPTY   -> AppStatus.Empty
        LineStatus.PARTIAL -> AppStatus.Partial
        LineStatus.EXACT   -> AppStatus.Exact
        LineStatus.OVER    -> AppStatus.Over
    }

/**
 * The state's colour as text: counters, labels, and solid badges carrying white text. Darker than
 * [color], which is for bars, dots and tints, so that text reaches 4.5:1. On the dark chrome
 * (Slate) keep [color]: the darker shade would be harder to read there, not easier.
 */
val LineStatus.inkColor: Color
    get() = when (this) {
        LineStatus.EMPTY   -> AppStatus.EmptyInk
        LineStatus.PARTIAL -> AppStatus.PartialInk
        LineStatus.EXACT   -> AppStatus.ExactInk
        LineStatus.OVER    -> AppStatus.OverInk
    }

/** Soft tinted background — used on chips and badges. */
val LineStatus.bgColor: Color
    get() = when (this) {
        LineStatus.EMPTY   -> AppStatus.EmptyBg
        LineStatus.PARTIAL -> AppStatus.PartialBg
        LineStatus.EXACT   -> AppStatus.ExactBg
        LineStatus.OVER    -> AppStatus.OverBg
    }

fun Document.scanStatus(): LineStatus {
    if (lines.isEmpty()) return LineStatus.EMPTY
    val s = lines.map { it.status }
    return when {
        s.all { it == LineStatus.EMPTY }  -> LineStatus.EMPTY
        s.any { it == LineStatus.OVER }   -> LineStatus.OVER
        s.all { it == LineStatus.EXACT }  -> LineStatus.EXACT
        else                              -> LineStatus.PARTIAL
    }
}
