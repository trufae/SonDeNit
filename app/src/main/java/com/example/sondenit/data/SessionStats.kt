package com.example.sondenit.data

import com.example.sondenit.audio.AudioGrouping
import com.example.sondenit.audio.NoiseGroup
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/**
 * Aggregated, derived statistics for a finished session. Re-computed on
 * demand from the events file using the user-configurable [groupingWindowMs]
 * and [minInterruptionMs]. The cache (stats.json) only stores the values
 * computed with default parameters; the Detail screen always recomputes
 * live so the sliders give immediate feedback.
 */
data class SessionStats(
    val totalDurationMs: Long,
    val sleptDurationMs: Long,
    val pausedDurationMs: Long,
    val audioChunkCount: Int,
    val audioGroupCount: Int,
    val audioChunksByClass: Map<SoundClass, Int>,
    val screenOnEvents: Int,
    val movementEvents: Int,
    val wakeMovementEvents: Int,
    val interruptions: Int,
    val apneaEvents: Int,
    val ambientAvgDb: Float,
    val phaseDurations: Map<SleepPhase, Long>,
    val qualityScore: Int, // 0..100
    val signals: List<DetectedSignal>,
    val groupingWindowMs: Long,
    val minInterruptionMs: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("totalDurationMs", totalDurationMs)
        put("sleptDurationMs", sleptDurationMs)
        put("pausedDurationMs", pausedDurationMs)
        put("audioChunkCount", audioChunkCount)
        put("audioGroupCount", audioGroupCount)
        put("screenOnEvents", screenOnEvents)
        put("movementEvents", movementEvents)
        put("wakeMovementEvents", wakeMovementEvents)
        put("interruptions", interruptions)
        put("apneaEvents", apneaEvents)
        put("ambientAvgDb", ambientAvgDb.toDouble())
        put("qualityScore", qualityScore)
        put("groupingWindowMs", groupingWindowMs)
        put("minInterruptionMs", minInterruptionMs)
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
        const val DEFAULT_GROUPING_WINDOW_MS = 30_000L
        const val DEFAULT_MIN_INTERRUPTION_MS = 2_000L

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
                audioGroupCount = json.optInt("audioGroupCount", json.optInt("audioChunkCount")),
                audioChunksByClass = classes,
                screenOnEvents = json.optInt("screenOnEvents"),
                movementEvents = json.optInt("movementEvents"),
                wakeMovementEvents = json.optInt("wakeMovementEvents"),
                interruptions = json.optInt("interruptions"),
                apneaEvents = json.optInt("apneaEvents"),
                ambientAvgDb = json.optDouble("ambientAvgDb", 0.0).toFloat(),
                phaseDurations = phases,
                qualityScore = json.optInt("qualityScore"),
                signals = signals,
                groupingWindowMs = json.optLong("groupingWindowMs", DEFAULT_GROUPING_WINDOW_MS),
                minInterruptionMs = json.optLong("minInterruptionMs", DEFAULT_MIN_INTERRUPTION_MS),
            )
        }
    }
}

enum class DetectedSignal {
    SPEECH, COUGH, MOVEMENT, SNORE, IRREGULAR_BREATHING, DOG_BARKING, CAT_MEOWING,
    APNEA, PANTING,
}

/**
 * Compute aggregated stats from a chronological list of events.
 *
 * Audio chunks closer than [groupingWindowMs] are merged; only groups whose
 * total recorded audio is at least [minInterruptionMs] are counted as
 * interruptions. The phase estimator and quality score react to the grouped
 * count, so the user sees the effect of both sliders in real time.
 */
object SessionStatsComputer {
    fun compute(
        events: List<SessionEvent>,
        fallbackEnd: Long? = null,
        groupingWindowMs: Long = SessionStats.DEFAULT_GROUPING_WINDOW_MS,
        minInterruptionMs: Long = SessionStats.DEFAULT_MIN_INTERRUPTION_MS,
    ): SessionStats {
        if (events.isEmpty()) return empty(groupingWindowMs, minInterruptionMs)
        val start = events.first().timestamp
        val end = (events.lastOrNull { it is SessionEvent.SessionEnd }?.timestamp)
            ?: fallbackEnd
            ?: events.last().timestamp
        val total = max(0L, end - start)

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
        val motion = events.filterIsInstance<SessionEvent.Motion>()
        val wakeMotion = motion.filter { it.wakeEvent }
        val ambientAvg = if (audio.isEmpty()) 0f
        else audio.map { it.ambientDb }.average().toFloat()

        val groups = AudioGrouping.group(audio, groupingWindowMs)
        val significantGroups = groups.filter { it.totalDurationMs >= minInterruptionMs }

        // Phase estimation buckets — significant noise events drive activity,
        // small ones get smoothed away by the min-length filter.
        val bucketMs = 5 * 60_000L
        val buckets = ((total + bucketMs - 1) / bucketMs).toInt().coerceAtLeast(1)
        val activity = FloatArray(buckets)
        val pauseMask = BooleanArray(buckets)

        significantGroups.forEach { g ->
            val rel = (g.startTimestamp - start).coerceIn(0, total)
            val idx = (rel / bucketMs).toInt().coerceIn(0, buckets - 1)
            val loudness = ((g.peakDb - g.ambientDb).coerceAtLeast(0f)) / 20f
            val durationWeight = (g.totalDurationMs / 1000f).coerceAtMost(15f) / 5f
            activity[idx] += 1f + loudness + durationWeight
        }
        motion.forEach { ev ->
            val rel = (ev.timestamp - start).coerceIn(0, total)
            val idx = (rel / bucketMs).toInt().coerceIn(0, buckets - 1)
            val accelerationWeight = (ev.peakAcceleration / 0.35f).coerceIn(0.25f, 3f)
            val durationWeight = (ev.durationMs / 1500f).coerceAtMost(2f)
            activity[idx] += accelerationWeight + durationWeight + if (ev.wakeEvent) 3f else 0f
        }
        events.forEach { ev ->
            val rel = (ev.timestamp - start).coerceIn(0, total)
            val idx = (rel / bucketMs).toInt().coerceIn(0, buckets - 1)
            when (ev) {
                is SessionEvent.ScreenOn, is SessionEvent.ScreenOff -> activity[idx] += 2f
                is SessionEvent.Pause, is SessionEvent.Resume -> activity[idx] += 1f
                else -> Unit
            }
        }
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

        // Interruptions = significant noise groups, screen-on bursts, and clear phone movement.
        val screenBursts = mutableSetOf<Int>()
        events.forEach { ev ->
            if (ev is SessionEvent.ScreenOn) {
                val rel = (ev.timestamp - start).coerceIn(0, total)
                screenBursts.add((rel / bucketMs).toInt().coerceIn(0, buckets - 1))
            }
        }
        val wakeMotionBursts = mutableSetOf<Int>()
        wakeMotion.forEach { ev ->
            val rel = (ev.timestamp - start).coerceIn(0, total)
            wakeMotionBursts.add((rel / bucketMs).toInt().coerceIn(0, buckets - 1))
        }
        val interruptions = significantGroups.size + screenBursts.size + wakeMotionBursts.size

        val deepRem = (phaseMs[SleepPhase.DEEP] ?: 0L) + (phaseMs[SleepPhase.REM] ?: 0L)
        val deepRemRatio = if (slept > 0) deepRem.toFloat() / slept else 0f
        val penalty = (interruptions * 4 + screenBursts.size * 2 + wakeMotionBursts.size * 2)
            .coerceAtMost(60)

        // Quiet-stretch bonus: a long, mostly-silent run should lift the score
        // even if the phase classifier never reached DEEP/REM. A gap between
        // two noise groups is "quiet" if it lasts at least 15 minutes, or if
        // it is shorter but interrupted by at most one small noise group
        // (total audio shorter than minInterruptionMs — the kind of tick that
        // doesn't otherwise count as an interruption).
        val quietMinGapMs = 15 * 60_000L
        var quietMs = 0L
        if (groups.size >= 2) {
            var i = 0
            while (i < groups.size - 1) {
                val a = groups[i]
                val b = groups[i + 1]
                val gap = (b.startTimestamp - a.endTimestamp).coerceAtLeast(0L)
                if (gap >= quietMinGapMs) {
                    quietMs += gap
                    i += 1
                } else if (b.totalDurationMs < minInterruptionMs && i + 2 < groups.size) {
                    // One small noise inside a short gap: roll the small
                    // group's span (and the surrounding gap) into the quiet
                    // total as long as the next gap is also short.
                    val aGap = (b.startTimestamp - a.endTimestamp).coerceAtLeast(0L)
                    val bGap = (groups[i + 2].startTimestamp - b.endTimestamp).coerceAtLeast(0L)
                    val totalStretch = aGap + b.spanMs + bGap
                    if (totalStretch >= quietMinGapMs) quietMs += totalStretch
                    i += 2
                } else {
                    i += 1
                }
            }
        }
        val quietRatio = if (slept > 0) quietMs.toFloat() / slept else 0f
        // Piecewise-linear bonus: 0 below 30% quiet, +20 at 80% or more.
        val quietBonus = if (quietRatio <= 0.3f) 0
        else ((quietRatio - 0.3f) / 0.5f * 20f).toInt().coerceIn(0, 20)

        val rawScore = (deepRemRatio * 100f).toInt() - penalty + 30 + quietBonus
        val qualityScore = rawScore.coerceIn(0, 100)

        // Apnea detection (post-hoc, on the audio chunk timeline).
        // Pattern: crescendo-snore → ≥ 10 s silent gap → broadband onset ("gasp").
        // The "silent gap" is implicit — audio chunks are only emitted when
        // the noise gate fires, so a gap of ≥ 10 s between two consecutive
        // chunks means the room was quiet for that long.
        val apneaResult = detectApneaEvents(audio, ambientAvg)
        val apneaEvents = apneaResult.count

        val signals = mutableListOf<DetectedSignal>()
        if ((classes[SoundClass.SPEECH] ?: 0) >= 2) signals.add(DetectedSignal.SPEECH)
        if ((classes[SoundClass.COUGH] ?: 0) >= 1) signals.add(DetectedSignal.COUGH)
        if ((classes[SoundClass.MOVEMENT] ?: 0) >= 3 || motion.size >= 3) {
            signals.add(DetectedSignal.MOVEMENT)
        }
        if ((classes[SoundClass.SNORE] ?: 0) >= 3) signals.add(DetectedSignal.SNORE)
        if ((classes[SoundClass.DOG_BARK] ?: 0) >= 1) signals.add(DetectedSignal.DOG_BARKING)
        if ((classes[SoundClass.CAT_MEOW] ?: 0) >= 1) signals.add(DetectedSignal.CAT_MEOWING)
        if ((classes[SoundClass.ESBUFEGAR] ?: 0) >= 2) signals.add(DetectedSignal.PANTING)
        if (apneaEvents >= 1) signals.add(DetectedSignal.APNEA)
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
            audioGroupCount = groups.size,
            audioChunksByClass = classes,
            screenOnEvents = screenOnCount,
            movementEvents = motion.size,
            wakeMovementEvents = wakeMotion.size,
            interruptions = min(interruptions, 999),
            apneaEvents = min(apneaEvents, 999),
            ambientAvgDb = ambientAvg,
            phaseDurations = phaseMs,
            qualityScore = qualityScore,
            signals = signals,
            groupingWindowMs = groupingWindowMs,
            minInterruptionMs = minInterruptionMs,
        )
    }

    /**
     * Result of [detectApneaEvents]. [gaspChunkFiles] are the file paths of
     * the audio chunks that were re-classified as the gasp that ends an
     * apneic pause. They let the timeline surface them as APNEA_GASP
     * instead of COUGH.
     */
    data class ApneaResult(val count: Int, val gaspChunkFiles: Set<String>)

    /**
     * Public entry point used by the Detail screen to re-classify gasp
     * chunks in the event stream. Returns the same count/stats-integrity as
     * the detector run inside [compute].
     */
    fun detectApneaEventsPublic(
        audio: List<SessionEvent.AudioChunk>,
        ambientAvg: Float,
    ): ApneaResult = detectApneaEvents(audio, ambientAvg)

    /**
     * Compute stats and the set of audio chunks that should be re-labelled
     * from COUGH to APNEA_GASP. The Detail screen uses this to render the
     * timeline with the post-apneic gasps visible.
     */
    fun computeWithRelabels(
        events: List<SessionEvent>,
        fallbackEnd: Long? = null,
        groupingWindowMs: Long = SessionStats.DEFAULT_GROUPING_WINDOW_MS,
        minInterruptionMs: Long = SessionStats.DEFAULT_MIN_INTERRUPTION_MS,
    ): SessionAnalysis {
        val stats = compute(events, fallbackEnd, groupingWindowMs, minInterruptionMs)
        val audio = events.filterIsInstance<SessionEvent.AudioChunk>()
        val ambientAvg = if (audio.isEmpty()) 0f
        else audio.map { it.ambientDb }.average().toFloat()
        val gasps = detectApneaEvents(audio, ambientAvg).gaspChunkFiles
        if (gasps.isEmpty()) return SessionAnalysis(stats, events)
        val relabeled = events.map { ev ->
            if (ev is SessionEvent.AudioChunk && ev.file in gasps)
                ev.copy(classification = SoundClass.APNEA_GASP)
            else ev
        }
        return SessionAnalysis(stats, relabeled)
    }

    data class SessionAnalysis(
        val stats: SessionStats,
        val relabeledEvents: List<SessionEvent>,
    )

    /**
     * Walk the chronological list of audio chunks and detect the classic
     * obstructive-apnea pattern: a rising-intensity snore run is followed by
     * a silent gap of ≥ 10 s, and that silence is broken by a broadband,
     * impulsive onset (the recovery gasp).
     *
     * The crescendo requirement (rising peak-above-ambient across the last
     * few snore chunks before the gap) is the key feature that distinguishes
     * apneic snoring from benign, steady snoring.
     */
    private fun detectApneaEvents(
        audio: List<SessionEvent.AudioChunk>,
        ambientAvg: Float,
    ): ApneaResult {
        if (audio.size < 2) return ApneaResult(0, emptySet())
        val apneaMinGapMs = 10_000L
        val gaspLookForwardMs = 5_000L
        val crescendoWindowMs = 30_000L
        val minGaspAboveAmbient = 10f
        var count = 0
        val gasps = mutableSetOf<String>()
        var i = 0
        while (i < audio.size - 1) {
            val a = audio[i]
            if (a.classification != SoundClass.SNORE) { i++; continue }
            // Look forward for the next chunk that is ≥ apneaMinGapMs after
            // the end of the current snore chunk.
            var j = i + 1
            while (j < audio.size && audio[j].timestamp - (a.timestamp + a.durationMs) < apneaMinGapMs) j++
            if (j >= audio.size) { i++; continue }
            val b = audio[j]
            // The gap must be a real silence: no chunks in between (the
            // noise gate would have emitted a chunk for any audible event,
            // so an empty interval between two chunks means the room was
            // genuinely quiet for that long).
            if (j > i + 1) { i++; continue }
            if (b.timestamp - a.timestamp > gaspLookForwardMs + apneaMinGapMs + 60_000L) {
                // Look-forward window exceeded: the "next chunk" is too far
                // away to plausibly be the recovery gasp. Move on.
                i++; continue
            }
            val bAbove = b.peakDb - b.ambientDb
            val looksLikeBroadbandOnset =
                b.durationMs in 60..1500 &&
                b.zcr > 0.10f &&
                bAbove >= minGaspAboveAmbient
            if (!looksLikeBroadbandOnset) { i++; continue }
            // Crescendo check: among SNORE chunks in the last crescendoWindowMs
            // before a, the peak-above-ambient should be rising.
            val windowStart = a.timestamp - crescendoWindowMs
            val recentSnores = (0..i)
                .map { audio[it] }
                .filter { it.classification == SoundClass.SNORE && it.timestamp >= windowStart }
                .map { it.peakDb - it.ambientDb }
            val isCrescendo = isRising(recentSnores)
            if (!isCrescendo) { i++; continue }
            count++
            gasps.add(b.file)
            // Skip past the gasp so we don't double-count overlapping events.
            i = j + 1
        }
        return ApneaResult(count, gasps)
    }

    /**
     * True if the sequence shows a clear rising trend. We accept either
     * a monotonic rise across the tail, or a clearly positive linear slope
     * with at least three points.
     */
    private fun isRising(values: List<Float>): Boolean {
        if (values.size < 3) return false
        // Simple linear regression slope.
        val n = values.size
        val xs = (0 until n).map { it.toDouble() }
        val meanX = xs.average()
        val meanY = values.average()
        var num = 0.0
        var den = 0.0
        for (k in 0 until n) {
            num += (xs[k] - meanX) * (values[k] - meanY)
            den += (xs[k] - meanX) * (xs[k] - meanX)
        }
        if (den == 0.0) return false
        val slope = num / den
        // Slope must be clearly positive relative to the magnitude of the values.
        return slope > 0.05 && values.last() - values.first() >= 2f
    }

    private fun empty(groupingWindowMs: Long, minInterruptionMs: Long) = SessionStats(
        totalDurationMs = 0,
        sleptDurationMs = 0,
        pausedDurationMs = 0,
        audioChunkCount = 0,
        audioGroupCount = 0,
        audioChunksByClass = emptyMap(),
        screenOnEvents = 0,
        movementEvents = 0,
        wakeMovementEvents = 0,
        interruptions = 0,
        apneaEvents = 0,
        ambientAvgDb = 0f,
        phaseDurations = emptyMap(),
        qualityScore = 0,
        signals = emptyList(),
        groupingWindowMs = groupingWindowMs,
        minInterruptionMs = minInterruptionMs,
    )
}

/** Convenience helper for callers that just want the audio groups. */
fun List<SessionEvent>.audioGroups(windowMs: Long): List<NoiseGroup> =
    AudioGrouping.group(filterIsInstance<SessionEvent.AudioChunk>(), windowMs)
