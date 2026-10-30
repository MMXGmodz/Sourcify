package com.metrolist.music.ui.tv

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.*
import com.metrolist.music.R
import com.metrolist.music.constants.*
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.player.CanvasVideoBackground
import com.metrolist.music.ui.theme.LocalSongColor
import com.metrolist.music.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf

private enum class TvPage(val label: Int) {
    SEARCH(R.string.search), LIBRARY(R.string.tv_library), FAVORITES(R.string.tv_favorites),
    PLAYLISTS(R.string.playlists), DOWNLOADS(R.string.tv_downloads), QUEUE(R.string.queue),
    PLAYER(R.string.tv_now_playing), SETTINGS(R.string.settings),
}

@Composable
internal fun TvActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, selected: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Surface(onClick = onClick, enabled = enabled, shape = shape,
        color = if (focused) Color(0xFFADE3FF) else if (selected) Color(0xFF314B60) else Color(0xFF242931),
        contentColor = if (focused) Color.Black else if (enabled) Color.White else Color.Gray,
        modifier = modifier.onFocusChanged { focused = it.hasFocus }
            .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)) {
        Text(text, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.defaultMinSize(minHeight = 44.dp).padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

@Composable
fun TvApp() {
    val connection = LocalPlayerConnection.current
    if (connection == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val ready by connection.service.isPlayerReady.collectAsStateWithLifecycle()
    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val database = LocalDatabase.current
    val download = LocalDownloadUtil.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    var page by rememberSaveable { mutableStateOf(TvPage.SEARCH) }
    var playlistId by rememberSaveable { mutableStateOf<String?>(null) }
    var playlistName by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    val current by connection.mediaMetadata.collectAsStateWithLifecycle()
    val playing by connection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val windows by connection.queueWindows.collectAsStateWithLifecycle()
    val shuffle by connection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val canNext by connection.canSkipNext.collectAsStateWithLifecycle()
    val canPrevious by connection.canSkipPrevious.collectAsStateWithLifecycle()
    val repeat by connection.repeatMode.collectAsStateWithLifecycle()
    val failure by connection.error.collectAsStateWithLifecycle()
    val library by remember(database) { database.songsByCreateDateAsc() }.collectAsStateWithLifecycle(emptyList())
    val favorites by remember(database) { database.likedSongsByCreateDateAsc() }.collectAsStateWithLifecycle(emptyList())
    val downloads by remember(database) { database.downloadedSongsByCreateDateAsc() }.collectAsStateWithLifecycle(emptyList())
    val playlists by remember(database) { database.playlistsByNameAsc() }.collectAsStateWithLifecycle(emptyList())
    val playlistSongs by remember(database, playlistId) { playlistId?.let { database.playlistSongs(it) } ?: flowOf(emptyList()) }
        .collectAsStateWithLifecycle(emptyList())
    val savedSong by remember(database, current?.id) { database.song(current?.id) }.collectAsStateWithLifecycle(null)
    val (canvasEnabled, setCanvasEnabled) = rememberPreference(CanvasEnabledKey, true)
    val (automatic, setAutomatic) = rememberPreference(AutomaticCanvasKey, true)
    val (brightness, setBrightness) = rememberPreference(CanvasBrightnessKey, 0.65f)
    val volume by connection.service.playerVolume.collectAsStateWithLifecycle()
    fun setVolume(value: Float) { connection.service.playerVolume.value = value.coerceIn(0f, 1f) }
    val (hideExplicit, setHideExplicit) = rememberPreference(HideExplicitKey, false)
    val hideVideo by rememberPreference(HideVideoSongsKey, false)
    var position by remember { mutableLongStateOf(0) }
    var duration by remember { mutableLongStateOf(0) }
    LaunchedEffect(connection, current?.id) {
        while (isActive) {
            position = connection.player.currentPosition.coerceAtLeast(0)
            duration = connection.player.duration.coerceAtLeast(0)
            delay(1000)
        }
    }
    var searchFailure by remember { mutableStateOf(false) }
    val search by produceState<List<MediaMetadata>?>(emptyList(), submitted, attempt) {
        if (submitted.isBlank()) { value = emptyList(); return@produceState }
        value = null; searchFailure = false
        value = try {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(20_000) { YouTube.search(submitted, YouTube.SearchFilter.FILTER_SONG).getOrThrow() }
                    ?.items?.filterIsInstance<SongItem>()?.map { it.toMediaMetadata() }
                    ?: throw java.io.IOException("Search timed out")
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { searchFailure = true; emptyList() }
    }
    fun searchNow() { submitted = query.trim().take(200); attempt++ }
    val songs = remember(page, library, favorites, downloads, playlistSongs, search, hideExplicit, hideVideo) {
        val source = when (page) {
            TvPage.LIBRARY -> library.map { it.toMediaMetadata() }
            TvPage.FAVORITES -> favorites.map { it.toMediaMetadata() }
            TvPage.DOWNLOADS -> downloads.map { it.toMediaMetadata() }
            TvPage.PLAYLISTS -> playlistSongs.map { it.song.toMediaMetadata() }
            TvPage.SEARCH -> search.orEmpty()
            else -> emptyList()
        }
        source.filter { (!hideExplicit || !it.explicit) && (!hideVideo || !it.isVideoSong) }
    }
    val pageTitle = if (page == TvPage.PLAYLISTS && playlistId != null) playlistName else stringResource(page.label)
    fun safely(action: () -> Unit) {
        try { action() } catch (_: Exception) { Toast.makeText(context, R.string.tv_action_failed, Toast.LENGTH_LONG).show() }
    }
    fun playSongs(index: Int = 0) = safely {
        if (songs.isNotEmpty()) connection.playQueue(ListQueue(pageTitle, songs.map { it.toMediaItem() }, index))
    }
    BackHandler(enabled = page != TvPage.SEARCH || playlistId != null) {
        if (playlistId != null) playlistId = null else page = TvPage.SEARCH
        scope.launch { firstFocus.requestFocus() }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        inputMode.requestInputMode(InputMode.Keyboard)
        firstFocus.requestFocus()
    }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
        androidx.compose.ui.graphics.lerp(LocalSongColor.current, Color.Black, 0.72f), Color(0xFF080B10))))
        .onPreviewKeyEvent { event ->
            val action = tvMediaAction(event.nativeKeyEvent.keyCode)
            if (action == null) false else {
                if (event.type == KeyEventType.KeyUp) safely {
                    when (action) {
                        TvMediaAction.TOGGLE -> connection.togglePlayPause()
                        TvMediaAction.PLAY -> connection.player.play()
                        TvMediaAction.PAUSE -> connection.player.pause()
                        TvMediaAction.NEXT -> connection.seekToNext()
                        TvMediaAction.PREVIOUS -> connection.seekToPrevious()
                        TvMediaAction.STOP -> connection.player.stop()
                    }
                }
                true
            }
        }.padding(horizontal = 40.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tv_wordmark), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.weight(1f))
            Text(pageTitle, style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            LazyColumn(Modifier.width(156.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(TvPage.entries) { index, item ->
                    TvActionButton(stringResource(item.label), onClick = { page = item; playlistId = null }, selected = page == item,
                        modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (page) {
                    TvPage.SETTINGS -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.tv_version, BuildConfig.VERSION_NAME), color = Color.White)
                        TvActionButton(stringResource(R.string.tv_canvas_state, stringResource(if (canvasEnabled) R.string.tv_on else R.string.tv_off)), { setCanvasEnabled(!canvasEnabled) })
                        TvActionButton(stringResource(R.string.tv_auto_canvas_state, stringResource(if (automatic) R.string.tv_on else R.string.tv_off)), { setAutomatic(!automatic) })
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TvActionButton(stringResource(R.string.tv_darker), { setBrightness((brightness - 0.1f).coerceIn(0.15f, 1f)) })
                            TvActionButton(stringResource(R.string.tv_brighter), { setBrightness((brightness + 0.1f).coerceIn(0.15f, 1f)) })
                        }
                        Text(stringResource(R.string.tv_brightness, (brightness * 100).toInt()), color = Color.White)
                        TvActionButton(stringResource(R.string.tv_explicit_state, stringResource(if (hideExplicit) R.string.tv_on else R.string.tv_off)), { setHideExplicit(!hideExplicit) })
                        Text(stringResource(R.string.tv_account_notice), color = Color.White.copy(alpha = 0.7f))
                    }
                    TvPage.PLAYER -> {
                        val song = current
                        if (song == null) Text(stringResource(R.string.tv_no_song), color = Color.White)
                        else Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            TvArtwork(song, playing, canvasEnabled, automatic, Modifier.width(220.dp).fillMaxHeight())
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(song.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(song.artists.joinToString { it.name }, style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.8f))
                                song.album?.title?.let { Text(it, color = Color.White.copy(alpha = 0.7f)) }
                                TvActionButton(stringResource(if (savedSong?.song?.liked == true) R.string.tv_unfavorite else R.string.tv_favorite), { safely(connection::toggleLike) })
                                TvActionButton(stringResource(R.string.tv_download_song), { safely { download.download(song) } })
                                Text(stringResource(R.string.tv_remote_hint), color = Color.White.copy(alpha = 0.65f))
                            }
                        }
                    }
                    TvPage.QUEUE -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (windows.isEmpty()) item { Text(stringResource(R.string.tv_empty), color = Color.White) }
                        itemsIndexed(windows) { _, window ->
                            val metadata = window.mediaItem.metadata
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TvActionButton(metadata?.title ?: window.mediaItem.mediaId, onClick = { safely {
                                    connection.player.seekToDefaultPosition(window.firstPeriodIndex); connection.player.play()
                                } }, modifier = Modifier.weight(1f), selected = current?.id == metadata?.id)
                                TvActionButton(stringResource(R.string.tv_remove), { safely { connection.player.removeMediaItem(window.firstPeriodIndex) } })
                            }
                        }
                    }
                    else -> {
                        if (page == TvPage.SEARCH) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = query, onValueChange = { query = it.take(200) }, singleLine = true,
                                label = { Text(stringResource(R.string.search)) }, modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { searchNow() }))
                            TvActionButton(stringResource(R.string.search), { searchNow() }, enabled = query.isNotBlank())
                        }
                        if (page == TvPage.SEARCH && search == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (searchFailure && page == TvPage.SEARCH) {
                            Text(stringResource(R.string.tv_search_failed), color = Color.White)
                            TvActionButton(stringResource(R.string.retry), { searchNow() })
                        }
                        if (page == TvPage.PLAYLISTS && playlistId == null) {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (playlists.isEmpty()) item { Text(stringResource(R.string.tv_empty), color = Color.White) }
                                itemsIndexed(playlists) { _, item -> TvActionButton(item.playlist.name, {
                                    playlistId = item.id; playlistName = item.playlist.name
                                }, Modifier.fillMaxWidth()) }
                            }
                        } else {
                            if (songs.isNotEmpty()) TvActionButton(stringResource(R.string.play_all), { playSongs() })
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (songs.isEmpty() && search != null) item {
                                    Text(stringResource(if (page == TvPage.SEARCH && submitted.isBlank()) R.string.tv_search_hint else R.string.tv_empty), color = Color.White.copy(alpha = 0.7f))
                                }
                                itemsIndexed(songs, key = { i, song -> "$i:${song.id}" }) { index, song ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(song.thumbnailUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp))
                                        TvActionButton(song.title + "\n" + song.artists.joinToString { it.name }, { playSongs(index) }, Modifier.weight(1f), selected = current?.id == song.id)
                                        TvActionButton(stringResource(R.string.tv_enqueue), { safely { connection.addToQueue(song.toMediaItem()) } })
                                        TvActionButton(stringResource(R.string.tv_download_short), { safely { download.download(song) } })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        failure?.let { Text(stringResource(R.string.tv_playback_failed, it.errorCodeName), color = Color(0xFFFFB4AB), maxLines = 2) }
        current?.let { song ->
            Text(song.title + " — " + song.artists.joinToString { it.name }, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (duration > 0) LinearProgressIndicator(progress = { (position.toFloat() / duration).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TvActionButton(stringResource(R.string.tv_previous), { safely(connection::seekToPrevious) }, enabled = current != null && canPrevious)
            TvActionButton(stringResource(if (playing) R.string.pause else R.string.play), { safely(connection::togglePlayPause) }, enabled = current != null)
            TvActionButton(stringResource(R.string.tv_next), { safely(connection::seekToNext) }, enabled = current != null && canNext)
            TvActionButton("−10 s", { safely { connection.seekTo(tvSeekPosition(position, duration, -10000)) } }, enabled = duration > 0)
            TvActionButton("+10 s", { safely { connection.seekTo(tvSeekPosition(position, duration, 10000)) } }, enabled = duration > 0)
            TvActionButton(stringResource(R.string.shuffle), { safely { connection.player.shuffleModeEnabled = !shuffle } }, selected = shuffle)
            TvActionButton(stringResource(when (repeat) { 1 -> R.string.tv_repeat_one; 2 -> R.string.tv_repeat_all; else -> R.string.tv_repeat_off }), { safely { connection.player.repeatMode = (repeat + 1) % 3 } }, selected = repeat != 0)
            TvActionButton("−", { setVolume((volume - 0.1f).coerceIn(0f, 1f)) })
            Text("${(volume * 100).toInt()}%", color = Color.White)
            TvActionButton("+", { setVolume((volume + 0.1f).coerceIn(0f, 1f)) })
        }
    }
}

@Composable
private fun TvArtwork(song: MediaMetadata, playing: Boolean, enabled: Boolean, automatic: Boolean, modifier: Modifier) {
    val selection = rememberCanvasSelection(song.id)
    val songEnabled = enabled && com.metrolist.music.utils.rememberCanvasEnabled(song.id)
    val selected = rememberSelectedCanvas(selection, songEnabled)
    val auto = rememberAutomaticCanvas(song, songEnabled && automatic && selection == null)
    val source = if (selection != null) selected else auto
    var failed by remember(song.id, source) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        AsyncImage(song.thumbnailUrl, song.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        if (enabled && source != null && !failed) CanvasVideoBackground(source, playing, onError = { failed = true }, modifier = Modifier.fillMaxSize())
    }
}
