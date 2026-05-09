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
    NOISE,
    UNKNOWN,
}
