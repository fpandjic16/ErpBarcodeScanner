package com.erpbarcode.scanner.data.model

import androidx.compose.ui.graphics.Color
import com.erpbarcode.scanner.ui.theme.PrimaStatus

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
        LineStatus.EMPTY   -> PrimaStatus.Empty
        LineStatus.PARTIAL -> PrimaStatus.Partial
        LineStatus.EXACT   -> PrimaStatus.Exact
        LineStatus.OVER    -> PrimaStatus.Over
    }

/**
 * The state's colour as text: counters, labels, and solid badges carrying white text. Darker than
 * [color], which is for bars, dots and tints, so that text reaches 4.5:1. On the dark chrome
 * (Slate) keep [color]: the darker shade would be harder to read there, not easier.
 */
val LineStatus.inkColor: Color
    get() = when (this) {
        LineStatus.EMPTY   -> PrimaStatus.EmptyInk
        LineStatus.PARTIAL -> PrimaStatus.PartialInk
        LineStatus.EXACT   -> PrimaStatus.ExactInk
        LineStatus.OVER    -> PrimaStatus.OverInk
    }

/** Soft tinted background — used on chips and badges. */
val LineStatus.bgColor: Color
    get() = when (this) {
        LineStatus.EMPTY   -> PrimaStatus.EmptyBg
        LineStatus.PARTIAL -> PrimaStatus.PartialBg
        LineStatus.EXACT   -> PrimaStatus.ExactBg
        LineStatus.OVER    -> PrimaStatus.OverBg
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
