package com.example.sondenit.audio

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

object AudioLeveling {
    private const val TARGET_RMS_DB = -20f
    private const val MAX_GAIN_DB = 14f
    private const val FULL_SCALE = 32767f

    fun apply(pcm: ShortArray, length: Int, amount: Float): ShortArray {
        val strength = amount.coerceIn(0f, 1f)
        if (strength <= 0.001f || length <= 0) return pcm.copyOf(length)

        var sumSq = 0.0
        var peak = 0
        for (i in 0 until length) {
            val s = pcm[i].toInt()
            val abs = if (s < 0) -s else s
            if (abs > peak) peak = abs
            sumSq += (s * s).toDouble()
        }
        if (peak <= 0) return pcm.copyOf(length)

        val rms = sqrt(sumSq / length).toFloat()
        val rmsDb = (20f * log10((rms / FULL_SCALE).coerceAtLeast(0.00001f)))
        val wantedGainDb = (TARGET_RMS_DB - rmsDb).coerceIn(0f, MAX_GAIN_DB)
        val gainDb = wantedGainDb * strength
        val requestedGain = 10f.pow(gainDb / 20f)
        val peakSafeGain = (FULL_SCALE / peak).coerceAtLeast(1f)
        val gain = requestedGain.coerceAtMost(peakSafeGain)

        val out = ShortArray(length)
        for (i in 0 until length) {
            out[i] = (pcm[i] * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }
}
