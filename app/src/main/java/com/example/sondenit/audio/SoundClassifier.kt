package com.example.sondenit.audio

import com.example.sondenit.data.SoundClass

/**
 * Quick rule-based classifier for a finished audio chunk. Operates only on
 * coarse summary features (duration, peak, ZCR). It is **not** a model and
 * the categories are best-effort hints — useful for friendly summaries, not
 * medical claims.
 *
 * Order of rules matters: more specific patterns are checked first so they
 * don't get swallowed by broader ones (e.g. dog barks before generic speech).
 */
object SoundClassifier {
    fun classify(
        durationMs: Long,
        peakDb: Float,
        avgDb: Float,
        zcr: Float,
        ambientDb: Float,
    ): SoundClass {
        val above = peakDb - ambientDb // signal-to-ambient margin (dB)
        return when {
            above < 6f -> SoundClass.UNKNOWN

            // Dog bark: very loud, very short transient, mid/high ZCR.
            // Discriminator vs cough: louder peak, slightly less duration.
            durationMs in 80..450 && zcr in 0.07f..0.20f && above >= 18f -> SoundClass.DOG_BARK

            // Sharp, brief, broadband transient → cough/sneeze.
            durationMs in 60..900 && zcr > 0.10f && above >= 14f -> SoundClass.COUGH

            // Cat meow: mid duration, tonal (low-medium ZCR), moderate peak.
            // Discriminator vs speech: shorter and lower ZCR (more tonal).
            durationMs in 300..1500 && zcr in 0.025f..0.08f && above >= 10f -> SoundClass.CAT_MEOW

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
