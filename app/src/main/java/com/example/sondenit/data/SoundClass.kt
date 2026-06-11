package com.example.sondenit.data

/**
 * Coarse acoustic categories inferred from each detected audio chunk.
 * The classification is heuristic-only (RMS / duration / zero-crossing rate);
 * it is not ML-based and only meant for friendly summaries.
 */
enum class SoundClass {
    SPEECH,
    COUGH,
    MOVEMENT,
    SNORE,
    DOG_BARK,
    CAT_MEOW,
    /**
     * Panting / heavy audible breathing ("esbufegar"). Long, broadband, with
     * a low crest factor — distinct from speech (syllabic, varying ZCR) and
     * from snore (tonal, low ZCR).
     */
    ESBUFEGAR,
    /**
     * The broadband onset ("gasp") that ends an apneic pause. Detected
     * post-hoc in SessionStatsComputer by re-labelling a COUGH-classified
     * chunk that follows a crescendo-snore → ≥10 s silence pattern.
     */
    APNEA_GASP,
    NOISE,
    UNKNOWN,
}
