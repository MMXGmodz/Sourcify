package com.metrolist.music.ui.player

import android.content.Context
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.view.Gravity
import android.view.TextureView
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.VideoSize
import com.metrolist.music.playback.MusicService
import com.metrolist.music.playback.MusicVideoMode
import com.metrolist.music.playback.isMusicVideoItem
import com.metrolist.music.ui.theme.LocalSongColor

internal fun musicVideoFrameSize(width: Int, height: Int, videoWidth: Float, videoHeight: Float): Pair<Int, Int> {
    if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0 || !videoWidth.isFinite() || !videoHeight.isFinite()) return width to height
    val scale = minOf(width / videoWidth, height / videoHeight)
    return (videoWidth * scale).toInt().coerceAtLeast(1) to (videoHeight * scale).toInt().coerceAtLeast(1)
}

internal class MusicVideoFrame(context: Context) : FrameLayout(context) {
    val texture = TextureView(context)
    internal val cover = FrameLayout(context)
    private val coverImage = ImageView(context)
    private val loading = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
        indeterminateTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
        contentDescription = context.getString(R.string.sourcify_music_video_loading)
    }
    private var revealAnimation: ValueAnimator? = null
    var placeholderColor: Int = android.graphics.Color.DKGRAY
        set(value) { field = value; cover.setBackgroundColor(value) }
    var crop = false
        set(value) {
            if (field == value) return
            field = value
            coverImage.scaleType = if (value) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            requestLayout()
        }
    private var size = VideoSize.UNKNOWN
    init {
        addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
        addView(cover, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        coverImage.scaleType = ImageView.ScaleType.FIT_CENTER
        cover.addView(coverImage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val indicatorSize = (24 * resources.displayMetrics.density).toInt()
        cover.addView(loading, LayoutParams(indicatorSize, indicatorSize, Gravity.CENTER))
        cover(null)
    }
    fun cover(bitmap: Bitmap?) {
        revealAnimation?.cancel()
        revealAnimation = null
        cover.alpha = 1f
        cover.setBackgroundColor(placeholderColor)
        coverImage.setImageBitmap(bitmap)
        loading.visibility = if (bitmap == null) VISIBLE else INVISIBLE
    }
    fun reveal() {
        revealAnimation?.cancel()
        loading.visibility = INVISIBLE
        revealAnimation = ValueAnimator.ofFloat(cover.alpha, 0f).apply {
            duration = 260
            interpolator = PathInterpolator(.2f, 0f, 0f, 1f)
            addUpdateListener { cover.alpha = it.animatedValue as Float }
            start()
        }
    }
    fun snapshot(): Bitmap? {
        if (!texture.isAvailable || texture.width <= 0 || texture.height <= 0) return null
        val scale = minOf(1f, 384f / maxOf(texture.width, texture.height))
        return try {
            texture.getBitmap((texture.width * scale).toInt().coerceAtLeast(1),
                (texture.height * scale).toInt().coerceAtLeast(1))
        } catch (_: IllegalStateException) { null }
    }
    fun updateSize(incoming: VideoSize) {
        if (incoming.width <= 0 || incoming.height <= 0 || incoming.pixelWidthHeightRatio <= 0 ||
            !incoming.pixelWidthHeightRatio.isFinite() || !(incoming.width * incoming.pixelWidthHeightRatio).isFinite() ||
            incoming == size) return
        size = incoming
        requestLayout()
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = (measuredWidth - paddingLeft - paddingRight).coerceAtLeast(0)
        val height = (measuredHeight - paddingTop - paddingBottom).coerceAtLeast(0)
        val videoWidth = size.width * size.pixelWidthHeightRatio
        val videoHeight = size.height.toFloat()
        val (w, h) = if (crop && width > 0 && height > 0 && videoWidth > 0 && videoHeight > 0) {
            val scale = maxOf(width / videoWidth, height / videoHeight)
            (videoWidth * scale).toInt().coerceAtLeast(1) to (videoHeight * scale).toInt().coerceAtLeast(1)
        } else musicVideoFrameSize(width, height, videoWidth, videoHeight)
        // Changing layout params in onSizeChanged leaves the first measured texture stretched.
        texture.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }
}

/** A surface for the service's primary player; sound and picture share all playback controls. */
@Composable
internal fun MusicVideoDisplay(service: MusicService, modifier: Modifier = Modifier,
    role: MusicVideoDisplayRole = MusicVideoDisplayRole.INLINE,
    onSettingsClick: (() -> Unit)? = null, crop: Boolean = false,
    controlsEnabled: Boolean = true,
) {
    val presentation = LocalMusicVideoPresentation.current
    val handoff = presentation?.handoff ?: remember { MusicVideoHandoff() }
    val video by service.musicVideoState.collectAsStateWithLifecycle()
    SideEffect { handoff.update(video.currentSongId, video.mode != MusicVideoMode.AUDIO) }
    val owns = presentation == null || presentation.owns(role)
    val pip = presentation?.inPip == true
    val controlsAvailable = controlsEnabled && !pip && owns
    val videoControlsLabel = stringResource(R.string.s86_video_controls)
    val loadingLabel = stringResource(R.string.sourcify_music_video_loading)
    var controls by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    val player by service.playerFlow.collectAsStateWithLifecycle()
    val activePlayer = player ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var frame by remember(owns) { mutableStateOf<MusicVideoFrame?>(null) }
    val placeholder = lerp(LocalSongColor.current, Color.Black, .78f)
    DisposableEffect(activePlayer, frame, lifecycle, pip, owns, video.currentSongId, video.mode == MusicVideoMode.AUDIO) {
        val bound = frame
        val binding = if (owns && bound != null) MusicVideoSurfaceBinding(activePlayer, bound, lifecycle, pip,
            ownsSurface = { presentation == null || presentation.owns(role) },
            video = { service.musicVideoState.value }, handoff = handoff) else null
        onDispose { binding?.close() }
    }
    val controlsModifier = if (controlsAvailable) Modifier.clickable(role = Role.Button, onClickLabel = videoControlsLabel) { controls = !controls }
        .semantics { contentDescription = videoControlsLabel } else Modifier
    Box(modifier.clipToBounds().background(Color.Black).then(controlsModifier)) {
        if (owns) {
            AndroidView(factory = { MusicVideoFrame(it).also { created -> frame = created } },
                update = { it.crop = crop; it.placeholderColor = placeholder.toArgb(); it.updateSize(activePlayer.videoSize) },
                modifier = Modifier.fillMaxSize())
        } else {
            val source = activePlayer.currentMediaItem?.takeIf { it.isMusicVideoItem() && it.mediaId == video.currentSongId }
                ?.let { MusicVideoFrameKey(it.mediaId, it.localConfiguration!!.uri.toString()) }
            val snapshot = handoff.frame(source)
            Box(Modifier.fillMaxSize().background(placeholder), contentAlignment = Alignment.Center) {
                if (snapshot != null) Image(snapshot.asImageBitmap(), null, Modifier.fillMaxSize(),
                    contentScale = if (crop) ContentScale.Crop else ContentScale.Fit)
                else Icon(painterResource(R.drawable.slow_motion_video), loadingLabel,
                    modifier = Modifier.size(24.dp), tint = Color.White.copy(alpha = .7f))
            }
        }
        if (controls && controlsAvailable) {
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
    if (settings && controlsAvailable) MusicVideoSettingsSheet(service) { settings = false }
}
