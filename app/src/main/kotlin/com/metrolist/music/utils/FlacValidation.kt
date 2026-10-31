package com.metrolist.music.utils

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException

internal class FlacSampleCounter(private val expected: Long) {
    private var decoded = 0L
    fun add(frames: Long) {
        if (frames < 0 || frames > expected - decoded) throw IOException("Invalid decoded FLAC length")
        decoded += frames
    }
    fun finish() { if (decoded != expected) throw IOException("The FLAC audio is incomplete") }
}

/** Header duration alone also survives truncation: decode to EOS and count actual PCM samples. */
internal suspend fun validateCompleteFlac(file: File, expectedDuration: Int) {
    val header = ByteArray(42)
    file.inputStream().use { input -> java.io.DataInputStream(input).readFully(header) }
    val expected = inspectFlacHeader(header, expectedDuration)
    val counter = FlacSampleCounter(expected.samples)
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    try {
        extractor.setDataSource(file.absolutePath)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == "audio/flac"
        } ?: throw IOException("No FLAC audio track")
        val format = extractor.getTrackFormat(track)
        extractor.selectTrack(track)
        val decoder = MediaCodec.createDecoderByType("audio/flac")
        codec = decoder
        decoder.configure(format, null, null, 0)
        decoder.start()
        var inputEnded = false
        var outputEnded = false
        var channels = expected.channels
        var sampleBytes = 2
        var lastProgress = SystemClock.elapsedRealtime()
        val deadline = lastProgress + 120_000
        val info = MediaCodec.BufferInfo()
        while (!outputEnded) {
            currentCoroutineContext().ensureActive()
            val now = SystemClock.elapsedRealtime()
            if (now > deadline || now - lastProgress > 15_000) throw IOException("FLAC verification timed out")
            if (!inputEnded) {
                val index = decoder.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    val buffer = decoder.getInputBuffer(index) ?: throw IOException("No decoder input")
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputEnded = true
                    } else {
                        decoder.queueInputBuffer(index, 0, size, extractor.sampleTime.coerceAtLeast(0), 0)
                        extractor.advance()
                    }
                    lastProgress = now
                }
            }
            val index = decoder.dequeueOutputBuffer(info, 10_000)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val output = decoder.outputFormat
                channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                if (channels != expected.channels || output.getInteger(MediaFormat.KEY_SAMPLE_RATE) != expected.sampleRate)
                    throw IOException("Unexpected decoder audio format")
                val encoding = if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) output.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                sampleBytes = when (encoding) {
                    AudioFormat.ENCODING_PCM_8BIT -> 1
                    AudioFormat.ENCODING_PCM_16BIT -> 2
                    AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                    AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 4
                    else -> throw IOException("Unsupported decoder output")
                }
            } else if (index >= 0) {
                try {
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val frameSize = channels * sampleBytes
                        if (info.size % frameSize != 0) throw IOException("Invalid decoded audio frame")
                        counter.add(info.size.toLong() / frameSize)
                    }
                    outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    lastProgress = now
                } finally { decoder.releaseOutputBuffer(index, false) }
            }
        }
        counter.finish()
    } finally {
        runCatching { codec?.stop() }
        codec?.release()
        extractor.release()
    }
}
