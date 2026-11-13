package com.metrolist.music.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.ui.component.PlayerPlayButton

@Composable
internal fun LyricsFullscreenHeader(cover: Any?, title: String, artists: String, time: String,
    onShare: () -> Unit, onExit: () -> Unit, onOptions: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(cover, null, Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artists, style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(time, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .8f), maxLines = 1)
        }
        IconButton(onClick = onShare) { Icon(painterResource(R.drawable.share), stringResource(R.string.s6_share_lyrics), tint = Color.White) }
        IconButton(onClick = onExit) { Icon(painterResource(R.drawable.fullscreen_exit), stringResource(R.string.sourcify_exit_fullscreen), tint = Color.White) }
        IconButton(onClick = onOptions) {
            Icon(painterResource(R.drawable.more_vert), stringResource(R.string.s16_lyrics_options), tint = Color.White)
        }
    }
}

@Composable
internal fun LyricsPlaybackControls(playing: Boolean, guest: Boolean, previousEnabled: Boolean, nextEnabled: Boolean,
    onPrevious: () -> Unit, onPlay: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, enabled = previousEnabled && !guest) {
            Icon(painterResource(R.drawable.landscape_previous), stringResource(R.string.previous), tint = Color.White.copy(alpha = if (previousEnabled && !guest) 1f else .4f))
        }
        Spacer(Modifier.width(20.dp))
        PlayerPlayButton(Modifier.size(56.dp), onPlay, enabled = !guest,
            iconRes = if (playing) R.drawable.landscape_pause else R.drawable.landscape_play,
            contentDescription = stringResource(if (playing) R.string.pause else R.string.play))
        Spacer(Modifier.width(20.dp))
        IconButton(onClick = onNext, enabled = nextEnabled && !guest) {
            Icon(painterResource(R.drawable.landscape_next), stringResource(R.string.next), tint = Color.White.copy(alpha = if (nextEnabled && !guest) 1f else .4f))
        }
    }
}
