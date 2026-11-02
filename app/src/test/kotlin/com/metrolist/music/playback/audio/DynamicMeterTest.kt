package com.metrolist.music.playback.audio
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class DynamicMeterTest {
 @Test fun hearsRightChannelWhenLeftChannelIsSilent() {
  val p = SilenceDetectorAudioProcessor(onLongSilence = {})
  p.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)); p.flush(); p.meterEnabled = true
  val b = ByteBuffer.allocateDirect(2048).order(ByteOrder.nativeOrder())
  repeat(512) { b.putShort(0); b.putShort(24000) }; b.flip(); p.queueInput(b)
  assertTrue("Right channel must contribute to the wave", p.audioLevel > .3f)
 }
 @Test fun reactsToPcmWithoutChangingAudioAndStopsWhenDisabled() {
  val p = SilenceDetectorAudioProcessor(onLongSilence = {})
  p.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)); p.flush()
  fun feed(sample: Short): Float {
   val b = ByteBuffer.allocateDirect(2048).order(ByteOrder.nativeOrder())
   repeat(1024) { b.putShort(sample) }; b.flip(); p.queueInput(b)
   val out = p.output.order(ByteOrder.nativeOrder()); repeat(1024) { assertEquals(sample, out.short) }
   return p.audioLevel
  }
  p.meterEnabled = true
  assertEquals(0f, feed(0), .001f)
  assertTrue(feed(24000) > .5f)
  p.meterEnabled = false
  assertEquals(0f, feed(24000), .001f)
 }
}
