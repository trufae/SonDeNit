package com.example.sondenit.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import androidx.core.content.ContextCompat
import com.example.sondenit.data.SoundClass
import java.io.File
import kotlin.math.max

/**
 * Continuously listens to the microphone, only saves chunks when sound rises
 * above the adaptive ambient floor, and streams live levels to the UI.
 *
 * The loop blocks the calling thread. Run it on a dedicated worker thread.
 */
class AudioCaptureLoop(
    private val context: Context,
    private val outputDir: File,
    private val onChunkRecorded: (RecordedChunk) -> Unit,
    private val onLevel: (rmsDb: Float, ambientDb: Float, capturing: Boolean) -> Unit,
) {

    data class RecordedChunk(
        val file: File,
        val startTimestamp: Long,
        val durationMs: Long,
        val peakDb: Float,
        val avgDb: Float,
        val zcr: Float,
        val ambientDb: Float,
        val classification: SoundClass,
    )

    @Volatile private var running = false
    @Volatile private var paused = false

    fun isPaused(): Boolean = paused
    fun setPaused(p: Boolean) { paused = p }
    fun stop() { running = false }

    @SuppressLint("MissingPermission")
    fun run() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        val bufBytes = max(minBuf, FRAME_SAMPLES * 2 * 8)

        val record = AudioRecordFactory.create(SAMPLE_RATE, CHANNEL_IN, ENCODING, bufBytes) ?: return
        record.startRecording()
        running = true

        val frame = ShortArray(FRAME_SAMPLES)
        val gate = NoiseGate()

        // Pre-roll keeps the very start of a sound (which would otherwise be
        // clipped because we only commit to a chunk after the gate fires).
        val preRollFrames = (PRE_ROLL_MS / FRAME_DURATION_MS).coerceAtLeast(1L).toInt()
        val preRoll = ArrayDeque<ShortArray>(preRollFrames)

        var capturing = false
        var captureStart = 0L
        val captureBuf = ArrayList<ShortArray>(MAX_CAPTURE_FRAMES)
        var captureFrames = 0
        var capturePeak = 0
        var captureRmsSum = 0.0
        var captureZcrSum = 0.0
        var ambientAtStart = gate.floorDb
        var silenceFrames = 0
        val silenceLimit = (HANG_MS / FRAME_DURATION_MS).coerceAtLeast(1L).toInt()

        try {
            while (running) {
                val read = record.read(frame, 0, FRAME_SAMPLES)
                if (read <= 0) continue

                if (paused) {
                    if (capturing) {
                        finalizeChunk(
                            captureBuf, captureFrames, captureStart, capturePeak,
                            captureRmsSum, captureZcrSum, ambientAtStart,
                        )
                        capturing = false
                        captureBuf.clear(); captureFrames = 0
                        capturePeak = 0; captureRmsSum = 0.0; captureZcrSum = 0.0
                    }
                    onLevel(AudioMath.SILENCE_DB, gate.floorDb, false)
                    continue
                }

                val features = AudioMath.features(frame, read)
                val active = gate.process(features.rmsDb, capturing)
                onLevel(features.rmsDb, gate.floorDb, capturing)

                if (!capturing) {
                    // Maintain rolling pre-roll window.
                    val copy = frame.copyOf(read)
                    if (preRoll.size >= preRollFrames) preRoll.removeFirst()
                    preRoll.addLast(copy)

                    if (active) {
                        capturing = true
                        captureStart = System.currentTimeMillis() -
                                preRoll.size.toLong() * FRAME_DURATION_MS
                        ambientAtStart = gate.floorDb
                        for (f in preRoll) {
                            captureBuf.add(f)
                            captureFrames++
                            val pf = AudioMath.features(f, f.size)
                            captureRmsSum += pf.rmsDb
                            captureZcrSum += pf.zcr
                            if (pf.peak > capturePeak) capturePeak = pf.peak
                        }
                        preRoll.clear()
                        silenceFrames = 0
                    }
                } else {
                    captureBuf.add(frame.copyOf(read))
                    captureFrames++
                    captureRmsSum += features.rmsDb
                    captureZcrSum += features.zcr
                    if (features.peak > capturePeak) capturePeak = features.peak
                    silenceFrames = if (active) 0 else silenceFrames + 1

                    val tooLong = captureFrames >= MAX_CAPTURE_FRAMES
                    val finishedQuiet = silenceFrames >= silenceLimit
                    if (tooLong || finishedQuiet) {
                        finalizeChunk(
                            captureBuf, captureFrames, captureStart, capturePeak,
                            captureRmsSum, captureZcrSum, ambientAtStart,
                        )
                        capturing = false
                        captureBuf.clear(); captureFrames = 0
                        capturePeak = 0; captureRmsSum = 0.0; captureZcrSum = 0.0
                        silenceFrames = 0
                    }
                }
            }
            // If we exit while still capturing, persist what we have.
            if (capturing && captureFrames > 0) {
                finalizeChunk(
                    captureBuf, captureFrames, captureStart, capturePeak,
                    captureRmsSum, captureZcrSum, ambientAtStart,
                )
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }

    private fun finalizeChunk(
        frames: List<ShortArray>,
        frameCount: Int,
        startTimestamp: Long,
        peakSample: Int,
        rmsSum: Double,
        zcrSum: Double,
        ambientDb: Float,
    ) {
        if (frameCount <= 0) return
        // Skip ultra-short blips — usually a single loud frame and noise.
        val durationMs = frameCount.toLong() * FRAME_DURATION_MS
        if (durationMs < MIN_CHUNK_MS) return

        val total = frames.sumOf { it.size }
        val merged = ShortArray(total)
        var off = 0
        for (f in frames) {
            System.arraycopy(f, 0, merged, off, f.size)
            off += f.size
        }
        val peakDb = if (peakSample <= 0) AudioMath.SILENCE_DB
        else (20.0 * kotlin.math.log10(peakSample / AudioMath.FULL_SCALE)).toFloat()
        val avgDb = (rmsSum / frameCount).toFloat()
        val zcr = (zcrSum / frameCount).toFloat()
        val klass = SoundClassifier.classify(durationMs, peakDb, avgDb, zcr, ambientDb)

        val name = "%1\$tH-%1\$tM-%1\$tS_%1\$tL.m4a".format(java.util.Date(startTimestamp))
        val out = File(outputDir, name)
        runCatching {
            val processed = AudioLeveling.apply(
                merged,
                total,
                AudioSettings.equalizationAmount(context),
            )
            AacM4aEncoder().encode(processed, processed.size, out)
        }.onFailure {
            return
        }
        onChunkRecorded(
            RecordedChunk(
                file = out,
                startTimestamp = startTimestamp,
                durationMs = durationMs,
                peakDb = peakDb,
                avgDb = avgDb,
                zcr = zcr,
                ambientDb = ambientDb,
                classification = klass,
            )
        )
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        // 1024 samples @ 16 kHz ≈ 64 ms per frame.
        const val FRAME_SAMPLES = 1024
        const val FRAME_DURATION_MS = 1000L * FRAME_SAMPLES / SAMPLE_RATE

        private const val PRE_ROLL_MS = 500L
        private const val HANG_MS = 1500L
        private const val MIN_CHUNK_MS = 250L
        private const val MAX_CAPTURE_MS = 30_000L
        private const val MAX_CAPTURE_FRAMES = (MAX_CAPTURE_MS / FRAME_DURATION_MS).toInt()
    }
}
