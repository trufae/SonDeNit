package com.example.sondenit.audio

import kotlin.math.pow

object AudioWaveform {
    const val SAMPLE_COUNT = 96

    fun visualLevel(rmsDb: Float, ambientDb: Float): Float {
        if (!rmsDb.isUsableDb()) return 0f

        val floorDb = if (ambientDb.isUsableDb()) ambientDb else DEFAULT_AMBIENT_DB
        val relativeDb = (rmsDb - floorDb.coerceIn(AudioMath.SILENCE_DB, MAX_FLOOR_DB))
            .coerceAtLeast(0f)
        val activeEnergy = if (relativeDb <= ACTIVE_DEADBAND_DB) {
            0f
        } else {
            ((relativeDb - ACTIVE_DEADBAND_DB) / ACTIVE_RANGE_DB)
                .coerceIn(0f, 1f)
                .pow(0.58f)
        }
        val roomTexture = ((rmsDb - AudioMath.SILENCE_DB) / TEXTURE_RANGE_DB)
            .coerceIn(0f, 1f) * MAX_TEXTURE_LEVEL
        return (activeEnergy + roomTexture).coerceIn(0f, 1f)
    }

    fun append(samples: FloatArray, value: Float): FloatArray {
        if (samples.isEmpty()) return floatArrayOf(value.coerceIn(0f, 1f))
        val next = FloatArray(samples.size)
        System.arraycopy(samples, 1, next, 0, samples.size - 1)
        next[samples.size - 1] = value.coerceIn(0f, 1f)
        return next
    }

    private fun Float.isUsableDb(): Boolean =
        !isNaN() && !isInfinite() && this > AudioMath.SILENCE_DB

    private const val DEFAULT_AMBIENT_DB = -55f
    private const val MAX_FLOOR_DB = -18f
    private const val ACTIVE_DEADBAND_DB = 5.5f
    private const val ACTIVE_RANGE_DB = 28f
    private const val TEXTURE_RANGE_DB = 62f
    private const val MAX_TEXTURE_LEVEL = 0.045f
}
