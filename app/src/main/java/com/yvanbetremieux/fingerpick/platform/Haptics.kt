package com.yvanbetremieux.fingerpick.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class Haptics(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    // Built once: these fire on touch and per countdown second.
    private val touchEffect = VibrationEffect.createOneShot(12, 90)
    private val tickEffect = VibrationEffect.createOneShot(28, 180)
    private val revealEffect =
        VibrationEffect.createWaveform(longArrayOf(0, 70, 60, 180), intArrayOf(0, 255, 0, 255), -1)

    fun touch() = vibrate(touchEffect)
    fun tick() = vibrate(tickEffect)
    fun reveal() = vibrate(revealEffect)

    private fun vibrate(effect: VibrationEffect) {
        val v = vibrator ?: return
        if (v.hasVibrator()) v.vibrate(effect)
    }
}
