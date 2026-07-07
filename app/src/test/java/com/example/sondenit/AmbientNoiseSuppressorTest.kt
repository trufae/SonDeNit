package com.example.sondenit

import com.example.sondenit.audio.AmbientNoiseSuppressor
import com.example.sondenit.audio.AudioFeatures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientNoiseSuppressorTest {
    @Test
    fun steadyFanLikeSoundIsSuppressedAfterMinimumDuration() {
        val suppressor = AmbientNoiseSuppressor(frameDurationMs = 100L, minDurationMs = 1_000L)
        suppressor.reset(ambientDb = -55f)

        var suppressed = false
        repeat(10) { i ->
            suppressed = suppressor.add(
                AudioFeatures(
                    rmsDb = -40f + if (i % 2 == 0) 0.3f else -0.3f,
                    zcr = 0.11f + if (i % 2 == 0) 0.001f else -0.001f,
                    peak = 500,
                )
            )
        }

        assertTrue(suppressed)
    }

    @Test
    fun burstySoundIsNotSuppressed() {
        val suppressor = AmbientNoiseSuppressor(frameDurationMs = 100L, minDurationMs = 1_000L)
        suppressor.reset(ambientDb = -55f)

        var suppressed = false
        repeat(20) { i ->
            val loud = i % 4 == 0
            suppressed = suppressor.add(
                AudioFeatures(
                    rmsDb = if (loud) -36f else -55f,
                    zcr = if (loud) 0.12f else 0.03f,
                    peak = if (loud) 1200 else 100,
                )
            )
        }

        assertFalse(suppressed)
    }
}
