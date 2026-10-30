package com.metrolist.music.ui.screens.settings

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.*
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.player.CanvasVideoBackground
import com.metrolist.music.utils.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.roundToInt

private data class CanvasEdit(val songId: String, val source: CanvasVideoSource, val duration: Long, val videoId: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasSettings(navController: NavController) {
    val context = LocalContext.current
    val downloads = if (LocalPlayerConnection.current != null) com.metrolist.music.LocalDownloadUtil.current else null
    val scope = rememberCoroutineScope()
    val store = remember(context) { CanvasVideoStore(context) }
    val metadata by LocalPlayerConnection.current?.mediaMetadata?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }
    val selection = rememberCanvasSelection(metadata?.id)
    val enabledForSong = rememberCanvasEnabled(metadata?.id)
    val (enabled, setEnabled) = rememberPreference(CanvasEnabledKey, true)
    val (wifiOnly, setWifiOnly) = rememberPreference(CanvasWifiOnlyKey, false)
    val (automatic, setAutomatic) = rememberPreference(AutomaticCanvasKey, true)
    val (brightness, setBrightness) = rememberPreference(CanvasBrightnessKey, 0.65f)
    val (zoom, setZoom) = rememberPreference(CanvasZoomKey, 1.35f)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<CanvasEdit?>(null) }
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    var cacheBytes by remember { mutableLongStateOf(0L) }
    LaunchedEffect(tab) { if (tab == 2) cacheBytes = withContext(Dispatchers.IO) { runCatching { CanvasVideoCache.bytesUsed(context) }.getOrDefault(0L) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val songId = target
        target = null
        if (uri != null && songId != null) scope.launch {
            busy = true; error = false
            try {
                val duration = withContext(Dispatchers.IO) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        requireNotNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull())
                            .also { require(it > 0) }
                    } finally { retriever.release() }
                }
                edit = CanvasEdit(songId, CanvasVideoSource(uri), duration)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
    TopAppBar(title = { Text(stringResource(R.string.sourcify_canvas)) }, navigationIcon = {
        IconButton(onClick = { navController.navigateUp() }) { Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.cd_back)) }
    })
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf(R.string.sourcify_canvas_general, R.string.sourcify_canvas_videos, R.string.sourcify_canvas_cache).forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(label)) })
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (tab) {
                0 -> Material3SettingsGroup(title = stringResource(R.string.sourcify_canvas), items = listOf(
                    Material3SettingsItem(icon = painterResource(R.drawable.slow_motion_video), title = { Text(stringResource(R.string.sourcify_canvas)) },
                        description = { Text(stringResource(R.string.sourcify_canvas_description)) },
                        trailingContent = { Switch(enabled, setEnabled) }, onClick = { setEnabled(!enabled) }),
                    Material3SettingsItem(icon = painterResource(R.drawable.offline), title = { Text(stringResource(R.string.canvas_wifi_only)) },
                        description = { Text(stringResource(R.string.canvas_wifi_only_description)) },
                        trailingContent = { Switch(wifiOnly, setWifiOnly) }, onClick = { setWifiOnly(!wifiOnly) }),
                    Material3SettingsItem(icon = painterResource(R.drawable.search), title = { Text(stringResource(R.string.sourcify_canvas_automatic)) },
                        description = { Text(stringResource(R.string.sourcify_canvas_automatic_description)) },
                        trailingContent = { Switch(automatic, setAutomatic) }, onClick = { setAutomatic(!automatic) }),
                    Material3SettingsItem(icon = painterResource(R.drawable.slow_motion_video), title = { Text(stringResource(R.string.canvas_brightness)) },
                        description = {
                            Column {
                                Text("${(brightness * 100).roundToInt()}%")
                                Slider(value = brightness.coerceIn(0.15f, 1f), onValueChange = setBrightness, valueRange = 0.15f..1f)
                            }
                        }),
                    Material3SettingsItem(icon = painterResource(R.drawable.fullscreen), title = { Text(stringResource(R.string.sourcify_canvas_zoom)) },
                        description = {
                            Column {
                                Text(stringResource(R.string.sourcify_canvas_zoom_description))
                                Text("${(zoom * 100).roundToInt()}%")
                                Slider(value = zoom.coerceIn(1f, 2f), onValueChange = setZoom, valueRange = 1f..2f)
                            }
                        }),
                ))
                1 -> {
                    Text(metadata?.title ?: stringResource(R.string.sourcify_canvas_start_song), style = MaterialTheme.typography.titleLarge)
                    if (metadata != null) Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(stringResource(R.string.sourcify_canvas_this_song), Modifier.weight(1f))
                        Switch(enabledForSong, { store.setEnabled(metadata!!.id, it); if (it) downloads?.requestCompanionDownload(metadata!!.id) })
                    }
                    Text(stringResource(R.string.sourcify_canvas_for_song), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { target = metadata?.id; picker.launch(arrayOf("video/*")) }, enabled = metadata != null && !busy) {
                        Text(stringResource(R.string.sourcify_canvas_choose))
                    }
                    if (selection != null) OutlinedButton(onClick = { metadata?.id?.let(store::remove) }, enabled = !busy) {
                        Text(stringResource(R.string.sourcify_canvas_remove))
                    }
                    OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(stringResource(R.string.sourcify_canvas_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(enabled = metadata != null && query.isNotBlank() && !busy, onClick = {
                        val searchQuery = query.trim()
                        scope.launch {
                            busy = true; error = false; results = emptyList(); searched = false
                            try {
                                results = withContext(Dispatchers.IO) {
                                    withTimeout(20_000) {
                                        check(CanvasNetworkPolicy.get(context).mayLoadRemote())
                                        YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_VIDEO).getOrThrow().items.filterIsInstance<SongItem>().take(20) }
                                }
                                searched = true
                            } catch (e: kotlinx.coroutines.TimeoutCancellationException) { error = true }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { error = true }
                            finally { busy = false }
                        }
                    }) { Text(stringResource(R.string.search)) }
                    if (searched && results.isEmpty()) Text(stringResource(R.string.sourcify_canvas_no_results))
                    results.forEach { video ->
                        ListItem(headlineContent = { Text(video.title) }, supportingContent = { Text(video.artists.joinToString { it.name }) },
                            modifier = Modifier.clickable(enabled = !busy && metadata != null) {
                                val songId = metadata?.id ?: return@clickable
                                scope.launch {
                                    busy = true; error = false
                                    try {
                                        edit = withContext(Dispatchers.IO) {
                                            withTimeout(25_000) {
                                                check(CanvasNetworkPolicy.get(context).mayLoadRemote())
                                                val source = requireNotNull(resolveSelectedCanvasVideo(video.id))
                                                val duration = requireNotNull(source.stream?.mediaMetadata?.durationSeconds?.times(1_000)).also { require(it > 0) }
                                                CanvasEdit(songId, source, duration, video.id)
                                            }
                                        }
                                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) { error = true }
                                    catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { error = true }
                                    finally { busy = false }
                                }
                            })
                    }
                }
                2 -> {
                    Text(stringResource(R.string.sourcify_canvas_cache), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.sourcify_canvas_cache_description))
                    Text("${cacheBytes / (1024 * 1024)} / 96 MB")
                    OutlinedButton(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true; error = false
                            try { withContext(Dispatchers.IO) { CanvasVideoCache.clear() }; cacheBytes = 0 }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { error = true }
                            finally { busy = false }
                        }
                    }) { Text(stringResource(R.string.sourcify_canvas_clear_cache)) }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error) Text(stringResource(R.string.sourcify_canvas_operation_error), color = MaterialTheme.colorScheme.error)
        }
    }

    edit?.let { editor ->
        var start by remember(editor) { mutableFloatStateOf(canvasMiddleClip(editor.duration).first.toFloat()) }
        var previewStart by remember(editor) { mutableLongStateOf(start.toLong()) }
        var failed by remember(editor) { mutableStateOf(false) }
        val clip = canvasClip(previewStart, editor.duration)
        val preview = remember(editor, clip) { editor.source.copy(clipStartMs = clip.first, clipEndMs = clip.last + 1) }
        AlertDialog(onDismissRequest = { edit = null }, title = { Text(stringResource(R.string.sourcify_canvas_clip)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CanvasVideoBackground(preview, true, { failed = true }, Modifier.fillMaxWidth().height(200.dp))
                    Text(stringResource(R.string.sourcify_canvas_clip_range, clip.first / 1000f, (clip.last + 1) / 1000f))
                    Slider(value = start, onValueChange = { start = it }, onValueChangeFinished = { previewStart = start.toLong(); failed = false },
                        valueRange = 0f..maxOf(1f, (editor.duration - 8_000).toFloat()), enabled = editor.duration > 8_000)
                    if (failed) Text(stringResource(R.string.sourcify_canvas_operation_error), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = !failed, onClick = {
                    try {
                        store.saveClip(editor.songId, CanvasSelection(uri = editor.source.uri.takeIf { editor.videoId == null }, videoId = editor.videoId, startMs = clip.first, endMs = clip.last + 1))
                        store.setEnabled(editor.songId, true)
                        downloads?.requestCompanionDownload(editor.songId)
                        setEnabled(true); edit = null
                    } catch (_: Exception) { failed = true }
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}
