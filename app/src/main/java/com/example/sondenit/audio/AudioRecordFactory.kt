package com.example.sondenit.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

object AudioRecordFactory {
    private val sources = intArrayOf(
        MediaRecorder.AudioSource.UNPROCESSED,
        MediaRecorder.AudioSource.MIC,
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
    )

    @Suppress("MissingPermission")
    fun create(sampleRate: Int, channel: Int, encoding: Int, bufferBytes: Int): AudioRecord? {
        for (source in sources) {
            val record = runCatching {
                AudioRecord(source, sampleRate, channel, encoding, bufferBytes)
            }.getOrNull()
            if (record?.state == AudioRecord.STATE_INITIALIZED) {
                return record
            }
            record?.release()
        }
        return null
    }
}
