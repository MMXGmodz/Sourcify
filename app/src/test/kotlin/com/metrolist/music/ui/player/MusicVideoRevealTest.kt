package com.metrolist.music.ui.player

import android.graphics.Bitmap
import android.os.Looper
import android.view.TextureView
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.FlagSet
import com.metrolist.music.playback.MusicVideoMode
import com.metrolist.music.playback.MusicVideoState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.lang.reflect.Proxy
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class MusicVideoRevealTest {
    private val first = MusicVideoFrameKey("song", "sourcify-musicvideo://session/first")
    private val second = first.copy(source = "sourcify-musicvideo://session/second")

    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)
    }

    private class Primary {
        var item: MediaItem? = null
        val listeners = linkedSetOf<Player.Listener>()
        var texture: TextureView? = null
        var attachments = 0
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "getCurrentMediaItem" -> item
                "getVideoSize" -> VideoSize(1920, 1080)
                "addListener" -> { listeners.add(args!![0] as Player.Listener); null }
                "removeListener" -> { listeners.remove(args!![0] as Player.Listener); null }
                "setVideoTextureView" -> {
                    assertNull("The previous owner must release the primary surface first", texture)
                    texture = args!![0] as TextureView
                    attachments++
                    null
                }
                "clearVideoTextureView" -> { if (texture === args!![0]) texture = null; null }
                else -> null
            }
        } as Player

        fun source(key: MusicVideoFrameKey) {
            item = MediaItem.Builder().setMediaId(key.songId).setUri(key.source).build()
            val events = Player.Events(FlagSet.Builder().add(Player.EVENT_MEDIA_ITEM_TRANSITION).build())
            listeners.toList().forEach { it.onEvents(player, events) }
        }

        fun rendered() { listeners.toList().forEach { it.onRenderedFirstFrame() } }
    }

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun advanceFrames(ms: Long) {
        repeat((ms / 16).toInt()) { idle(16) }
        if (ms % 16 > 0) idle(ms % 16)
    }
    private fun frame() = MusicVideoFrame(RuntimeEnvironment.getApplication())
    private fun video() = MusicVideoState(MusicVideoMode.VIDEO, "song")

    @Test fun sourceAndSurfaceGenerationRejectStaleReadiness() {
        val gate = MusicVideoRevealGate()
        val old = gate.attach(first)
        assertFalse(gate.rendered(old, second, true))
        assertFalse(gate.rendered(old, first, false))
        gate.detach()
        val next = gate.attach(first)
        assertFalse(gate.rendered(old, first, true))
        assertTrue(gate.rendered(next, first, true))
        assertFalse(gate.rendered(next, first, true))
        assertTrue(gate.decoded)
    }

    @Test fun textureStaysDrawableAndCoverFadesOnlyAfterDecodedFrame() {
        val owner = Owner().apply { lifecycle.currentState = Lifecycle.State.RESUMED }
        val primary = Primary().apply { source(first) }
        val frame = frame()
        val cache = MusicVideoHandoff().apply { update("song", true) }
        MusicVideoSurfaceBinding(primary.player, frame, owner.lifecycle, false, { true }, ::video, cache).use {
            idle(400)
            assertEquals(1f, frame.cover.alpha, 0f)
            assertEquals(View.VISIBLE, frame.texture.visibility)
            assertEquals(1f, frame.texture.alpha, 0f)
            val wasPaused = ShadowChoreographer.isPaused()
            val previousDelay = ShadowChoreographer.getFrameDelay()
            try {
                // The default shadow advances time on every vsync request and drains an entire animation.
                ShadowChoreographer.setPaused(true)
                ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
                primary.rendered()
                assertEquals(1f, frame.cover.alpha, .01f)
                advanceFrames(120)
                assertTrue("The decoded frame should blend through the cover; alpha=${frame.cover.alpha}",
                    frame.cover.alpha > 0f && frame.cover.alpha < 1f)
                advanceFrames(200)
                assertEquals(0f, frame.cover.alpha, .01f)
                assertEquals(View.VISIBLE, frame.texture.visibility)
                assertEquals(1f, frame.texture.alpha, 0f)
            } finally {
                ShadowChoreographer.setFrameDelay(previousDelay)
                ShadowChoreographer.setPaused(wasPaused)
            }
        }
        assertNull(primary.texture)
    }

    @Test fun pauseAndSourceReplacementInvalidateAlreadyQueuedFirstFrameEvents() {
        val owner = Owner().apply { lifecycle.currentState = Lifecycle.State.RESUMED }
        val primary = Primary().apply { source(first) }
        val frame = frame()
        val cache = MusicVideoHandoff().apply { update("song", true) }
        MusicVideoSurfaceBinding(primary.player, frame, owner.lifecycle, false, { true }, ::video, cache).use {
            val beforePause = primary.listeners.toList()
            owner.lifecycle.currentState = Lifecycle.State.STARTED
            assertNull(primary.texture)
            beforePause.forEach { it.onRenderedFirstFrame() }
            idle(300)
            assertEquals(1f, frame.cover.alpha, 0f)
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
            beforePause.forEach { it.onRenderedFirstFrame() }
            idle(300)
            assertEquals(1f, frame.cover.alpha, 0f)
            val beforeReplacement = primary.listeners.toList()
            primary.source(second)
            beforeReplacement.forEach { it.onRenderedFirstFrame() }
            idle(300)
            assertEquals(1f, frame.cover.alpha, 0f)
            primary.rendered()
            idle(300)
            assertEquals(0f, frame.cover.alpha, .01f)
        }
    }

    @Test fun pipCanBindWhileStartedAndOwnershipTransferReleasesThePrimaryTextureFirst() {
        val owner = Owner().apply { lifecycle.currentState = Lifecycle.State.STARTED }
        val primary = Primary().apply { source(first) }
        val cache = MusicVideoHandoff().apply { update("song", true) }
        val inline = MusicVideoSurfaceBinding(primary.player, frame(), owner.lifecycle, false, { true }, ::video, cache)
        assertNull(primary.texture)
        val pipFrame = frame()
        val pip = MusicVideoSurfaceBinding(primary.player, pipFrame, owner.lifecycle, true, { true }, ::video, cache)
        assertSame(pipFrame.texture, primary.texture)
        val replacementFrame = frame()
        val replacement = MusicVideoSurfaceBinding(primary.player, replacementFrame, owner.lifecycle, true, { true }, ::video, cache)
        assertSame(replacementFrame.texture, primary.texture)
        pip.close()
        inline.close()
        assertSame(replacementFrame.texture, primary.texture)
        replacement.close()
        assertNull(primary.texture)
        assertEquals(2, primary.attachments)
    }

    @Test fun anAudioItemWithVideoDimensionsCannotBindOrRevealTheVideoSurface() {
        val owner = Owner().apply { lifecycle.currentState = Lifecycle.State.RESUMED }
        val primary = Primary().apply { source(first.copy(source = "https://audio.example/song")) }
        val frame = frame()
        MusicVideoSurfaceBinding(primary.player, frame, owner.lifecycle, false, { true }, ::video,
            MusicVideoHandoff().apply { update("song", true) }).use {
            primary.rendered()
            idle(300)
            assertNull(primary.texture)
            assertEquals(1f, frame.cover.alpha, 0f)
        }
    }

    @Test fun oneBoundedFrameCannotLeakAcrossSongsSourcesOrAudioMode() {
        val cache = MusicVideoHandoff().apply { update(first.songId, true) }
        val bitmap = Bitmap.createBitmap(384, 216, Bitmap.Config.ARGB_8888)
        cache.save(first, bitmap)
        assertSame(bitmap, cache.frame(first))
        assertNull(cache.frame(second))
        cache.save(first, Bitmap.createBitmap(385, 216, Bitmap.Config.ARGB_8888))
        assertSame(bitmap, cache.frame(first))
        cache.update("other", true)
        assertNull(cache.snapshot)
        cache.save(first, bitmap)
        assertNull(cache.snapshot)
        val other = first.copy(songId = "other")
        cache.save(other, bitmap)
        cache.update("other", false)
        assertNull(cache.snapshot)
        cache.save(other, bitmap)
        assertNull(cache.snapshot)
    }
}
