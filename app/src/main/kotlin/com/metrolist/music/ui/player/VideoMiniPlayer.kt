package com.metrolist.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.MiniPlayerHeight
import com.metrolist.music.utils.joinToArtistString

internal fun videoMiniPlayerHeight(fontScale: Float): Dp =
    104.dp + 40.dp * ((if (fontScale.isFinite()) fontScale else 1f).coerceAtLeast(1f) - 1f)

internal fun videoMiniPlayerExtraHeight(largeVideo: Boolean, hasVideo: Boolean, fontScale: Float): Dp =
    if (largeVideo && hasVideo) videoMiniPlayerHeight(fontScale) - MiniPlayerHeight else 0.dp

@Composable
internal fun VideoMiniPlayer(progress: ProgressState, modifier: Modifier, onExpand: () -> Unit) {
    val connection = LocalPlayerConnection.current ?: return
    val song by connection.mediaMetadata.collectAsStateWithLifecycle()
    val metadata = song ?: return
    val current by connection.currentSong.collectAsStateWithLifecycle()
    val playing by connection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    VideoMiniPlayerCard(metadata.title, metadata.artists.joinToArtistString(" " + stringResource(R.string.and) + " ") { it.name }, playing,
        current?.takeIf { it.song.id == metadata.id }?.song?.liked == true,
        modifier = modifier.drawWithContent {
            drawContent()
            val y = size.height - 2.dp.toPx()
            drawLine(Color.White.copy(alpha = .25f), androidx.compose.ui.geometry.Offset(12.dp.toPx(), y),
                androidx.compose.ui.geometry.Offset(size.width - 12.dp.toPx(), y), 2.dp.toPx())
            drawLine(Color.White, androidx.compose.ui.geometry.Offset(12.dp.toPx(), y),
                androidx.compose.ui.geometry.Offset(12.dp.toPx() + (size.width - 24.dp.toPx()) * progress.progress, y), 2.dp.toPx())
        }, onExpand = onExpand, onPlayPause = connection::togglePlayPause, onLike = connection::toggleLike,
        video = {
            MusicVideoDisplay(connection.service, Modifier.fillMaxSize(), MusicVideoDisplayRole.BROWSE, controlsEnabled = false)
        })
}

@Composable
internal fun VideoMiniPlayerCard(
    title: String, artist: String, playing: Boolean, liked: Boolean,
    modifier: Modifier = Modifier, onExpand: () -> Unit, onPlayPause: () -> Unit, onLike: () -> Unit,
    video: @Composable () -> Unit,
) {
    val height = videoMiniPlayerHeight(LocalDensity.current.fontScale)
    BoxWithConstraints(modifier.fillMaxWidth().height(height).windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)).padding(horizontal = 12.dp)
        .clip(RoundedCornerShape(20.dp)).background(Color(0xFF141719))
        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.s87_open_player), onClick = onExpand)) {
        val previewWidth = (maxWidth * .36f).coerceIn(88.dp, 156.dp)
        Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(previewWidth).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Color.Black),
                contentAlignment = Alignment.Center) { video() }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onLike, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border),
                            stringResource(R.string.s87_toggle_favorite), tint = if (liked) Color(0xFF8ED8FF) else Color.White)
                    }
                    IconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(if (playing) R.drawable.pause else R.drawable.play),
                            stringResource(if (playing) R.string.player_pause else R.string.play), tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }
            }
        }
    }
}
