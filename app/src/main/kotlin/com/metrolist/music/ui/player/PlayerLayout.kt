package com.metrolist.music.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.constants.PlayButtonStyle
import com.metrolist.music.ui.component.PlayerPlayButton
import com.metrolist.music.ui.utils.resize
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.*
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun KeepCanvasPlayerPortrait(enabled: Boolean) {
    val activity = LocalContext.current.videoActivity() ?: return
    if (enabled) DisposableEffect(activity) {
        val original = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity.requestedOrientation = original }
    }
}

@Composable
internal fun PlayerLandscapeLayout(artwork: @Composable () -> Unit, controls: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        artwork()
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 66.dp, bottom = 19.dp)) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, content = controls)
        }
    }
}

@Composable
internal fun LandscapeArtworkBackdrop(image: Any?, color: Color, fallback: Any? = null) {
    var failed by remember(image) { mutableStateOf(false) }
    val selected = if (failed) fallback else image
    val model = remember(selected) { if (selected is String) selected.resize(width = 1600) else selected }
    Box(Modifier.fillMaxSize().background(color)) {
        AsyncImage(model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd, onError = { failed = true })
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            listOf(Color.Black.copy(alpha = .85f), Color.Black.copy(alpha = .25f)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = .45f)))))
    }
}

@Composable
internal fun LandscapeTrackRow(cover: Any?, title: String, liked: Boolean, onLike: () -> Unit,
    onTitleClick: () -> Unit, onTitleLongClick: () -> Unit, showCover: Boolean = true, artists: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showCover) {
            AsyncImage(cover, null, Modifier.size(48.dp).clip(RoundedCornerShape(2.dp)),
                contentScale = ContentScale.Crop)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White,
                fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp).combinedClickable(onClick = onTitleClick, onLongClick = onTitleLongClick))
            artists()
        }
        Spacer(Modifier.width(12.dp))
        IconButton(onClick = onLike, modifier = Modifier.size(24.dp)) {
            Icon(painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border),
                stringResource(R.string.player_toggle_favorite),
                tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun LandscapeTransport(playIcon: Int, playDescription: String, playStyle: PlayButtonStyle,
    accent: Color, shuffle: Boolean, repeat: Int, previousEnabled: Boolean, nextEnabled: Boolean,
    guest: Boolean, onShuffle: () -> Unit, onPrevious: () -> Unit, onPlay: () -> Unit, onNext: () -> Unit, onRepeat: () -> Unit, playModifier: Modifier = Modifier) {
    Row(Modifier.widthIn(max = 320.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        @Composable fun action(icon: Int, description: Int, enabled: Boolean, selected: Boolean = false, onClick: () -> Unit) {
            IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(icon), stringResource(description),
                    tint = if (selected) accent else Color.White.copy(alpha = if (enabled) 1f else .4f), modifier = Modifier.size(22.dp))
            }
        }
        action(R.drawable.landscape_shuffle, R.string.player_shuffle, !guest, shuffle, onShuffle)
        action(R.drawable.landscape_previous, R.string.previous, previousEnabled && !guest, onClick = onPrevious)
        PlayerPlayButton(playModifier.size(56.dp).padding(4.dp), onPlay, iconRes = when (playIcon) { R.drawable.play -> R.drawable.landscape_play; R.drawable.pause -> R.drawable.landscape_pause; else -> playIcon },
            contentDescription = playDescription, style = playStyle, accentColor = accent)
        action(R.drawable.landscape_next, R.string.next, nextEnabled && !guest, onClick = onNext)
        action(if (repeat == Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.landscape_repeat,
            when (repeat) { Player.REPEAT_MODE_ONE -> R.string.repeat_mode_one; Player.REPEAT_MODE_ALL -> R.string.repeat_mode_all; else -> R.string.repeat_mode_off }, !guest, repeat != Player.REPEAT_MODE_OFF, onRepeat)
    }
}
