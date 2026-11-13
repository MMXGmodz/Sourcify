package com.metrolist.music.ui.player

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.lyrics.LyricsUtils
import com.metrolist.music.lyrics.lyricsTextLooksSynced
import com.metrolist.music.models.MediaMetadata

internal fun coverLyricsText(raw: String?, position: Long, offset: Long): String? {
    if (!lyricsTextLooksSynced(raw)) return null
    val entries = LyricsUtils.parseLyrics(raw!!)
    return entries.getOrNull(activeLyricsLine(entries.map { it.time }, position + offset))?.text?.takeIf(String::isNotBlank)
}

@Composable
internal fun CoverLyricsFrame(enabled: Boolean, modifier: Modifier = Modifier, lyrics: @Composable () -> Unit, artwork: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { artwork() }
        AnimatedVisibility(enabled, enter = expandVertically(tween(280)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(280)) + fadeOut(tween(160))) {
            Box(Modifier.fillMaxWidth().height(80.dp).padding(start = 24.dp, end = 24.dp, top = 12.dp), contentAlignment = Alignment.TopStart) { lyrics() }
        }
    }
}

@Composable
internal fun CoverLyricsDisplay(song: MediaMetadata, position: Long, textColor: Color = Color.White) {
    if (!rememberSongLyricsVisible(song.id)) return
    val connection = LocalPlayerConnection.current ?: return
    val lyrics by connection.currentLyrics.collectAsStateWithLifecycle()
    val currentSong by connection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val raw = lyrics?.takeIf { it.id == song.id }?.lyrics
    val offset = currentSong?.takeIf { it.song.id == song.id }?.song?.lyricsOffset ?: 0
    val text = remember(raw, position, offset) { coverLyricsText(raw, position, offset.toLong()) }
        ?: if (raw.isNullOrBlank() || raw == com.metrolist.music.db.entities.LyricsEntity.LYRICS_NOT_FOUND) stringResource(R.string.lyrics_not_found)
        else if (!lyricsTextLooksSynced(raw)) stringResource(R.string.lyrics_unsynced) else ""
    CoverLyricsText(text, textColor)
}

@Composable
internal fun CoverLyricsText(text: String, color: Color = Color.White) {
    AnimatedContent(text, transitionSpec = {
        (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 4 }) togetherWith
            (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 4 })
    }, label = "coverLyricsLine") { line ->
        Text(line, color = color, fontFamily = PlayerLyricsFont, fontWeight = FontWeight.Bold,
            fontSize = 16.sp, lineHeight = 22.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
