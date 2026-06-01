package com.example.sondenit.audio

import android.content.Context
import kotlin.math.roundToInt

object AudioSettings {
    private const val PREFS = "audio_settings"
    private const val KEY_EQUALIZATION = "equalization_amount"
    private const val KEY_RECORDING_START_DELAY_SECONDS = "recording_start_delay_seconds"

    const val DEFAULT_EQUALIZATION = 0.5f
    const val DEFAULT_RECORDING_START_DELAY_SECONDS = 5
    val RECORDING_START_DELAY_OPTIONS_SECONDS = listOf(0, 3, 5, 10, 15, 20, 30)

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

    fun recordingStartDelaySeconds(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_RECORDING_START_DELAY_SECONDS, DEFAULT_RECORDING_START_DELAY_SECONDS)
            .coerceIn(0, RECORDING_START_DELAY_OPTIONS_SECONDS.max())

    fun setRecordingStartDelaySeconds(context: Context, seconds: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_RECORDING_START_DELAY_SECONDS, seconds.coerceIn(0, RECORDING_START_DELAY_OPTIONS_SECONDS.max()))
            .apply()
    }

    fun percent(amount: Float): Int = (amount.coerceIn(0f, 1f) * 100f).roundToInt()
}
