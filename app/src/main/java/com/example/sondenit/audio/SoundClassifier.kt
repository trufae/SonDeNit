package com.example.sondenit.audio

import com.example.sondenit.data.SoundClass

/**
 * Quick rule-based classifier for a finished audio chunk. Operates only on
 * coarse summary features (duration, peak, ZCR). It is **not** a model and
 * the categories are best-effort hints — useful for friendly summaries, not
 * medical claims.
 */
object SoundClassifier {
    fun classify(
        durationMs: Long,
        peakDb: Float,
        avgDb: Float,
        zcr: Float,
        ambientDb: Float,
    ): SoundClass {
        val above = peakDb - ambientDb // signal-to-ambient margin
        return when {
            above < 6f -> SoundClass.UNKNOWN
            // Sharp, brief, broadband transient → cough/sneeze.
            durationMs in 60..900 && zcr > 0.10f && above >= 14f -> SoundClass.COUGH
            // Mid-length, voice-like ZCR, healthy SNR → speech / sleep-talk.
            durationMs in 700..6000 && zcr in 0.04f..0.16f && above >= 10f -> SoundClass.SPEECH
            // Long, low ZCR, sustained → snoring / heavy breathing.
            durationMs >= 1500 && zcr < 0.06f -> SoundClass.SNORE
            // Brief, low ZCR thump → bedclothes / movement / footsteps.
            durationMs < 600 && zcr < 0.05f -> SoundClass.MOVEMENT
            else -> SoundClass.NOISE
        }
    }
}
