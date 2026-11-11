package com.erpbarcode.scanner.data.haptic

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

class HapticEngine(context: Context) {
    private val vibrator = context.getSystemService(Vibrator::class.java)

    // No API guard here on purpose. VibrationEffect and vibrate(VibrationEffect) both arrived in
    // API 26, which is this app's minSdk, so there is nothing to guard against. The check that
    // used to sit here also could not have worked: every caller builds its VibrationEffect in the
    // argument to this method, so on a pre-26 device the failure would land there, before the
    // guard was ever reached.
    private fun vibrate(effect: VibrationEffect) = vibrator.vibrate(effect)

    // Very light tap — keypad key press
    fun tick() = vibrate(VibrationEffect.createOneShot(12, 60))

    // Medium pulse — quantity increment / decrement
    fun bump() = vibrate(VibrationEffect.createOneShot(28, VibrationEffect.DEFAULT_AMPLITUDE))

    // Short single pulse — confirmed scan or exact-quantity reached
    fun confirm() = vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))

    // Double pulse (growing) — unknown barcode or scan error
    fun error() = vibrate(VibrationEffect.createWaveform(longArrayOf(0, 70, 60, 120), -1))
}
