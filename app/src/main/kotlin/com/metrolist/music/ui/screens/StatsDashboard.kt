package com.metrolist.music.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.ui.theme.LocalSongColor

@Composable
internal fun StatsDashboard(minutes: Long, artist: String?, artistImage: String?, song: String?, songImage: String?,
    covers: List<String>, onArtist: () -> Unit, onSong: () -> Unit, onHistory: () -> Unit) {
    val accent by animateColorAsState(LocalSongColor.current, tween(350), label = "statsArtworkColor")
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.ui_stats_period), style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant)
        Surface(onClick = onHistory, shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainer,
            border = BorderStroke(1.dp, accent.copy(alpha = .28f))) {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(
                accent.copy(alpha = .22f).compositeOver(scheme.surfaceContainer), scheme.surfaceContainer,
            ))).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(painterResource(R.drawable.graphic_eq), null, tint = scheme.onSurface, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.ui_minutes), style = MaterialTheme.typography.titleMedium)
                }
                AnimatedContent(minutes, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                    label = "statsMinutes") { value ->
                    Text(value.toString(), Modifier.fillMaxWidth(), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black,
                        autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 56.sp), maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.history), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Icon(painterResource(R.drawable.arrow_forward), null, modifier = Modifier.size(20.dp))
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatsHighlight(stringResource(R.string.ui_top_artist), artist, artistImage, true, true, onArtist)
                    StatsHighlight(stringResource(R.string.ui_top_song), song, songImage, false, true, onSong)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { StatsHighlight(stringResource(R.string.ui_top_artist), artist, artistImage, true, false, onArtist) }
                    Box(Modifier.weight(1f)) { StatsHighlight(stringResource(R.string.ui_top_song), song, songImage, false, false, onSong) }
                }
            }
        }
        Surface(onClick = onSong, enabled = song != null, shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.ui_top_songs), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(if (song == null) stringResource(R.string.ui_stats_empty) else stringResource(R.string.ui_stats_personal),
                            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                    Icon(painterResource(R.drawable.play), null, modifier = Modifier.size(24.dp))
                }
                if (covers.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    covers.take(3).forEach { cover ->
                        AsyncImage(cover, null, Modifier.weight(1f).aspectRatio(1.4f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsHighlight(title: String, value: String?, image: String?, artist: Boolean, horizontal: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val artwork: @Composable () -> Unit = {
        val shape = if (artist) CircleShape else RoundedCornerShape(16.dp)
        Box((if (horizontal) Modifier.size(80.dp) else Modifier.fillMaxWidth().aspectRatio(1f))
            .clip(shape).background(scheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(painterResource(if (artist) R.drawable.person else R.drawable.music_note), null,
                tint = scheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
            if (image != null) AsyncImage(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
    val label: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            Text(value ?: stringResource(R.string.ui_stats_empty), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
    Surface(onClick = onClick, enabled = value != null, shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainer) {
        if (horizontal) Column(Modifier.fillMaxWidth().animateContentSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                artwork()
                Text(value ?: stringResource(R.string.ui_stats_empty), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        } else Column(Modifier.fillMaxWidth().animateContentSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            artwork()
            label()
        }
    }
}
