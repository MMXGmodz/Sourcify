package com.metrolist.music.ui.player

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.lyrics.LyricsUtils
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.theme.LocalSongColor
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun activeLyricsLine(times: List<Long>, position: Long): Int = times.indexOfLast { it <= position }

internal fun lyricsPreviewLines(raw: String?, position: Long): List<String> {
    if (raw.isNullOrBlank() || raw == LyricsEntity.LYRICS_NOT_FOUND) return emptyList()
    if (!com.metrolist.music.lyrics.lyricsTextLooksSynced(raw)) return raw.lines().filter { it.isNotBlank() }.take(4)
    val lines = LyricsUtils.parseLyrics(raw).filter { it.text.isNotBlank() }
    val current = lines.indexOfLast { it.time <= position }.coerceAtLeast(0)
    return lines.drop(current).take(4).map { it.text }
}

internal data class LyricsPreviewWindow(val lines: List<String>, val activeLine: Int)
internal fun lyricsPreviewWindow(raw: String?, position: Long): LyricsPreviewWindow {
    if (raw.isNullOrBlank() || raw == LyricsEntity.LYRICS_NOT_FOUND) return LyricsPreviewWindow(emptyList(), -1)
    if (!com.metrolist.music.lyrics.lyricsTextLooksSynced(raw)) return LyricsPreviewWindow(raw.lines().filter(String::isNotBlank).take(5), -1)
    val entries = LyricsUtils.parseLyrics(raw).filter { it.text.isNotBlank() }
    val active = activeLyricsLine(entries.map { it.time }, position)
    val start = (active - 1).coerceAtLeast(0)
    return LyricsPreviewWindow(entries.drop(start).take(5).map { it.text }, if (active < 0) -1 else active - start)
}

@Composable
fun SongLyricsCard(song: MediaMetadata, position: Long) {
    val connection = LocalPlayerConnection.current ?: return
    val lyrics by connection.currentLyrics.collectAsStateWithLifecycle()
    val database = LocalDatabase.current
    val context = LocalContext.current
    val color = LocalSongColor.current
    var fullScreen by rememberSaveable(song.id) { mutableStateOf(false) }
    LaunchedEffect(song.id) {
        withContext(Dispatchers.IO) {
            try {
                if (database.lyrics(song.id).first() == null) {
                    val helper = EntryPointAccessors.fromApplication(context.applicationContext,
                        com.metrolist.music.di.LyricsHelperEntryPoint::class.java).lyricsHelper()
                    val result = helper.getLyrics(song)
                    database.withTransaction { upsert(LyricsEntity(song.id, result.lyrics, result.provider)) }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* The preview remains unavailable; playback continues. */ }
        }
    }
    val raw = lyrics?.takeIf { it.id == song.id }?.lyrics
    val window = remember(raw, position / 500) { lyricsPreviewWindow(raw, position) }
    val synced = com.metrolist.music.lyrics.lyricsTextLooksSynced(raw)
    LyricsPreviewCard(window.lines, color, onFullscreen = { fullScreen = true }, activeLine = window.activeLine)
    if (fullScreen) Dialog(onDismissRequest = { fullScreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(lerp(color, Color.Black, 0.4f), color))).systemBarsPadding().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(song.artists.joinToString { it.name }, color = Color.White)
                    Text(com.metrolist.music.utils.makeTimeString(position) + " / " +
                        com.metrolist.music.utils.makeTimeString(song.duration * 1000L), color = Color.White.copy(alpha = 0.8f))
                }
                IconButton(onClick = { fullScreen = false }) {
                    Icon(painterResource(R.drawable.fullscreen), stringResource(R.string.cd_back), tint = Color.White)
                }
            }
            val entries = remember(raw) {
                if (raw.isNullOrBlank() || raw == LyricsEntity.LYRICS_NOT_FOUND) emptyList()
                else if (synced) LyricsUtils.parseLyrics(raw).filter { it.text.isNotBlank() }
                else raw.lines().filter(String::isNotBlank).map { com.metrolist.music.lyrics.LyricsEntry(0, it) }
            }
            val active = if (synced) activeLyricsLine(entries.map { it.time }, position) else -1
            val listState = rememberLazyListState()
            LaunchedEffect(active) {
                if (active >= 0 && !listState.isScrollInProgress) listState.animateScrollToItem((active - 1).coerceAtLeast(0))
            }
            if (!synced) Text(stringResource(R.string.lyrics_unsynced), color = Color.White.copy(alpha = 0.75f))
            LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                itemsIndexed(entries) { index, line ->
                    Text(line.text, style = MaterialTheme.typography.headlineMedium,
                        fontFamily = PlayerLyricsFont,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = if (!synced || index == active) 1f else 0.5f))
                }
            }
        }
    }
}

internal val PlayerLyricsFont = FontFamily(Font(R.font.lato_bold, FontWeight.Bold))

@Composable
internal fun LyricsPreviewCard(preview: List<String>, color: Color, onFullscreen: () -> Unit, highlightFirst: Boolean = true, activeLine: Int = if (highlightFirst) 0 else -1) {
    val top = lerp(color, Color.Black, 0.45f)
    val bottom = lerp(color, Color.Black, 0.12f)
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = Color.Transparent, contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().height(400.dp).background(Brush.linearGradient(listOf(top, bottom))).padding(16.dp)) {
            Text(stringResource(R.string.player_lyrics_preview), fontFamily = PlayerLyricsFont, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(24.dp))
            Column(Modifier.weight(1f).fillMaxWidth().clipToBounds()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(0f to Color.White, 0.82f to Color.White, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                }, verticalArrangement = Arrangement.spacedBy(22.dp)) {
                if (preview.isEmpty()) Text(stringResource(R.string.lyrics_not_found))
                else preview.forEachIndexed { index, line ->
                    Text(line, fontFamily = PlayerLyricsFont, fontWeight = FontWeight.Bold, fontSize = 21.sp,
                        lineHeight = 27.sp, letterSpacing = (-0.35).sp,
                        color = Color.White.copy(alpha = if (activeLine < 0 || index == activeLine) 1f else 0.65f))
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onFullscreen, shape = androidx.compose.foundation.shape.CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.player_show_lyrics), fontFamily = PlayerLyricsFont, fontWeight = FontWeight.Bold)
            }
        }
    }
}
