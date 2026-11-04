package com.metrolist.music.ui.player

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.metrolist.music.playback.MusicVideoMode
import com.metrolist.music.playback.MusicVideoState
import com.metrolist.music.playback.isMusicVideoItem

internal data class MusicVideoFrameKey(val songId: String, val source: String)

/** A first-frame event is only useful for the source and surface that requested it. */
internal class MusicVideoRevealGate {
    private var generation = 0L
    private var key: MusicVideoFrameKey? = null
    var decoded = false
        private set

    fun attach(source: MusicVideoFrameKey): Long {
        key = source
        decoded = false
        return ++generation
    }

    fun detach() {
        generation++
        key = null
        decoded = false
    }

    fun rendered(token: Long, current: MusicVideoFrameKey?, active: Boolean): Boolean {
        if (!active || token != generation || current == null || current != key) return false
        if (decoded) return false
        decoded = true
        return true
    }
}

internal data class MusicVideoSnapshot(val key: MusicVideoFrameKey, val bitmap: Bitmap)

/** One small decoded frame, shared only within this player presentation. */
internal class MusicVideoHandoff {
    private var songId: String? = null
    private var enabled = false
    private var owner: Any? = null
    private var releaseOwner: (() -> Unit)? = null
    var snapshot by mutableStateOf<MusicVideoSnapshot?>(null)
        private set

    fun update(songId: String?, enabled: Boolean) {
        if (this.songId != songId || !enabled) snapshot = null
        this.songId = songId
        this.enabled = enabled
    }

    fun frame(key: MusicVideoFrameKey?): Bitmap? = snapshot?.takeIf { enabled && it.key == key }?.bitmap

    fun save(key: MusicVideoFrameKey, bitmap: Bitmap) {
        if (enabled && key.songId == songId && maxOf(bitmap.width, bitmap.height) <= 384) {
            snapshot = MusicVideoSnapshot(key, bitmap)
        }
    }

    fun claim(owner: Any, release: () -> Unit) {
        if (this.owner !== owner) releaseOwner?.invoke()
        this.owner = owner
        releaseOwner = release
    }

    fun release(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            releaseOwner = null
        }
    }
}

/** Keeps the native texture drawable while its covering placeholder waits for a decoded frame. */
internal class MusicVideoSurfaceBinding(
    private val player: Player,
    private val frame: MusicVideoFrame,
    private val lifecycle: Lifecycle,
    private val pip: Boolean,
    private val ownsSurface: () -> Boolean,
    private val video: () -> MusicVideoState,
    private val handoff: MusicVideoHandoff,
) : AutoCloseable {
    private val gate = MusicVideoRevealGate()
    private var key: MusicVideoFrameKey? = null
    private var frameListener: Player.Listener? = null
    private var closed = false
    private val lifecycleObserver = LifecycleEventObserver { _, _ -> sync() }
    private val sourceListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED)) sync()
        }
        override fun onVideoSizeChanged(videoSize: VideoSize) { frame.updateSize(videoSize) }
    }

    init {
        player.addListener(sourceListener)
        lifecycle.addObserver(lifecycleObserver)
        sync()
    }

    private fun active(): Boolean = !closed && ownsSurface() && lifecycle.currentState.isAtLeast(
        if (pip) Lifecycle.State.STARTED else Lifecycle.State.RESUMED)

    private fun currentKey(): MusicVideoFrameKey? {
        val state = video()
        val item = player.currentMediaItem ?: return null
        if (state.mode == MusicVideoMode.AUDIO || state.currentSongId != item.mediaId || !item.isMusicVideoItem()) return null
        return MusicVideoFrameKey(item.mediaId, item.localConfiguration!!.uri.toString())
    }

    private fun capture() {
        val source = key ?: return
        if (gate.decoded && currentKey() == source) frame.snapshot()?.let { handoff.save(source, it) }
    }

    private fun detach() {
        capture()
        gate.detach()
        frameListener?.let(player::removeListener)
        frameListener = null
        if (key != null) player.clearVideoTextureView(frame.texture)
        key = null
        frame.cover(handoff.frame(currentKey()))
        handoff.release(this)
    }

    private fun sync() {
        val next = currentKey().takeIf { active() }
        if (next == key) return
        detach()
        if (next == null) return
        handoff.claim(this, ::detach)
        key = next
        val token = gate.attach(next)
        frame.cover(handoff.frame(next))
        frame.updateSize(player.videoSize)
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (gate.rendered(token, currentKey(), active())) {
                    frame.reveal()
                }
            }
        }
        frameListener = listener
        player.addListener(listener)
        player.setVideoTextureView(frame.texture)
    }

    override fun close() {
        closed = true
        lifecycle.removeObserver(lifecycleObserver)
        player.removeListener(sourceListener)
        detach()
    }
}
