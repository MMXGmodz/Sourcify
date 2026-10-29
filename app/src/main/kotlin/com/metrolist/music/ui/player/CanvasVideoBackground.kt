package com.metrolist.music.ui.player

import android.content.Context
import android.view.Gravity
import android.view.TextureView
import android.widget.FrameLayout
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
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.metrolist.music.utils.CanvasVideoCache

internal fun canvasDimAlpha(brightness: Float): Float =
    1f - (if (brightness.isFinite()) brightness else 0.65f).coerceIn(0.15f, 1f)

internal fun canvasFrameSize(viewWidth: Int, viewHeight: Int, videoWidth: Float, videoHeight: Float, zoom: Float = 1.35f): Pair<Int, Int> {
    if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0 || !videoWidth.isFinite() || !videoHeight.isFinite()) return viewWidth to viewHeight
    val scale = max(viewWidth / videoWidth, viewHeight / videoHeight) * (if (zoom.isFinite()) zoom else 1.35f).coerceIn(1f, 2f)
    return ceil(videoWidth * scale).toInt() to ceil(videoHeight * scale).toInt()
}

internal fun stableCanvasVideoSize(previous: VideoSize, incoming: VideoSize): VideoSize =
    if (incoming.width > 0 && incoming.height > 0 && incoming.pixelWidthHeightRatio.isFinite() && incoming.pixelWidthHeightRatio > 0) incoming else previous

private class CanvasVideoFrame(context: Context) : FrameLayout(context) {
    val texture = TextureView(context).apply { isOpaque = false }
    private var videoSize = VideoSize.UNKNOWN
    private var zoom = 1.35f
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
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val zoom by rememberPreference(CanvasZoomKey, 1.35f)
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
    Box(modifier) {
        AndroidView(
            factory = { CanvasVideoFrame(it) },
            update = { frame = it; it.setVideoSize(videoSize, zoom) },
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = canvasDimAlpha(brightness))))
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.2f),
                0.45f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.78f),
            ),
        ))
    }
}
