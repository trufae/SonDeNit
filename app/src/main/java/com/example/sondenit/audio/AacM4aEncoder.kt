package com.example.sondenit.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteOrder

/**
 * Encodes a buffer of mono PCM-16 samples into a self-contained .m4a (AAC-LC
 * inside an MP4 container).
 *
 * We're keeping each chunk small (typically under 30 seconds), so we can do
 * the whole encode synchronously from the capture thread once a chunk
 * closes. Android's [MediaCodec] handles AAC natively — no third-party
 * encoder library needed.
 */
class AacM4aEncoder(
    private val sampleRate: Int = 16_000,
    private val channelCount: Int = 1,
    private val bitRate: Int = 64_000,
) {

    /**
     * Encode the first [length] PCM samples of [pcm] into [output].
     * Throws on hard failures; partial output files are deleted.
     */
    fun encode(pcm: ShortArray, length: Int, output: File) {
        val format = MediaFormat.createAudioFormat(MIME, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }

        val encoder = MediaCodec.createEncoderByType(MIME)
        var muxer: MediaMuxer? = null
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false
            val info = MediaCodec.BufferInfo()
            val timeoutUs = 10_000L
            var inputIndex = 0
            var presentationUs = 0L
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val ib = encoder.dequeueInputBuffer(timeoutUs)
                    if (ib >= 0) {
                        val buf = encoder.getInputBuffer(ib) ?: continue
                        buf.clear()
                        val maxShorts = buf.capacity() / BYTES_PER_SAMPLE
                        val remaining = length - inputIndex
                        if (remaining <= 0) {
                            encoder.queueInputBuffer(
                                ib, 0, 0, presentationUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val toWrite = minOf(maxShorts, remaining)
                            buf.order(ByteOrder.LITTLE_ENDIAN)
                                .asShortBuffer()
                                .put(pcm, inputIndex, toWrite)
                            encoder.queueInputBuffer(
                                ib, 0, toWrite * BYTES_PER_SAMPLE, presentationUs, 0
                            )
                            inputIndex += toWrite
                            presentationUs += (toWrite * 1_000_000L) / (sampleRate.toLong() * channelCount)
                        }
                    }
                }

                when (val ob = encoder.dequeueOutputBuffer(info, timeoutUs)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (muxerStarted) error("Output format changed twice")
                        trackIndex = muxer!!.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    else -> if (ob >= 0) {
                        val outBuf = encoder.getOutputBuffer(ob)
                        if (outBuf != null) {
                            if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                info.size = 0
                            }
                            if (info.size > 0 && muxerStarted) {
                                outBuf.position(info.offset)
                                outBuf.limit(info.offset + info.size)
                                muxer!!.writeSampleData(trackIndex, outBuf, info)
                            }
                        }
                        encoder.releaseOutputBuffer(ob, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            output.delete()
            throw t
        } finally {
            runCatching { encoder.stop() }
            encoder.release()
            muxer?.let {
                runCatching { it.stop() }
                it.release()
            }
        }
    }

    companion object {
        private const val MIME = MediaFormat.MIMETYPE_AUDIO_AAC
        private const val BYTES_PER_SAMPLE = 2
    }
}
