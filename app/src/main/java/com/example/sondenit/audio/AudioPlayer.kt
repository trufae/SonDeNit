package com.example.sondenit.audio

import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import java.io.File
import kotlin.math.roundToInt

/**
 * Tiny wrapper around [MediaPlayer] used by the detail screen to preview an
 * individual chunk. Only one playback is active at a time.
 */
class AudioPlayer {

    private var player: MediaPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var playingFile: File? = null

    val currentlyPlaying: File? get() = playingFile

    fun play(
        file: File,
        playbackAmplificationAmount: Float = 0f,
        onFinished: () -> Unit,
    ) {
        stop()
        val mp = MediaPlayer()
        var newEnhancer: LoudnessEnhancer? = null
        try {
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { completedPlayer ->
                if (player !== completedPlayer) {
                    runCatching { completedPlayer.release() }
                    return@setOnCompletionListener
                }
                player = null
                playingFile = null
                releaseEnhancer()
                runCatching { completedPlayer.release() }
                onFinished()
            }
            mp.prepare()
            newEnhancer = createLoudnessEnhancer(mp, playbackAmplificationAmount)
            player = mp
            loudnessEnhancer = newEnhancer
            playingFile = file
            mp.start()
        } catch (t: Throwable) {
            if (player === mp) {
                player = null
                playingFile = null
                releaseEnhancer()
            } else {
                runCatching { newEnhancer?.release() }
            }
            mp.release()
            throw t
        }
    }

    fun stop() {
        val current = player ?: return
        player = null
        playingFile = null
        releaseEnhancer()
        current.runCatching { if (isPlaying) stop() }
        current.release()
    }

    private fun createLoudnessEnhancer(
        mp: MediaPlayer,
        amount: Float,
    ): LoudnessEnhancer? {
        val targetGain = (amount.coerceIn(0f, 1f) * MAX_PLAYBACK_GAIN_MB).roundToInt()
        if (targetGain <= 0) return null
        return runCatching {
            LoudnessEnhancer(mp.audioSessionId).apply {
                setTargetGain(targetGain)
                enabled = true
            }
        }.getOrNull()
    }

    private fun releaseEnhancer() {
        val enhancer = loudnessEnhancer ?: return
        loudnessEnhancer = null
        runCatching { enhancer.enabled = false }
        runCatching { enhancer.release() }
    }

    private companion object {
        private const val MAX_PLAYBACK_GAIN_MB = 2000
    }
}
