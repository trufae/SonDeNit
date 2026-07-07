package com.example.sondenit.audio

import kotlin.math.sqrt

/**
 * Detects sounds that are above the current floor but too steady to be useful
 * sleep events, such as a fan or air conditioner hum.
 */
class AmbientNoiseSuppressor(
    private val frameDurationMs: Long,
    private val minDurationMs: Long = 12_000L,
    private val maxRmsStdDevDb: Double = 2.5,
    private val maxZcrStdDev: Double = 0.02,
    private val minAboveAmbientDb: Float = 3f,
) {
    private var frameCount = 0
    private var ambientAtStartDb = -55f
    private var rmsMean = 0.0
    private var rmsM2 = 0.0
    private var zcrMean = 0.0
    private var zcrM2 = 0.0

    val estimatedAmbientDb: Float
        get() = rmsMean.toFloat().coerceIn(AudioMath.SILENCE_DB, 0f)

    fun reset(ambientDb: Float) {
        frameCount = 0
        ambientAtStartDb = ambientDb
        rmsMean = 0.0
        rmsM2 = 0.0
        zcrMean = 0.0
        zcrM2 = 0.0
    }

    fun add(features: AudioFeatures): Boolean {
        frameCount++
        updateRms(features.rmsDb.toDouble())
        updateZcr(features.zcr.toDouble())

        if (frameCount * frameDurationMs < minDurationMs || frameCount < 2) return false

        val aboveAmbient = estimatedAmbientDb - ambientAtStartDb
        return aboveAmbient >= minAboveAmbientDb &&
            standardDeviation(rmsM2) <= maxRmsStdDevDb &&
            standardDeviation(zcrM2) <= maxZcrStdDev
    }

    private fun updateRms(value: Double) {
        val delta = value - rmsMean
        rmsMean += delta / frameCount
        rmsM2 += delta * (value - rmsMean)
    }

    private fun updateZcr(value: Double) {
        val delta = value - zcrMean
        zcrMean += delta / frameCount
        zcrM2 += delta * (value - zcrMean)
    }

    private fun standardDeviation(m2: Double): Double =
        if (frameCount <= 1) 0.0 else sqrt(m2 / (frameCount - 1))
}
