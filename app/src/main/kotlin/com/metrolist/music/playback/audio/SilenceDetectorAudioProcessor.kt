/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Lightweight PCM pass-through processor that detects long stretches of near-silence.
 * When [instantModeEnabled] is true and a silence block longer than [minSilenceDurationUs]
 * is detected, [onLongSilence] is invoked exactly once per silent segment.
 */
@UnstableApi
class SilenceDetectorAudioProcessor(
    private val minSilenceDurationUs: Long = 2_000_000L,
    private val silenceThreshold: Int = 256,
    private val onLongSilence: () -> Unit,
) : BaseAudioProcessor() {
    @Volatile var meterEnabled = false
    @Volatile private var meterLevel = 0f
    @Volatile private var meterNanos = 0L
    val audioLevel: Float get() = if (meterEnabled && System.nanoTime() - meterNanos < 250_000_000L) meterLevel else 0f
    private var sampleRate = 0
    private var channelCount = 0

    @Volatile
    var instantModeEnabled = false

    @Volatile
    private var consecutiveSilentFrames = 0L

    @Volatile
    private var inSilence = false

    private var notifiedThisSilence = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        if (meterEnabled) {
            inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
            var energy = 0.0
            var count = 0
            var index = inputBuffer.position()
            while (index + 1 < inputBuffer.limit()) {
                for (channel in 0 until channelCount.coerceAtLeast(1)) {
                    val offset = index + channel * 2
                    if (offset + 1 < inputBuffer.limit()) {
                        val value = inputBuffer.getShort(offset).toDouble() / 32768.0
                        energy += value * value
                        count++
                    }
                }
                index += channelCount.coerceAtLeast(1) * 16
            }
            meterLevel = if (count > 0) (kotlin.math.sqrt(energy / count) * 1.4).toFloat().coerceIn(0f, 1f) else 0f
            meterNanos = System.nanoTime()
        } else meterLevel = 0f

        if (instantModeEnabled && sampleRate > 0 && channelCount > 0) {
            detectSilence(inputBuffer)
        } else {
            clearSilenceState()
        }

        replaceOutputBuffer(inputBuffer.remaining()).apply {
            put(inputBuffer)
            flip()
        }
    }

    private fun detectSilence(inputBuffer: ByteBuffer) {
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        val frameCount = inputBuffer.remaining() / 2 / channelCount
        val basePosition = inputBuffer.position()

        repeat(frameCount) { frameIndex ->
            var framePeak = 0
            repeat(channelCount) { channelIndex ->
                val sampleIndex = basePosition + (frameIndex * channelCount + channelIndex) * 2
                framePeak = maxOf(framePeak, abs(inputBuffer.getShort(sampleIndex).toInt()))
            }

            if (framePeak < silenceThreshold) {
                consecutiveSilentFrames++
                if (consecutiveSilentFrames * 1_000_000L / sampleRate >= minSilenceDurationUs) {
                    inSilence = true
                    if (!notifiedThisSilence) {
                        notifiedThisSilence = true
                        onLongSilence()
                    }
                }
            } else {
                clearSilenceState()
            }
        }
    }

    private fun clearSilenceState() {
        consecutiveSilentFrames = 0
        inSilence = false
        notifiedThisSilence = false
    }

    fun resetTracking() = clearSilenceState()

    fun isCurrentlySilent(): Boolean = inSilence

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) { meterLevel = 0f; clearSilenceState() }

    override fun onReset() {
        sampleRate = 0
        channelCount = 0
        clearSilenceState()
    }
}
