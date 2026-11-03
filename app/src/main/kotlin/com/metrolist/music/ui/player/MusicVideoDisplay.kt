package com.metrolist.music.ui.player

import android.content.Context
import android.view.Gravity
import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.metrolist.music.R
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
    var crop = false
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
        val videoWidth = size.width * size.pixelWidthHeightRatio
        val videoHeight = size.height.toFloat()
        val (w, h) = if (crop && videoWidth > 0 && videoHeight > 0) {
            val scale = maxOf(width / videoWidth, height / videoHeight)
            (videoWidth * scale).toInt() to (videoHeight * scale).toInt()
        } else musicVideoFrameSize(width, height, videoWidth, videoHeight)
        if (w > 0 && h > 0 && (texture.layoutParams.width != w || texture.layoutParams.height != h)) {
            texture.layoutParams = LayoutParams(w, h, Gravity.CENTER)
        }
    }
}

/** A surface for the service's primary player; sound and picture share all playback controls. */
@Composable
internal fun MusicVideoDisplay(service: MusicService, modifier: Modifier = Modifier,
    role: MusicVideoDisplayRole = MusicVideoDisplayRole.INLINE,
    onSettingsClick: (() -> Unit)? = null, crop: Boolean = false,
) {
    val presentation = LocalMusicVideoPresentation.current
    if (presentation != null && !presentation.owns(role)) return
    val pip = presentation?.inPip == true
    val videoControlsLabel = stringResource(R.string.s86_video_controls)
    var controls by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    val player by service.playerFlow.collectAsStateWithLifecycle()
    val activePlayer = player ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var frame by remember { mutableStateOf<MusicVideoFrame?>(null) }
    DisposableEffect(activePlayer, frame, lifecycle, pip) {
        val bound = frame
        fun attachSurface() {
            if (bound != null && lifecycle.currentState.isAtLeast(if (pip) Lifecycle.State.STARTED else Lifecycle.State.RESUMED)) {
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
    Box(modifier.clipToBounds().background(Color.Black).clickable(enabled = !pip, role = Role.Button, onClickLabel = videoControlsLabel) { controls = !controls }
        .semantics { if (!pip) contentDescription = videoControlsLabel }) {
        AndroidView(factory = { MusicVideoFrame(it).also { created -> frame = created } },
            update = { it.crop = crop; it.updateSize(activePlayer.videoSize) }, modifier = Modifier.fillMaxSize())
        if (controls && !pip) {
            IconButton(onClick = { onSettingsClick?.invoke() ?: run { settings = true } }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = .5f), androidx.compose.foundation.shape.CircleShape)) {
                Icon(painterResource(R.drawable.settings), stringResource(R.string.s86_video_settings), tint = Color.White)
            }
            Row(Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(alpha = .5f), androidx.compose.foundation.shape.RoundedCornerShape(24.dp))) {
                IconButton(onClick = { presentation?.fullscreen = MusicVideoFullscreen.PORTRAIT }) {
                    Icon(painterResource(R.drawable.s86_portrait), stringResource(R.string.s86_fullscreen_portrait), tint = Color.White)
                }
                IconButton(onClick = { presentation?.fullscreen = MusicVideoFullscreen.LANDSCAPE }) {
                    Icon(painterResource(R.drawable.s86_landscape), stringResource(R.string.s86_fullscreen_landscape), tint = Color.White)
                }
            }
        }
    }
    if (settings) MusicVideoSettingsSheet(service) { settings = false }
}
