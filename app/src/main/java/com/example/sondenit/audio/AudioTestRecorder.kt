package com.example.sondenit.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AudioTestRecorder {
    private const val SAMPLE_RATE = 16_000
    private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private const val DURATION_MS = 5_000L

    @SuppressLint("MissingPermission")
    suspend fun record(
        context: Context,
        equalizationAmount: Float,
        onLevel: (rmsDb: Float, ambientDb: Float) -> Unit = { _, _ -> },
    ): File? = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return@withContext null

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        val frameSamples = AudioCaptureLoop.FRAME_SAMPLES
        val bufBytes = max(minBuf, frameSamples * 2 * 8)
        val record = AudioRecordFactory.create(SAMPLE_RATE, CHANNEL_IN, ENCODING, bufBytes)
            ?: return@withContext null

        val totalSamples = (SAMPLE_RATE * DURATION_MS / 1000L).toInt()
        val pcm = ShortArray(totalSamples)
        val frame = ShortArray(frameSamples)
        val gate = NoiseGate()
        var written = 0
        var failed = false

        try {
            record.startRecording()
            while (written < totalSamples) {
                val toRead = minOf(frame.size, totalSamples - written)
                val read = record.read(frame, 0, toRead)
                if (read > 0) {
                    System.arraycopy(frame, 0, pcm, written, read)
                    written += read
                    val features = AudioMath.features(frame, read)
                    gate.process(features.rmsDb, currentlyOpen = false)
                    onLevel(features.rmsDb, gate.floorDb)
                } else if (read < 0) {
                    failed = true
                    break
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
        if (failed || written <= 0) return@withContext null

        val output = File(context.cacheDir, "audio-test.m4a")
        val processed = AudioLeveling.apply(pcm, written, equalizationAmount)
        runCatching {
            AacM4aEncoder().encode(processed, processed.size, output)
        }.getOrNull()?.let { output }
    }
}
