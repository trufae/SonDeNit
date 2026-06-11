package com.example.sondenit.audio

import com.example.sondenit.data.SessionEvent
import com.example.sondenit.data.SoundClass

/**
 * A run of audio chunks that occurred close together in time. Used both for
 * statistics (interruption counting) and for the visual timeline (collapsed
 * display).
 */
data class NoiseGroup(
    val startTimestamp: Long,
    val endTimestamp: Long,
    val chunks: List<SessionEvent.AudioChunk>,
) {
    val totalDurationMs: Long get() = chunks.sumOf { it.durationMs }
    val spanMs: Long get() = (endTimestamp - startTimestamp).coerceAtLeast(0L)
    val peakDb: Float get() = chunks.maxOf { it.peakDb }
    val ambientDb: Float get() = chunks.map { it.ambientDb }.average().toFloat()
    val classCounts: Map<SoundClass, Int> get() = chunks.groupingBy { it.classification }.eachCount()

    /** The most common classification in the group, with COUGH/DOG/CAT given
     *  precedence over generic NOISE/UNKNOWN when frequencies tie. */
    val dominantClass: SoundClass
        get() {
            val counts = classCounts
            val maxCount = counts.values.max()
            val winners = counts.filter { it.value == maxCount }.keys
            return PREFERENCE.firstOrNull { it in winners } ?: winners.first()
        }

    private companion object {
        val PREFERENCE = listOf(
            SoundClass.APNEA_GASP, SoundClass.SPEECH, SoundClass.COUGH, SoundClass.DOG_BARK,
            SoundClass.CAT_MEOW, SoundClass.ESBUFEGAR, SoundClass.SNORE, SoundClass.MOVEMENT,
            SoundClass.NOISE, SoundClass.UNKNOWN,
        )
    }
}

object AudioGrouping {
    /**
     * Merge consecutive audio chunks whose start time is within [windowMs]
     * of the previous chunk's end. Non-audio events do not break a group.
     */
    fun group(chunks: List<SessionEvent.AudioChunk>, windowMs: Long): List<NoiseGroup> {
        if (chunks.isEmpty()) return emptyList()
        if (windowMs <= 0L) return chunks.map { c ->
            NoiseGroup(c.timestamp, c.timestamp + c.durationMs, listOf(c))
        }
        val sorted = chunks.sortedBy { it.timestamp }
        val out = mutableListOf<NoiseGroup>()
        var current = mutableListOf<SessionEvent.AudioChunk>()
        var currentEnd = 0L
        for (c in sorted) {
            if (current.isEmpty()) {
                current.add(c); currentEnd = c.timestamp + c.durationMs
            } else if (c.timestamp - currentEnd <= windowMs) {
                current.add(c); currentEnd = maxOf(currentEnd, c.timestamp + c.durationMs)
            } else {
                out.add(NoiseGroup(current.first().timestamp, currentEnd, current.toList()))
                current = mutableListOf(c); currentEnd = c.timestamp + c.durationMs
            }
        }
        if (current.isNotEmpty()) {
            out.add(NoiseGroup(current.first().timestamp, currentEnd, current.toList()))
        }
        return out
    }
}
