package com.metrolist.music.ui.player

import com.metrolist.music.lyrics.rememberTranslatedSongTitle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.models.MediaMetadata

@Composable
internal fun CompactSongRow(song: MediaMetadata, liked: Boolean, playing: Boolean, onLike: () -> Unit, onPlay: () -> Unit, modifier: Modifier = Modifier, controlsEnabled: Boolean = true) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(song.thumbnailUrl, null, Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(rememberTranslatedSongTitle(song.title), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artists.joinToString { it.name }, color = Color.White.copy(alpha = .7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onLike, enabled = controlsEnabled) {
            Icon(painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border), stringResource(R.string.player_toggle_favorite), tint = if (liked) Color(0xFFFF4D5E) else Color.White)
        }
        IconButton(onClick = onPlay, enabled = controlsEnabled) {
            Icon(painterResource(if (playing) R.drawable.pause else R.drawable.play), stringResource(if (playing) R.string.pause else R.string.play), tint = Color.White)
        }
    }
}

@Composable
internal fun PlayerScrollHeader(song: MediaMetadata, fraction: Float, background: Color, liked: Boolean, playing: Boolean, progress: Float,
    onCollapse: () -> Unit, onLike: () -> Unit, onPlay: () -> Unit, more: @Composable () -> Unit, landscape: Boolean = false) {
    val amount = fraction.coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(background.copy(alpha = .65f + .35f * amount), background.copy(alpha = amount))))
        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        Row(Modifier.fillMaxWidth().height(if (landscape) 48.dp else 64.dp).clipToBounds().padding(horizontal = if (landscape) 8.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (amount < 1f) Row(Modifier.fillMaxWidth().graphicsLayer { alpha = 1f - amount; translationY = amount * size.height / 2 }, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCollapse, enabled = amount < .5f) { Icon(painterResource(R.drawable.expand_more), stringResource(R.string.cd_back), tint = Color.White) }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.sourcify_wordmark), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (amount > 0f) CompactSongRow(song, liked, playing, onLike, onPlay,
                    Modifier.graphicsLayer { alpha = amount; translationY = -(1f - amount) * size.height }, controlsEnabled = amount >= .5f)
            }
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { more() }
        }
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, color = Color.White, trackColor = Color.White.copy(alpha = .25f),
            modifier = Modifier.fillMaxWidth().height(2.dp).graphicsLayer { alpha = amount })
    }
}

@Composable
internal fun CanvasFullscreenControls(song: MediaMetadata, liked: Boolean, playing: Boolean, progress: Float,
    onExit: () -> Unit, onLike: () -> Unit, onPlay: () -> Unit) {
    Column(Modifier.fillMaxSize().clickable(onClickLabel = stringResource(R.string.sourcify_exit_fullscreen), onClick = onExit).systemBarsPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) { Icon(painterResource(R.drawable.fullscreen_exit), stringResource(R.string.sourcify_exit_fullscreen), tint = Color.White) }
            Text(stringResource(R.string.sourcify_wordmark), color = Color.White, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.width(48.dp))
        }
        Spacer(Modifier.weight(1f))
        CompactSongRow(song, liked, playing, onLike, onPlay)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, color = Color.White, trackColor = Color.White.copy(alpha = .3f), modifier = Modifier.fillMaxWidth().height(2.dp))
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
internal fun PlayerTransportActions(newDesign: Boolean, onOpenQueue: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        content()
        Row(Modifier.fillMaxWidth().padding(horizontal = com.metrolist.music.constants.PlayerHorizontalPadding),
            horizontalArrangement = if (newDesign) Arrangement.End else Arrangement.Start) {
            IconButton(onClick = onOpenQueue, modifier = Modifier.size(40.dp)) {
                Icon(painterResource(R.drawable.queue_music), stringResource(R.string.queue), tint = Color.White)
            }
        }
    }
}
