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
    private var onComplete: (() -> Unit)? = null

    val currentlyPlaying: File? get() = playingFile

    fun play(file: File, onFinished: () -> Unit) {
        stop()
        val mp = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                playingFile = null
                onComplete?.invoke()
                onComplete = null
            }
            prepare()
            start()
        }
        player = mp
        playingFile = file
        onComplete = onFinished
    }

    fun stop() {
        player?.runCatching { if (isPlaying) stop() }
        player?.release()
        player = null
        playingFile = null
        onComplete = null
    }
}
