package com.example.sondenit.audio

import android.content.Context
import kotlin.math.roundToInt

object AudioSettings {
    private const val PREFS = "audio_settings"
    private const val KEY_EQUALIZATION = "equalization_amount"

    const val DEFAULT_EQUALIZATION = 0f

    fun equalizationAmount(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_EQUALIZATION, DEFAULT_EQUALIZATION)
            .coerceIn(0f, 1f)

    fun setEqualizationAmount(context: Context, amount: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_EQUALIZATION, amount.coerceIn(0f, 1f))
            .apply()
    }

    fun percent(amount: Float): Int = (amount.coerceIn(0f, 1f) * 100f).roundToInt()
}
