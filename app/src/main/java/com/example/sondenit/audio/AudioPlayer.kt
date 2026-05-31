package com.example.sondenit.audio

import android.media.MediaPlayer
import java.io.File

/**
 * Tiny wrapper around [MediaPlayer] used by the detail screen to preview an
 * individual chunk. Only one playback is active at a time.
 */
class AudioPlayer {

    private var player: MediaPlayer? = null
    private var playingFile: File? = null

    val currentlyPlaying: File? get() = playingFile

    fun play(file: File, onFinished: () -> Unit) {
        stop()
        val mp = MediaPlayer()
        try {
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { completedPlayer ->
                if (player !== completedPlayer) {
                    runCatching { completedPlayer.release() }
                    return@setOnCompletionListener
                }
                player = null
                playingFile = null
                runCatching { completedPlayer.release() }
                onFinished()
            }
            mp.prepare()
            player = mp
            playingFile = file
            mp.start()
        } catch (t: Throwable) {
            if (player === mp) {
                player = null
                playingFile = null
            }
            mp.release()
            throw t
        }
    }

    fun stop() {
        val current = player ?: return
        player = null
        playingFile = null
        current.runCatching { if (isPlaying) stop() }
        current.release()
    }
}
