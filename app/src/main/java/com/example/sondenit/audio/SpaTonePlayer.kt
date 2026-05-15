package com.example.sondenit.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

class SpaTonePlayer {
    @Volatile
    private var running = false

    @Volatile
    private var targetVolume = 0.018f

    private var thread: Thread? = null
    private var audioTrack: AudioTrack? = null

    fun start() {
        if (running || thread?.isAlive == true) return
        running = true
        thread = Thread(::renderLoop, "spa-tone-player").apply {
            isDaemon = true
            start()
        }
    }

    fun setBreathLevel(level: Float) {
        targetVolume = 0.018f + level.coerceIn(0f, 1f) * 0.052f
    }

    fun stop() {
        targetVolume = 0f
        running = false
    }

    private fun renderLoop() {
        val sampleRate = 44_100
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val frameCount = maxOf(minBuffer, sampleRate / 5)
        val buffer = ShortArray(frameCount)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(frameCount * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack = track

        var phaseA = 0.0
        var phaseB = 0.0
        var phaseC = 0.0
        var lfo = 0.0
        var volume = 0.0f
        val stepA = 2.0 * PI * 174.61 / sampleRate
        val stepB = 2.0 * PI * 261.63 / sampleRate
        val stepC = 2.0 * PI * 349.23 / sampleRate
        val lfoStep = 2.0 * PI * 0.025 / sampleRate

        track.play()
        while (running) {
            for (i in buffer.indices) {
                volume += (targetVolume - volume) * 0.00006f
                val shimmer = 0.92 + 0.08 * sin(lfo)
                val sample = (
                    sin(phaseA) * 0.48 +
                        sin(phaseB) * 0.24 +
                        sin(phaseC) * 0.12
                    ) * shimmer * volume
                buffer[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                phaseA += stepA
                phaseB += stepB
                phaseC += stepC
                lfo += lfoStep
                if (phaseA > 2.0 * PI) phaseA -= 2.0 * PI
                if (phaseB > 2.0 * PI) phaseB -= 2.0 * PI
                if (phaseC > 2.0 * PI) phaseC -= 2.0 * PI
                if (lfo > 2.0 * PI) lfo -= 2.0 * PI
            }
            track.write(buffer, 0, buffer.size)
        }

        targetVolume = 0f
        repeat(2) {
            for (i in buffer.indices) {
                volume *= 0.9993f
                val sample = (sin(phaseA) * 0.48 + sin(phaseB) * 0.24) * volume
                buffer[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                phaseA += stepA
                phaseB += stepB
            }
            track.write(buffer, 0, buffer.size)
        }
        track.pause()
        track.flush()
        track.release()
        audioTrack = null
        thread = null
    }
}
