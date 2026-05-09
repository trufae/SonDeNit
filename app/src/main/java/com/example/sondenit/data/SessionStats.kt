package com.example.sondenit.data

import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/**
 * Aggregated, derived statistics for a finished session. Computed once when
 * the session ends (and cached as stats.json) so the dashboard opens fast.
 */
data class SessionStats(
    val totalDurationMs: Long,
    val sleptDurationMs: Long,
    val pausedDurationMs: Long,
    val audioChunkCount: Int,
    val audioChunksByClass: Map<SoundClass, Int>,
    val screenOnEvents: Int,
    val interruptions: Int,
    val ambientAvgDb: Float,
    val phaseDurations: Map<SleepPhase, Long>,
    val qualityScore: Int, // 0..100
    val signals: List<DetectedSignal>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("totalDurationMs", totalDurationMs)
        put("sleptDurationMs", sleptDurationMs)
        put("pausedDurationMs", pausedDurationMs)
        put("audioChunkCount", audioChunkCount)
        put("screenOnEvents", screenOnEvents)
        put("interruptions", interruptions)
        put("ambientAvgDb", ambientAvgDb.toDouble())
        put("qualityScore", qualityScore)
        val classes = JSONObject()
        audioChunksByClass.forEach { (k, v) -> classes.put(k.name, v) }
        put("audioChunksByClass", classes)
        val phases = JSONObject()
        phaseDurations.forEach { (k, v) -> phases.put(k.name, v) }
        put("phaseDurations", phases)
        val sig = org.json.JSONArray()
        signals.forEach { sig.put(it.name) }
        put("signals", sig)
    }

    companion object {
        fun fromJson(json: JSONObject): SessionStats {
            val classes = mutableMapOf<SoundClass, Int>()
            json.optJSONObject("audioChunksByClass")?.let { obj ->
                obj.keys().forEach { k ->
                    runCatching { SoundClass.valueOf(k) }.getOrNull()
                        ?.let { classes[it] = obj.optInt(k) }
                }
            }
            val phases = mutableMapOf<SleepPhase, Long>()
            json.optJSONObject("phaseDurations")?.let { obj ->
                obj.keys().forEach { k ->
                    runCatching { SleepPhase.valueOf(k) }.getOrNull()
                        ?.let { phases[it] = obj.optLong(k) }
                }
            }
            val signals = mutableListOf<DetectedSignal>()
            json.optJSONArray("signals")?.let { arr ->
                for (i in 0 until arr.length()) {
                    runCatching { DetectedSignal.valueOf(arr.optString(i)) }.getOrNull()
                        ?.let { signals.add(it) }
                }
            }
            return SessionStats(
                totalDurationMs = json.optLong("totalDurationMs"),
                sleptDurationMs = json.optLong("sleptDurationMs"),
                pausedDurationMs = json.optLong("pausedDurationMs"),
                audioChunkCount = json.optInt("audioChunkCount"),
                audioChunksByClass = classes,
                screenOnEvents = json.optInt("screenOnEvents"),
                interruptions = json.optInt("interruptions"),
                ambientAvgDb = json.optDouble("ambientAvgDb", 0.0).toFloat(),
                phaseDurations = phases,
                qualityScore = json.optInt("qualityScore"),
                signals = signals,
            )
        }
    }
}

enum class DetectedSignal { SPEECH, COUGH, MOVEMENT, SNORE, IRREGULAR_BREATHING }

/**
 * Compute aggregated stats from a chronological list of events.
 * The phase estimation is intentionally simple: a sliding window over
 * activity density determines what kind of sleep was happening.
 */
object SessionStatsComputer {
    fun compute(events: List<SessionEvent>, fallbackEnd: Long? = null): SessionStats {
        if (events.isEmpty()) return empty()
        val start = events.first().timestamp
        val end = (events.lastOrNull { it is SessionEvent.SessionEnd }?.timestamp)
            ?: fallbackEnd
            ?: events.last().timestamp
        val total = max(0L, end - start)

        // Pause windows
        var pausedMs = 0L
        var pauseStart: Long? = null
        events.forEach {
            when (it) {
                is SessionEvent.Pause -> pauseStart = it.timestamp
                is SessionEvent.Resume -> pauseStart?.let { ps ->
                    pausedMs += max(0L, it.timestamp - ps); pauseStart = null
                }
                is SessionEvent.SessionEnd -> pauseStart?.let { ps ->
                    pausedMs += max(0L, it.timestamp - ps); pauseStart = null
                }
                else -> Unit
            }
        }
        val slept = max(0L, total - pausedMs)

        val audio = events.filterIsInstance<SessionEvent.AudioChunk>()
        val classes = audio.groupingBy { it.classification }.eachCount()
        val screenOnCount = events.count { it is SessionEvent.ScreenOn }
        val ambientAvg = if (audio.isEmpty()) 0f
        else audio.map { it.ambientDb }.average().toFloat()

        // Phase estimation: bucket the timeline into 5-minute slots and count
        // "activity points" per slot (audio chunks weighted by loudness, plus
        // screen/pause events). Map activity density → phase.
        val bucketMs = 5 * 60_000L
        val buckets = ((total + bucketMs - 1) / bucketMs).toInt().coerceAtLeast(1)
        val activity = FloatArray(buckets)
        val pauseMask = BooleanArray(buckets)

        events.forEach { ev ->
            val rel = (ev.timestamp - start).coerceIn(0, total)
            val idx = (rel / bucketMs).toInt().coerceIn(0, buckets - 1)
            when (ev) {
                is SessionEvent.AudioChunk -> {
                    val loudness = ((ev.peakDb - ev.ambientDb).coerceAtLeast(0f)) / 20f
                    activity[idx] += 1f + loudness
                }
                is SessionEvent.ScreenOn, is SessionEvent.ScreenOff -> activity[idx] += 2f
                is SessionEvent.Pause, is SessionEvent.Resume -> activity[idx] += 1f
                else -> Unit
            }
        }
        // Mark buckets entirely contained in a paused window.
        var p: Long? = null
        events.forEach { ev ->
            when (ev) {
                is SessionEvent.Pause -> p = ev.timestamp
                is SessionEvent.Resume -> {
                    p?.let { ps ->
                        val a = ((ps - start) / bucketMs).toInt().coerceIn(0, buckets - 1)
                        val b = ((ev.timestamp - start) / bucketMs).toInt().coerceIn(0, buckets - 1)
                        for (i in a..b) pauseMask[i] = true
                    }
                    p = null
                }
                else -> Unit
            }
        }

        val phaseMs = mutableMapOf<SleepPhase, Long>().withDefault { 0L }
        for (i in 0 until buckets) {
            val slotLen = if (i == buckets - 1) total - i * bucketMs else bucketMs
            if (pauseMask[i]) {
                phaseMs[SleepPhase.AWAKE] = (phaseMs.getValue(SleepPhase.AWAKE)) + slotLen
                continue
            }
            val a = activity[i]
            val phase = when {
                a >= 4f -> SleepPhase.AWAKE
                a >= 2f -> SleepPhase.REM
                a >= 0.6f -> SleepPhase.LIGHT
                else -> SleepPhase.DEEP
            }
            phaseMs[phase] = phaseMs.getValue(phase) + slotLen
        }

        // Interruptions = count of audio chunks meaningfully louder than ambient
        // OR distinct screen-on bursts (debounced into 5-min buckets).
        val noisyAudio = audio.count { it.peakDb - it.ambientDb >= 12f }
        val screenBursts = mutableSetOf<Int>()
        events.forEach { ev ->
            if (ev is SessionEvent.ScreenOn) {
                screenBursts.add(((ev.timestamp - start) / bucketMs).toInt())
            }
        }
        val interruptions = noisyAudio + screenBursts.size

        // Quality score
        val deepRem = (phaseMs[SleepPhase.DEEP] ?: 0L) + (phaseMs[SleepPhase.REM] ?: 0L)
        val deepRemRatio = if (slept > 0) deepRem.toFloat() / slept else 0f
        val penalty = (interruptions * 4 + screenBursts.size * 2).coerceAtMost(60)
        val rawScore = (deepRemRatio * 100f).toInt() - penalty + 30 // baseline boost
        val qualityScore = rawScore.coerceIn(0, 100)

        // Signals
        val signals = mutableListOf<DetectedSignal>()
        if ((classes[SoundClass.SPEECH] ?: 0) >= 2) signals.add(DetectedSignal.SPEECH)
        if ((classes[SoundClass.COUGH] ?: 0) >= 1) signals.add(DetectedSignal.COUGH)
        if ((classes[SoundClass.MOVEMENT] ?: 0) >= 3) signals.add(DetectedSignal.MOVEMENT)
        if ((classes[SoundClass.SNORE] ?: 0) >= 3) signals.add(DetectedSignal.SNORE)
        // Irregular breathing heuristic: lots of snore-like classifications with
        // wildly varying intervals between them.
        val snores = audio.filter { it.classification == SoundClass.SNORE }
        if (snores.size >= 5) {
            val gaps = snores.zipWithNext { a, b -> b.timestamp - a.timestamp }
            val mean = gaps.average()
            val variance = gaps.map { (it - mean) * (it - mean) }.average()
            val stddev = kotlin.math.sqrt(variance)
            if (mean > 0 && stddev / mean > 0.6) signals.add(DetectedSignal.IRREGULAR_BREATHING)
        }

        return SessionStats(
            totalDurationMs = total,
            sleptDurationMs = slept,
            pausedDurationMs = pausedMs,
            audioChunkCount = audio.size,
            audioChunksByClass = classes,
            screenOnEvents = screenOnCount,
            interruptions = min(interruptions, 999),
            ambientAvgDb = ambientAvg,
            phaseDurations = phaseMs,
            qualityScore = qualityScore,
            signals = signals,
        )
    }

    private fun empty() = SessionStats(
        totalDurationMs = 0,
        sleptDurationMs = 0,
        pausedDurationMs = 0,
        audioChunkCount = 0,
        audioChunksByClass = emptyMap(),
        screenOnEvents = 0,
        interruptions = 0,
        ambientAvgDb = 0f,
        phaseDurations = emptyMap(),
        qualityScore = 0,
        signals = emptyList(),
    )
}
