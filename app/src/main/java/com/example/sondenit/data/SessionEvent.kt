package com.example.sondenit.data

import org.json.JSONObject

/**
 * An event recorded during a sleep session. Persisted as JSONL so that
 * appends are cheap and the file can be parsed line-by-line on read.
 *
 * We deliberately keep the event stream slim: only "interesting" things
 * (state changes, screen events, detected audio chunks). High-frequency
 * RMS samples never hit disk — they live in memory and get summarised.
 */
sealed class SessionEvent {
    abstract val timestamp: Long
    abstract fun toJson(): JSONObject

    protected fun baseJson(type: String) = JSONObject().apply {
        put("type", type)
        put("ts", timestamp)
    }

    data class SessionStart(override val timestamp: Long) : SessionEvent() {
        override fun toJson() = baseJson("session_start")
    }
    data class SessionEnd(override val timestamp: Long) : SessionEvent() {
        override fun toJson() = baseJson("session_end")
    }
    data class Pause(override val timestamp: Long) : SessionEvent() {
        override fun toJson() = baseJson("pause")
    }
    data class Resume(override val timestamp: Long) : SessionEvent() {
        override fun toJson() = baseJson("resume")
    }
    data class ScreenOn(override val timestamp: Long) : SessionEvent() {
        override fun toJson() = baseJson("screen_on")
    }
    data class ScreenOff(override val timestamp: Long) : SessionEvent() {
        override fun toJson() = baseJson("screen_off")
    }
    data class AudioChunk(
        override val timestamp: Long,
        val durationMs: Long,
        val peakDb: Float,
        val avgDb: Float,
        val zcr: Float,
        val ambientDb: Float,
        val classification: SoundClass,
        val file: String,
    ) : SessionEvent() {
        override fun toJson(): JSONObject = baseJson("audio_chunk").apply {
            put("durationMs", durationMs)
            put("peakDb", peakDb.toDouble())
            put("avgDb", avgDb.toDouble())
            put("zcr", zcr.toDouble())
            put("ambientDb", ambientDb.toDouble())
            put("class", classification.name)
            put("file", file)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SessionEvent? {
            val ts = json.optLong("ts", 0L)
            return when (json.optString("type")) {
                "session_start" -> SessionStart(ts)
                "session_end" -> SessionEnd(ts)
                "pause" -> Pause(ts)
                "resume" -> Resume(ts)
                "screen_on" -> ScreenOn(ts)
                "screen_off" -> ScreenOff(ts)
                "audio_chunk" -> AudioChunk(
                    timestamp = ts,
                    durationMs = json.optLong("durationMs"),
                    peakDb = json.optDouble("peakDb", 0.0).toFloat(),
                    avgDb = json.optDouble("avgDb", 0.0).toFloat(),
                    zcr = json.optDouble("zcr", 0.0).toFloat(),
                    ambientDb = json.optDouble("ambientDb", 0.0).toFloat(),
                    classification = runCatching {
                        SoundClass.valueOf(json.optString("class", "UNKNOWN"))
                    }.getOrDefault(SoundClass.UNKNOWN),
                    file = json.optString("file"),
                )
                else -> null
            }
        }
    }
}
