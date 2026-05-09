package com.example.sondenit.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Filesystem-backed session store. Each session has its own directory under
 * filesDir/sessions/<id>/ containing meta.json, events.jsonl, stats.json and
 * an audio/ subfolder with the recorded chunks.
 *
 *   filesDir/sessions/
 *     20260508-220130/
 *       meta.json
 *       events.jsonl
 *       stats.json          (only present once the session ends)
 *       audio/
 *         22-31-05.m4a
 */
class SessionRepository(context: Context) {

    private val root: File = File(context.filesDir, "sessions").apply { mkdirs() }
    private val activeMarker = File(context.filesDir, "active_session.txt")

    fun newSessionId(now: Long = System.currentTimeMillis()): String =
        ID_FORMAT.format(Date(now))

    fun newSession(now: Long = System.currentTimeMillis()): SleepSession {
        val id = newSessionId(now)
        val displayName = DISPLAY_FORMAT.format(Date(now))
        val s = SleepSession(id, displayName, now, null, now)
        sessionDir(s.id).mkdirs()
        audioDir(s.id).mkdirs()
        writeMeta(s)
        markActive(id)
        return s
    }

    fun activeSession(): SleepSession? {
        if (!activeMarker.exists()) return null
        val id = activeMarker.readText().trim()
        if (id.isEmpty()) return null
        val s = readSession(id) ?: return null
        return if (s.isActive) s else { clearActive(); null }
    }

    fun markActive(id: String) {
        FileWriter(activeMarker, false).use { it.write(id) }
    }

    fun clearActive() {
        if (activeMarker.exists()) activeMarker.delete()
    }

    fun listSessions(): List<SleepSession> =
        (root.listFiles()?.toList() ?: emptyList())
            .mapNotNull { readSession(it.name) }
            .sortedByDescending { it.startedAt }

    fun readSession(id: String): SleepSession? {
        val meta = File(sessionDir(id), META_FILE)
        if (!meta.exists()) return null
        return runCatching {
            SleepSession.fromJson(JSONObject(meta.readText()))
        }.getOrNull()
    }

    fun rename(id: String, newName: String) {
        val s = readSession(id) ?: return
        writeMeta(s.copy(displayName = newName))
    }

    fun finish(id: String, endedAt: Long) {
        val s = readSession(id) ?: return
        writeMeta(s.copy(endedAt = endedAt))
        clearActive()
        // Pre-compute stats so the dashboard opens instantly.
        val events = readEvents(id)
        val stats = SessionStatsComputer.compute(events, endedAt)
        File(sessionDir(id), STATS_FILE).writeText(stats.toJson().toString())
    }

    fun delete(id: String) {
        if (activeSession()?.id == id) clearActive()
        sessionDir(id).deleteRecursively()
    }

    fun appendEvent(id: String, event: SessionEvent) {
        val f = File(sessionDir(id), EVENTS_FILE)
        FileWriter(f, true).use { w ->
            w.append(event.toJson().toString())
            w.append('\n')
        }
    }

    fun readEvents(id: String): List<SessionEvent> {
        val f = File(sessionDir(id), EVENTS_FILE)
        if (!f.exists()) return emptyList()
        return f.useLines { lines ->
            lines.mapNotNull { line ->
                if (line.isBlank()) null
                else runCatching { SessionEvent.fromJson(JSONObject(line)) }.getOrNull()
            }.toList()
        }
    }

    fun readStats(id: String): SessionStats? {
        val f = File(sessionDir(id), STATS_FILE)
        if (f.exists()) {
            return runCatching { SessionStats.fromJson(JSONObject(f.readText())) }.getOrNull()
        }
        // Compute lazily for sessions that ended before stats were cached.
        val events = readEvents(id)
        if (events.isEmpty()) return null
        return SessionStatsComputer.compute(events)
    }

    fun audioDir(id: String): File =
        File(sessionDir(id), AUDIO_DIR).apply { mkdirs() }

    fun sessionDir(id: String): File = File(root, id)

    private fun writeMeta(s: SleepSession) {
        File(sessionDir(s.id), META_FILE).writeText(s.toJson().toString())
    }

    companion object {
        private const val META_FILE = "meta.json"
        private const val EVENTS_FILE = "events.jsonl"
        private const val STATS_FILE = "stats.json"
        private const val AUDIO_DIR = "audio"

        private val ID_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        private val DISPLAY_FORMAT = SimpleDateFormat("EEEE d 'de' MMMM", Locale("ca"))
    }
}
