package com.metrolist.music.ui.player

import android.content.Context
import android.view.Gravity
import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.metrolist.music.playback.MusicService

internal fun musicVideoFrameSize(width: Int, height: Int, videoWidth: Float, videoHeight: Float): Pair<Int, Int> {
    if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0 || !videoWidth.isFinite() || !videoHeight.isFinite()) return width to height
    val scale = minOf(width / videoWidth, height / videoHeight)
    return (videoWidth * scale).toInt().coerceAtLeast(1) to (videoHeight * scale).toInt().coerceAtLeast(1)
}

private class MusicVideoFrame(context: Context) : FrameLayout(context) {
    val texture = TextureView(context)
    private var size = VideoSize.UNKNOWN
    init { addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER)) }
    fun updateSize(incoming: VideoSize) {
        if (incoming.width > 0 && incoming.height > 0) size = incoming
        resizeVideo()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resizeVideo()
    }
    private fun resizeVideo() {
        val (w, h) = musicVideoFrameSize(width, height, size.width * size.pixelWidthHeightRatio, size.height.toFloat())
        if (w > 0 && h > 0 && (texture.layoutParams.width != w || texture.layoutParams.height != h)) {
            texture.layoutParams = LayoutParams(w, h, Gravity.CENTER)
        }
    }
}

/** A surface for the service's primary player; sound and picture share all playback controls. */
@Composable
fun MusicVideoDisplay(service: MusicService, modifier: Modifier = Modifier) {
    val player by service.playerFlow.collectAsStateWithLifecycle()
    val activePlayer = player ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var frame by remember { mutableStateOf<MusicVideoFrame?>(null) }
    DisposableEffect(activePlayer, frame, lifecycle) {
        val bound = frame
        fun attachSurface() {
            if (bound != null && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                bound.updateSize(activePlayer.videoSize)
                activePlayer.setVideoTextureView(bound.texture)
            } else bound?.let { activePlayer.clearVideoTextureView(it.texture) }
        }
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) { bound?.updateSize(videoSize) }
        }
        val observer = LifecycleEventObserver { _, _ -> attachSurface() }
        activePlayer.addListener(listener)
        lifecycle.addObserver(observer)
        attachSurface()
        onDispose {
            lifecycle.removeObserver(observer)
            activePlayer.removeListener(listener)
            bound?.let { activePlayer.clearVideoTextureView(it.texture) }
        }
    }
    Box(modifier.clipToBounds().background(Color.Black)) {
        AndroidView(factory = { MusicVideoFrame(it).also { created -> frame = created } },
            update = { it.updateSize(activePlayer.videoSize) }, modifier = Modifier.fillMaxSize())
    }
}
