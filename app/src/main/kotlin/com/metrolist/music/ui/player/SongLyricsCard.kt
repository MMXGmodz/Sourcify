package com.metrolist.music.ui.player

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.lyrics.saveFetchedLyricsIfMissing
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

internal data class LyricsPreviewWindow(val lines: List<String>, val activeLine: Int, val startIndex: Int = 0)
internal fun lyricsPreviewWindow(raw: String?, position: Long): LyricsPreviewWindow {
    if (raw.isNullOrBlank() || raw == LyricsEntity.LYRICS_NOT_FOUND) return LyricsPreviewWindow(emptyList(), -1)
    if (!com.metrolist.music.lyrics.lyricsTextLooksSynced(raw)) return LyricsPreviewWindow(raw.lines().filter(String::isNotBlank).take(5), -1)
    val entries = LyricsUtils.parseLyrics(raw).filter { it.text.isNotBlank() }
    val active = activeLyricsLine(entries.map { it.time }, position)
    val start = (active - 1).coerceAtLeast(0)
    return LyricsPreviewWindow(entries.drop(start).take(5).map { it.text }, if (active < 0) -1 else active - start, start)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongLyricsCard(song: MediaMetadata, position: Long, onSeekRequested: ((Long) -> Unit)? = null) {
    val connection = LocalPlayerConnection.current ?: return
    val lyrics by connection.currentLyrics.collectAsStateWithLifecycle()
    val database = LocalDatabase.current
    val context = LocalContext.current
    val currentSong by connection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val offset = currentSong?.takeIf { it.song.id == song.id }?.song?.lyricsOffset ?: 0
    val clickEnabled by com.metrolist.music.utils.rememberPreference(com.metrolist.music.constants.LyricsClickKey, true)
    val role = com.metrolist.music.LocalListenTogetherManager.current?.role?.collectAsStateWithLifecycle()
    val guest = role?.value == com.metrolist.music.listentogether.RoomRole.GUEST
    val cast = connection.service.castConnectionHandler
    val casting by cast?.isCasting?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val castPlaying by cast?.castIsPlaying?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val playing by connection.isPlaying.collectAsStateWithLifecycle()
    var livePosition by remember(song.id) { mutableLongStateOf(position) }
    var pendingSeek by remember(song.id) { mutableStateOf<Pair<Long, Long>?>(null) }
    fun seek(target: Long) {
        pendingSeek = target to android.os.SystemClock.elapsedRealtime()
        if (onSeekRequested != null) onSeekRequested(target) else connection.seekTo(target)
        livePosition = target
    }
    val color = LocalSongColor.current
    var sharing by rememberSaveable(song.id) { mutableStateOf(false) }
    if (sharing) SongShareSheet(song, { sharing = false }, startWithLyrics = true)
    var fullScreen by rememberSaveable(song.id) { mutableStateOf(false) }
    var lyricsOptions by rememberSaveable(song.id) { mutableStateOf(false) }
    var offsetEditor by rememberSaveable(song.id) { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(fullScreen, song.id, lifecycle) {
        if (fullScreen) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val remote = cast?.isCasting?.value == true
                val reported = if (remote) cast!!.castPosition.value else connection.player.currentPosition
                val pending = pendingSeek
                livePosition = lyricsReportedPosition(reported, pending?.first?.takeIf { remote },
                    android.os.SystemClock.elapsedRealtime() - (pending?.second ?: 0L))
                delay(100)
            }
        }
    }
    LaunchedEffect(song.id) {
        withContext(Dispatchers.IO) {
            try {
                if (database.lyrics(song.id).first()?.lyrics.let { it == null || it == LyricsEntity.LYRICS_NOT_FOUND }) {
                    val helper = EntryPointAccessors.fromApplication(context.applicationContext,
                        com.metrolist.music.di.LyricsHelperEntryPoint::class.java).lyricsHelper()
                    val result = helper.getLyrics(song)
                    if (result.lyrics.isNotBlank() && result.lyrics != LyricsEntity.LYRICS_NOT_FOUND) {
                        database.saveFetchedLyricsIfMissing(LyricsEntity(song.id, result.lyrics, result.provider))
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* The preview remains unavailable; playback continues. */ }
        }
    }
    val raw = lyrics?.takeIf { it.id == song.id }?.lyrics
    val window = remember(raw, position / 500, offset) { lyricsPreviewWindow(raw, position + offset) }
    val synced = com.metrolist.music.lyrics.lyricsTextLooksSynced(raw)
    LyricsPreviewCard(window.lines, color, onFullscreen = { fullScreen = true }, activeLine = window.activeLine, startIndex = window.startIndex)
    if (fullScreen) Dialog(onDismissRequest = { fullScreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(lerp(color, Color.Black, 0.4f), color))).systemBarsPadding().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(song.artists.joinToString { it.name }, color = Color.White)
                    Text(com.metrolist.music.utils.makeTimeString(livePosition) + " / " +
                        com.metrolist.music.utils.makeTimeString(song.duration * 1000L), color = Color.White.copy(alpha = 0.8f))
                }
                IconButton(onClick = { sharing = true }) { Icon(painterResource(R.drawable.share), stringResource(R.string.s6_share_lyrics), tint = Color.White) }
                IconButton(onClick = { fullScreen = false }) {
                    Icon(painterResource(R.drawable.fullscreen_exit), stringResource(R.string.sourcify_exit_fullscreen), tint = Color.White)
                }
            }
            TextButton(onClick = { lyricsOptions = true }) {
                Icon(painterResource(R.drawable.search), null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.s8_change_lyrics), color = Color.White)
            }
            val entries = remember(raw) {
                if (raw.isNullOrBlank() || raw == LyricsEntity.LYRICS_NOT_FOUND) emptyList()
                else if (synced) LyricsUtils.parseLyrics(raw).filter { it.text.isNotBlank() }
                else raw.lines().filter(String::isNotBlank).map { com.metrolist.music.lyrics.LyricsEntry(0, it) }
            }
            val active = if (synced) activeLyricsLine(entries.map { it.time }, livePosition + offset) else -1
            val listState = rememberLazyListState()
            LaunchedEffect(raw, active) {
                if (active >= 0 && !listState.isScrollInProgress) listState.animateScrollToItem((active - 1).coerceAtLeast(0))
            }
            if (!synced) Text(stringResource(R.string.lyrics_unsynced), color = Color.White.copy(alpha = 0.75f))
            LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                itemsIndexed(entries) { index, line ->
                    FullscreenLyricsLine(line.text, !synced || index == active,
                        lyricsSeekPosition(line.time, offset, synced, clickEnabled, guest) != null) {
                        lyricsSeekPosition(line.time, offset, synced, clickEnabled, guest)?.let { target ->
                            seek(target)
                        }
                    }
                }
            }
            var scrub by remember { mutableStateOf<Float?>(null) }
            val total = (if (casting) cast?.castDuration?.value else song.duration * 1000L)?.coerceAtLeast(1L) ?: 1L
            Slider(value = scrub ?: livePosition.coerceIn(0, total).toFloat(), valueRange = 0f..total.toFloat(), enabled = !guest,
                onValueChange = { scrub = it }, onValueChangeFinished = { scrub?.let { seek(it.toLong()) }; scrub = null },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = .3f)))
            FilledIconButton(onClick = connection::togglePlayPause, enabled = !guest,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black), modifier = Modifier.size(56.dp)) {
                val activePlayback = if (casting) castPlaying else playing
                Icon(painterResource(if (activePlayback) R.drawable.pause else R.drawable.play), stringResource(if (activePlayback) R.string.pause else R.string.play))
            }
        }
        if (lyricsOptions) ModalBottomSheet(onDismissRequest = { lyricsOptions = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Box(Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
                androidx.compose.runtime.key(song.id) {
                    com.metrolist.music.ui.menu.LyricsMenu(lyricsProvider = { lyrics?.takeIf { it.id == song.id } },
                        songProvider = { currentSong?.song?.takeIf { it.id == song.id } }, mediaMetadataProvider = { song },
                        onDismiss = { lyricsOptions = false }, onShowOffsetDialog = { offsetEditor = true }, initiallySearch = true)
                }
            }
        }
        if (offsetEditor) com.metrolist.music.ui.component.DefaultDialog(onDismiss = { offsetEditor = false },
            title = { Text(stringResource(R.string.lyrics_offset)) },
            content = { com.metrolist.music.ui.utils.ShowOffsetDialog { currentSong?.song?.takeIf { it.id == song.id } } },
            buttons = { TextButton(onClick = { offsetEditor = false }) { Text(stringResource(android.R.string.ok)) } })
    }
}

@Composable
internal fun FullscreenLyricsLine(text: String, active: Boolean, seekEnabled: Boolean, onSeek: () -> Unit) {
    Text(text, style = MaterialTheme.typography.headlineMedium, fontFamily = PlayerLyricsFont, fontWeight = FontWeight.Bold,
        color = Color.White.copy(alpha = if (active) 1f else .5f),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).semantics { selected = active }
            .clickable(enabled = seekEnabled, onClickLabel = stringResource(R.string.s7_seek_lyrics), onClick = onSeek)
            .padding(vertical = 6.dp))
}

internal val PlayerLyricsFont = FontFamily(Font(R.font.lato_bold, FontWeight.Bold))

@Composable
internal fun LyricsPreviewCard(preview: List<String>, color: Color, onFullscreen: () -> Unit, highlightFirst: Boolean = true, activeLine: Int = if (highlightFirst) 0 else -1, startIndex: Int = 0) {
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
                    val ink by animateColorAsState(Color.White.copy(alpha = if (activeLine < 0 || index == activeLine) 1f else .65f), tween(250), label = "lyricsHighlight")
                    AnimatedContent(targetState = startIndex to line, label = "previewLine$index", transitionSpec = {
                        val direction = if (targetState.first >= initialState.first) 1 else -1
                        (slideInVertically(tween(260)) { direction * it / 3 } + fadeIn(tween(260))) togetherWith
                            (slideOutVertically(tween(200)) { -direction * it / 3 } + fadeOut(tween(180)))
                    }) { (_, text) ->
                        Text(text, fontFamily = PlayerLyricsFont, fontWeight = FontWeight.Bold, fontSize = 21.sp,
                            lineHeight = 27.sp, letterSpacing = (-.35).sp, color = ink)
                    }
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
