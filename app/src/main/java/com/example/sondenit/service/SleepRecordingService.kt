package com.example.sondenit.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.example.sondenit.MainActivity
import com.example.sondenit.R
import com.example.sondenit.audio.AudioCaptureLoop
import com.example.sondenit.data.SessionEvent
import com.example.sondenit.data.SessionRepository

/**
 * Long-running recording service. Owns the audio capture thread, the screen
 * state receiver, the wake lock, and persists every event to disk.
 *
 * State that the UI cares about lives in [RecordingController]; the service
 * is just the engine that mutates it.
 */
class SleepRecordingService : Service() {

    private lateinit var repo: SessionRepository
    private var captureLoop: AudioCaptureLoop? = null
    private var captureThread: Thread? = null
    private var screenReceiver: ScreenStateReceiver? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var sessionId: String? = null
    private var paused: Boolean = false

    override fun onCreate() {
        super.onCreate()
        repo = SessionRepository(applicationContext)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_STOP -> handleStop()
        }
        return START_STICKY
    }

    private fun handleStart(intent: Intent) {
        val id = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        val resuming = intent.getBooleanExtra(EXTRA_RESUMING, false)
        if (sessionId != null) return // already running

        sessionId = id
        startForegroundCompat()
        acquireWakeLock()
        registerScreenReceiver()
        appendEvent(
            if (resuming) SessionEvent.Resume(System.currentTimeMillis())
            else SessionEvent.SessionStart(System.currentTimeMillis())
        )
        startCaptureThread(id)
    }

    private fun startCaptureThread(id: String) {
        val outputDir = repo.audioDir(id)
        val loop = AudioCaptureLoop(
            context = applicationContext,
            outputDir = outputDir,
            onChunkRecorded = { chunk ->
                val rel = "audio/${chunk.file.name}"
                appendEvent(
                    SessionEvent.AudioChunk(
                        timestamp = chunk.startTimestamp,
                        durationMs = chunk.durationMs,
                        peakDb = chunk.peakDb,
                        avgDb = chunk.avgDb,
                        zcr = chunk.zcr,
                        ambientDb = chunk.ambientDb,
                        classification = chunk.classification,
                        file = rel,
                    )
                )
            },
            onLevel = { rms, amb, cap ->
                RecordingController.publishLevel(rms, amb, cap)
            },
        )
        captureLoop = loop
        val t = Thread({ loop.run() }, "AudioCaptureLoop").apply {
            priority = Thread.MAX_PRIORITY - 1
        }
        captureThread = t
        t.start()
    }

    private fun handlePause() {
        if (sessionId == null || paused) return
        paused = true
        captureLoop?.setPaused(true)
        appendEvent(SessionEvent.Pause(System.currentTimeMillis()))
    }

    private fun handleResume() {
        if (sessionId == null || !paused) return
        paused = false
        captureLoop?.setPaused(false)
        appendEvent(SessionEvent.Resume(System.currentTimeMillis()))
    }

    private fun handleStop() {
        val id = sessionId ?: run {
            stopSelfNow(); return
        }
        val now = System.currentTimeMillis()
        appendEvent(SessionEvent.SessionEnd(now))
        captureLoop?.stop()
        runCatching { captureThread?.join(2_000L) }
        captureLoop = null
        captureThread = null
        runCatching { repo.finish(id, now) }
        sessionId = null
        paused = false
        unregisterScreenReceiver()
        releaseWakeLock()
        RecordingController.onSessionEnded()
        stopSelfNow()
    }

    private fun stopSelfNow() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // Defensive: if the service is killed without an explicit stop, at
        // least release resources. The session remains "active" on disk so
        // the user can resume it next time the app is opened.
        captureLoop?.stop()
        runCatching { captureThread?.join(1_000L) }
        unregisterScreenReceiver()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun appendEvent(event: SessionEvent) {
        sessionId?.let { repo.appendEvent(it, event) }
        RecordingController.publishEvent(event)
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        val r = ScreenStateReceiver { on ->
            val e = if (on) SessionEvent.ScreenOn(System.currentTimeMillis())
            else SessionEvent.ScreenOff(System.currentTimeMillis())
            appendEvent(e)
        }
        registerReceiver(r, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
        screenReceiver = r
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG).apply {
            setReferenceCounted(false)
            acquire(MAX_WAKE_LOCK_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.runCatching { if (isHeld) release() }
        wakeLock = null
    }

    private fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(getString(R.string.notif_channel_name))
            .setDescription(getString(R.string.notif_channel_desc))
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        ServiceCompat.startForeground(
            this, NOTIF_ID, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.example.sondenit.action.START"
        const val ACTION_PAUSE = "com.example.sondenit.action.PAUSE"
        const val ACTION_RESUME = "com.example.sondenit.action.RESUME"
        const val ACTION_STOP = "com.example.sondenit.action.STOP"
        const val EXTRA_SESSION_ID = "sessionId"
        const val EXTRA_RESUMING = "resuming"

        private const val NOTIF_ID = 11
        private const val CHANNEL_ID = "sondenit-recording"
        private const val WAKE_TAG = "SonDeNit:recording"
        private const val MAX_WAKE_LOCK_MS = 12L * 60L * 60L * 1000L
    }
}
