package com.metrolist.music.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.playback.MusicService
import com.metrolist.music.playback.MusicVideoMode

internal enum class MusicVideoDisplayRole { INLINE, BROWSE, OVERLAY }
internal enum class MusicVideoFullscreen { PORTRAIT, LANDSCAPE }

internal class MusicVideoPresentationState {
    var fullscreen by mutableStateOf<MusicVideoFullscreen?>(null)
    var inPip by mutableStateOf(false)
    var browsing by mutableStateOf(false)
    var browseHeight by mutableStateOf(0.dp)
    fun owns(role: MusicVideoDisplayRole): Boolean = when {
        inPip || fullscreen != null -> role == MusicVideoDisplayRole.OVERLAY
        browsing -> role == MusicVideoDisplayRole.BROWSE
        else -> role == MusicVideoDisplayRole.INLINE
    }
}

internal val LocalMusicVideoPresentation = staticCompositionLocalOf<MusicVideoPresentationState?> { null }

internal fun musicVideoBrowseHeight(width: Dp): Dp = (width * 9f / 16f).coerceAtMost(200.dp)

internal fun musicVideoPipEligible(enabled: Boolean, supported: Boolean, video: Boolean, playing: Boolean): Boolean =
    enabled && supported && video && playing

internal fun musicVideoPipRatio(aspectRatio: Float): Rational {
    val ratio = if (aspectRatio.isFinite() && aspectRatio > 0) aspectRatio.coerceIn(1f / 2.39f, 2.39f) else 16f / 9f
    return Rational((ratio * 10_000).toInt(), 10_000)
}

internal fun Activity.startMusicVideoPip(service: MusicService?, presentation: MusicVideoPresentationState, enabled: Boolean): Boolean {
    if (service?.isPlayerReady?.value != true) return false
    if (!musicVideoPipEligible(enabled, !com.metrolist.music.BuildConfig.TV_INTERFACE &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE),
            service.musicVideoState.value.hasVideo, service.player.isPlaying)) return false
    if (isInPictureInPictureMode) return true
    presentation.inPip = true
    return try {
        val params = PictureInPictureParams.Builder().setAspectRatio(musicVideoPipRatio(service.musicVideoState.value.aspectRatio))
        if (Build.VERSION.SDK_INT >= 31) params.setSeamlessResizeEnabled(true)
        enterPictureInPictureMode(params.build()).also { if (!it) presentation.inPip = false }
    } catch (_: IllegalStateException) {
        presentation.inPip = false
        false
    } catch (_: SecurityException) {
        presentation.inPip = false
        false
    } catch (_: IllegalArgumentException) {
        presentation.inPip = false
        false
    }
}

internal fun Context.videoActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.videoActivity()
    else -> null
}

@Composable
internal fun MusicVideoOverlayHost(service: MusicService?, state: MusicVideoPresentationState) {
    val video by (service?.musicVideoState ?: return).collectAsStateWithLifecycle()
    val activity = LocalContext.current.videoActivity() ?: return
    LaunchedEffect(video.mode, state.inPip) {
        if (video.mode == MusicVideoMode.AUDIO) {
            state.fullscreen = null
            if (state.inPip) {
                // Close the video-only window when the queue returns to audio; the service continues.
                state.inPip = false
                activity.finish()
            }
        }
    }
    if (!state.inPip && state.fullscreen == null) return
    val mode = state.fullscreen
    DisposableEffect(activity, mode, state.inPip) {
        val window = if (mode != null && !state.inPip) MusicVideoFullscreenWindow(activity, mode) else null
        onDispose { window?.close() }
    }
    BackHandler(enabled = mode != null && !state.inPip) { state.fullscreen = null }
    var settings by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        MusicVideoDisplay(service, Modifier.fillMaxSize(), MusicVideoDisplayRole.OVERLAY,
            onSettingsClick = { settings = true }, crop = !state.inPip)
        if (!state.inPip) IconButton(onClick = { state.fullscreen = null },
            modifier = Modifier.align(Alignment.TopStart).systemBarsPadding().padding(8.dp)
                .background(Color.Black.copy(alpha = .5f), androidx.compose.foundation.shape.CircleShape)) {
            Icon(painterResource(R.drawable.close), stringResource(R.string.s86_close_fullscreen), tint = Color.White)
        }
    }
    if (settings && !state.inPip) MusicVideoSettingsSheet(service) { settings = false }
}

internal class MusicVideoFullscreenWindow(private val activity: Activity, mode: MusicVideoFullscreen) : AutoCloseable {
    private val originalOrientation = activity.requestedOrientation
    private val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    private val originalBehavior = controller.systemBarsBehavior
    private val originalLightStatus = controller.isAppearanceLightStatusBars
    private val originalLightNavigation = controller.isAppearanceLightNavigationBars
    private val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
    private val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
    private val navVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
    init {
        activity.requestedOrientation = if (mode == MusicVideoFullscreen.LANDSCAPE)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
    override fun close() {
        activity.requestedOrientation = originalOrientation
        controller.systemBarsBehavior = originalBehavior
        controller.isAppearanceLightStatusBars = originalLightStatus
        controller.isAppearanceLightNavigationBars = originalLightNavigation
        if (statusVisible) controller.show(WindowInsetsCompat.Type.statusBars()) else controller.hide(WindowInsetsCompat.Type.statusBars())
        if (navVisible) controller.show(WindowInsetsCompat.Type.navigationBars()) else controller.hide(WindowInsetsCompat.Type.navigationBars())
    }
}
