package com.example.sondenit.audio

import kotlin.math.log10
import kotlin.math.sqrt

/** Frame-level acoustic features extracted from a PCM-16 buffer. */
data class AudioFeatures(val rmsDb: Float, val zcr: Float, val peak: Int)

object AudioMath {

    /** Compute RMS (in dBFS, 0 dB = full scale, negative below) and ZCR. */
    fun features(pcm: ShortArray, len: Int = pcm.size): AudioFeatures {
        if (len <= 0) return AudioFeatures(SILENCE_DB, 0f, 0)
        var sumSq = 0.0
        var crossings = 0
        var peak = 0
        var prevSign = 0
        for (i in 0 until len) {
            val s = pcm[i].toInt()
            val abs = if (s < 0) -s else s
            if (abs > peak) peak = abs
            sumSq += (s * s).toDouble()
            val sign = if (s > 0) 1 else if (s < 0) -1 else prevSign
            if (sign != 0 && prevSign != 0 && sign != prevSign) crossings++
            if (sign != 0) prevSign = sign
        }
        val rms = sqrt(sumSq / len)
        val rmsDb = if (rms <= 0.0) SILENCE_DB
        else (20.0 * log10(rms / FULL_SCALE)).toFloat().coerceAtLeast(SILENCE_DB)
        val zcr = crossings.toFloat() / len
        return AudioFeatures(rmsDb, zcr, peak)
    }

    const val SILENCE_DB = -90f
    const val FULL_SCALE = 32767.0
}
