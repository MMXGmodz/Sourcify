package com.metrolist.music.ui.player

import android.content.Context
import android.view.Gravity
import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.metrolist.music.utils.CanvasVideoSource
import com.metrolist.music.constants.CanvasZoomKey
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.metrolist.music.utils.CanvasVideoCache

internal fun stableCanvasVideoSize(previous: VideoSize, incoming: VideoSize): VideoSize =
    if (incoming.width > 0 && incoming.height > 0 && incoming.pixelWidthHeightRatio.isFinite() && incoming.pixelWidthHeightRatio > 0) incoming else previous

private class CanvasVideoFrame(context: Context) : FrameLayout(context) {
    val texture = TextureView(context).apply { isOpaque = false }
    private var videoSize = VideoSize.UNKNOWN
    private var zoom = DefaultCanvasZoom
    init {
        clipChildren = true
        addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
    }
    fun setVideoSize(size: VideoSize, zoom: Float) {
        videoSize = stableCanvasVideoSize(videoSize, size)
        this.zoom = zoom
        resizeVideo()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resizeVideo()
    }
    private fun resizeVideo() {
        val (w, h) = canvasFrameSize(width, height, videoSize.width * videoSize.pixelWidthHeightRatio, videoSize.height.toFloat(), zoom)
        if (w > 0 && h > 0 && (texture.layoutParams.width != w || texture.layoutParams.height != h)) {
            texture.layoutParams = LayoutParams(w, h, Gravity.CENTER)
        }
    }
}

@Composable
fun CanvasVideoBackground(
    source: CanvasVideoSource,
    isPlaying: Boolean,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val zoom by rememberPreference(CanvasZoomKey, DefaultCanvasZoom)
    val brightness by rememberPreference(com.metrolist.music.constants.CanvasBrightnessKey, 0.65f)
    val currentZoom by rememberUpdatedState(zoom)
    val playing by rememberUpdatedState(isPlaying)
    val handleError by rememberUpdatedState(onError)
    var frame by remember { mutableStateOf<CanvasVideoFrame?>(null) }
    var videoSize by remember(source) { mutableStateOf(VideoSize.UNKNOWN) }
    val dataSource by produceState<Pair<CanvasVideoSource, androidx.media3.datasource.DataSource.Factory>?>(null, context, source) {
        value = null
        value = source to withContext(Dispatchers.IO) { CanvasVideoCache.dataSource(context, source) }
    }
    val sourceFactory = dataSource?.takeIf { it.first == source }?.second ?: return
    val player = remember(context, source, sourceFactory) {
        ExoPlayer.Builder(context)
            .setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(8_000, 16_000, 250, 750).build())
            .setMediaSourceFactory(DefaultMediaSourceFactory(sourceFactory))
            .build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, false)
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }
    DisposableEffect(player, frame) {
        val boundFrame = frame
        boundFrame?.let { player.setVideoTextureView(it.texture) }
        onDispose { boundFrame?.let { player.clearVideoTextureView(it.texture) } }
    }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = stableCanvasVideoSize(videoSize, size)
                frame?.setVideoSize(videoSize, currentZoom)
            }
            override fun onPlayerError(error: PlaybackException) { handleError() }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && !player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)) {
                    handleError()
                }
            }
        }
        val observer = LifecycleEventObserver { _, _ ->
            player.playWhenReady = playing && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        player.addListener(listener)
        lifecycle.addObserver(observer)
        player.setMediaItem(MediaItem.Builder()
            .setUri(source.uri)
            .setMimeType(source.stream?.videoMimeType)
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(source.clipStartMs)
                .setEndPositionMs(source.clipEndMs)
                .build())
            .build())
        player.prepare()
        player.playWhenReady = playing && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        onDispose {
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            frame?.texture?.let(player::clearVideoTextureView)
            player.release()
        }
    }
    LaunchedEffect(player, isPlaying, lifecycle) {
        player.playWhenReady = isPlaying && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }
    Box(modifier.clipToBounds().background(Color.Black)) {
        AndroidView(
            factory = { CanvasVideoFrame(it) },
            update = { frame = it; it.setVideoSize(videoSize, zoom) },
            modifier = Modifier.fillMaxSize(),
        )
        CanvasLightingOverlay(brightness, fullscreen, videoScrim = true)
    }
}

@Composable
internal fun CanvasImageBackground(uri: android.net.Uri, onError: () -> Unit, fullscreen: Boolean = false) {
    val zoom by rememberPreference(CanvasZoomKey, DefaultCanvasZoom)
    val brightness by rememberPreference(com.metrolist.music.constants.CanvasBrightnessKey, 0.65f)
    Box(Modifier.fillMaxSize().clipToBounds().background(Color.Black)) {
        coil3.compose.AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = remember(zoom) { CanvasContentScale(zoom) },
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
            onError = { onError() },
            modifier = Modifier.fillMaxSize(),
        )
        CanvasLightingOverlay(brightness, fullscreen, videoScrim = false)
    }
}

@Composable
internal fun CanvasLightingOverlay(brightness: Float, fullscreen: Boolean, videoScrim: Boolean) {
    val dim by animateFloatAsState(canvasDimAlpha(brightness, fullscreen), tween(250), label = "canvasFullscreenBrightness")
    val scrimAlpha by animateFloatAsState(if (fullscreen) 0f else 1f, tween(250), label = "canvasVideoScrim")
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
    if (videoScrim) Box(Modifier.fillMaxSize().graphicsLayer { alpha = scrimAlpha }.background(
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = .2f),
            .45f to Color.Transparent,
            1f to Color.Black.copy(alpha = .78f),
        ),
    ))
}
