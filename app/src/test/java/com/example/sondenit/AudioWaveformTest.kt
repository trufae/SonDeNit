package com.example.sondenit

import com.example.sondenit.audio.AudioMath
import com.example.sondenit.audio.AudioWaveform
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioWaveformTest {
    @Test
    fun silenceAndAmbientStayThin() {
        assertEquals(0f, AudioWaveform.visualLevel(AudioMath.SILENCE_DB, -55f), 0.0001f)
        assertTrue(AudioWaveform.visualLevel(-55f, -55f) < 0.05f)
    }

    @Test
    fun soundAboveAmbientExpands() {
        val ambient = AudioWaveform.visualLevel(-55f, -55f)
        val active = AudioWaveform.visualLevel(-40f, -55f)

        assertTrue(active > 0.35f)
        assertTrue(active > ambient * 8f)
    }

    @Test
    fun appendRollsOldestSampleOut() {
        val next = AudioWaveform.append(floatArrayOf(0.1f, 0.2f, 0.3f), 2f)

        assertArrayEquals(floatArrayOf(0.2f, 0.3f, 1f), next, 0.0001f)
    }
}
