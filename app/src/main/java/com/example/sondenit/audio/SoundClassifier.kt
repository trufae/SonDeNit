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
        // Crest factor: how peaky the amplitude is. Heavy breathing is
        // amplitude-uniform (low peak/avg gap); speech is syllabic (larger
        // peak/avg gap because syllables punctuate the envelope).
        val crest = peakDb - avgDb
        return when {
            above < 6f -> SoundClass.UNKNOWN

            // Dog bark: very loud, very short transient, mid/high ZCR.
            // Discriminator vs cough: louder peak, slightly less duration.
            durationMs in 80..450 && zcr in 0.07f..0.20f && above >= 18f -> SoundClass.DOG_BARK

            // Sharp, brief, broadband transient → cough/sneeze.
            // Note: post-apneic gasps also fit this profile and may be
            // re-labelled to APNEA_GASP later by SessionStatsComputer.
            durationMs in 60..900 && zcr > 0.10f && above >= 14f -> SoundClass.COUGH

            // Cat meow: mid duration, tonal (low-medium ZCR), moderate peak.
            // Discriminator vs speech: shorter and lower ZCR (more tonal).
            durationMs in 300..1500 && zcr in 0.025f..0.08f && above >= 10f -> SoundClass.CAT_MEOW

            // Panting / heavy breathing ("esbufegar"): long, broadband
            // (high ZCR) but amplitude-uniform (low crest factor).
            // Discriminators:
            //   vs SNORE:    ZCR is high (broadband, not tonal) and duration
            //                is short enough not to look like sustained snore.
            //   vs SPEECH:   crest factor is low — speech is syllabic and
            //                produces a larger peak-to-avg gap.
            durationMs in 1500..8000 && zcr in 0.10f..0.30f &&
                above >= 8f && crest <= 6f -> SoundClass.ESBUFEGAR

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
