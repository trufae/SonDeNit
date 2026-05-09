package com.example.sondenit.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.sondenit.audio.AudioMath
import com.example.sondenit.data.SessionEvent
import com.example.sondenit.data.SessionRepository
import com.example.sondenit.data.SleepSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RecordingState { IDLE, RECORDING, PAUSED }

data class LevelSnapshot(
    val rmsDb: Float = AudioMath.SILENCE_DB,
    val ambientDb: Float = -55f,
    val capturing: Boolean = false,
)

data class TimelineEntry(val event: SessionEvent)

/**
 * App-wide singleton that the UI observes for recording state and that the
 * [SleepRecordingService] mutates as audio is captured.
 *
 * The controller is the single source of truth so the UI can keep working
 * even if the activity restarts (the service stays alive).
 */
object RecordingController {

    private const val MAX_RECENT = 60

    private val _state = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _activeSession = MutableStateFlow<SleepSession?>(null)
    val activeSession: StateFlow<SleepSession?> = _activeSession.asStateFlow()

    private val _level = MutableStateFlow(LevelSnapshot())
    val level: StateFlow<LevelSnapshot> = _level.asStateFlow()

    private val _recentEvents = MutableStateFlow<List<TimelineEntry>>(emptyList())
    val recentEvents: StateFlow<List<TimelineEntry>> = _recentEvents.asStateFlow()

    private val _chunkCount = MutableStateFlow(0)
    val chunkCount: StateFlow<Int> = _chunkCount.asStateFlow()

    /** Wall-clock timestamp at which the current session started (ms). */
    private val _startedAt = MutableStateFlow(0L)
    val startedAt: StateFlow<Long> = _startedAt.asStateFlow()

    /** Latest waveform samples (recent rms in dB, normalised −90..0). */
    private val _waveform = MutableStateFlow(FloatArray(WAVEFORM_LEN))
    val waveform: StateFlow<FloatArray> = _waveform.asStateFlow()

    fun startSession(context: Context, repo: SessionRepository): SleepSession {
        val existing = repo.activeSession()
        val session = existing ?: repo.newSession()
        _activeSession.value = session
        _startedAt.value = session.startedAt
        if (existing == null) {
            _recentEvents.value = emptyList()
            _chunkCount.value = 0
        } else {
            // Resume: pre-load recent timeline so the user sees context.
            val events = repo.readEvents(session.id)
            _recentEvents.value = events.takeLast(MAX_RECENT).map { TimelineEntry(it) }
            _chunkCount.value = events.count { it is SessionEvent.AudioChunk }
        }
        _state.value = RecordingState.RECORDING
        val intent = Intent(context, SleepRecordingService::class.java).apply {
            action = SleepRecordingService.ACTION_START
            putExtra(SleepRecordingService.EXTRA_SESSION_ID, session.id)
            putExtra(SleepRecordingService.EXTRA_RESUMING, existing != null)
        }
        ContextCompat.startForegroundService(context, intent)
        return session
    }

    fun pause(context: Context) {
        if (_state.value != RecordingState.RECORDING) return
        _state.value = RecordingState.PAUSED
        context.startService(Intent(context, SleepRecordingService::class.java).apply {
            action = SleepRecordingService.ACTION_PAUSE
        })
    }

    fun resume(context: Context) {
        if (_state.value != RecordingState.PAUSED) return
        _state.value = RecordingState.RECORDING
        context.startService(Intent(context, SleepRecordingService::class.java).apply {
            action = SleepRecordingService.ACTION_RESUME
        })
    }

    fun stop(context: Context) {
        if (_state.value == RecordingState.IDLE) return
        context.startService(Intent(context, SleepRecordingService::class.java).apply {
            action = SleepRecordingService.ACTION_STOP
        })
    }

    // --- callbacks invoked from the service / capture loop ---

    internal fun publishLevel(rmsDb: Float, ambientDb: Float, capturing: Boolean) {
        _level.value = LevelSnapshot(rmsDb, ambientDb, capturing)
        // Append a normalised value to the rolling waveform.
        val norm = ((rmsDb - AudioMath.SILENCE_DB) / -AudioMath.SILENCE_DB).coerceIn(0f, 1f)
        val arr = _waveform.value
        val next = FloatArray(arr.size)
        System.arraycopy(arr, 1, next, 0, arr.size - 1)
        next[arr.size - 1] = norm
        _waveform.value = next
    }

    internal fun publishEvent(event: SessionEvent) {
        val list = _recentEvents.value.toMutableList()
        list.add(TimelineEntry(event))
        while (list.size > MAX_RECENT) list.removeAt(0)
        _recentEvents.value = list
        if (event is SessionEvent.AudioChunk) {
            _chunkCount.value = _chunkCount.value + 1
        }
    }

    internal fun onSessionEnded() {
        _state.value = RecordingState.IDLE
        _activeSession.value = null
        _level.value = LevelSnapshot()
        _waveform.value = FloatArray(WAVEFORM_LEN)
    }

    const val WAVEFORM_LEN = 96
}
