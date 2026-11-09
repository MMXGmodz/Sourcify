package com.metrolist.music.ui.player

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.R
import com.metrolist.music.playback.MusicService
import com.metrolist.music.playback.MusicVideoSelection
import com.metrolist.music.playback.MusicVideoSelectionStore
import com.metrolist.music.playback.localMusicVideoSelection
import com.metrolist.music.playback.musicVideoId
import com.metrolist.music.ui.component.VideoSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Composable
internal fun InitialMusicVideoSearch(songId: String, query: String, onSearch: (String) -> Unit) {
    var started by rememberSaveable(songId) { mutableStateOf(false) }
    LaunchedEffect(songId) {
        if (!started) {
            started = true
            query.trim().takeIf { it.isNotEmpty() }?.let(onSearch)
        }
    }
}

internal suspend fun searchManualMusicVideos(
    query: String,
    search: suspend (String) -> List<YTItem> = {
        YouTube.search(it, YouTube.SearchFilter.FILTER_VIDEO).getOrThrow().items
    },
): List<SongItem> = withContext(Dispatchers.IO) {
    withTimeout(20_000) {
        search(query.trim()).filterIsInstance<SongItem>()
            .filter { musicVideoId(it.id) == it.id }.distinctBy { it.id }.take(20)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManualMusicVideoPicker(songId: String, songTitle: String, service: MusicService, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { MusicVideoSelectionStore(context) }
    val scope = rememberCoroutineScope()
    var query by rememberSaveable(songId) { mutableStateOf(songTitle.trim()) }
    var pickerSongId by rememberSaveable { mutableStateOf<String?>(null) }
    var results by remember(songId) { mutableStateOf(emptyList<SongItem>()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var request by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    fun save(targetSongId: String = songId, selection: suspend () -> MusicVideoSelection?) {
        if (saving) return
        searchJob?.cancel()
        request++
        searching = false
        saving = true
        failed = false
        scope.launch {
            try {
                val selected = withContext(Dispatchers.IO) { withTimeout(20_000) { selection() } }
                // Complete a confirmed selection even if the song changes while the document is read.
                withContext(NonCancellable) {
                    withContext(Dispatchers.IO) {
                        val uri = (selected as? MusicVideoSelection.Local)?.uri
                        val alreadyGranted = uri != null && context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
                        var granted = false
                        try {
                            if (uri != null) {
                                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                granted = true
                            }
                            store.set(targetSongId, selected)
                        } catch (error: Exception) {
                            if (uri != null && granted && !alreadyGranted) runCatching {
                                context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            throw error
                        }
                    }
                    service.reloadMusicVideoSelection(targetSongId)
                }
                onSaved()
            } catch (_: TimeoutCancellationException) { failed = true }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { failed = true }
            finally { saving = false }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetSongId = pickerSongId
        pickerSongId = null
        if (uri != null && targetSongId != null) save(targetSongId) { localMusicVideoSelection(context, uri) }
    }

    fun submit(submitted: String = query.trim(), allowLink: Boolean = true) {
        if (saving || submitted.isEmpty()) return
        if (allowLink) musicVideoId(submitted)?.let { videoId -> save { MusicVideoSelection.YouTube(videoId) }; return }
        searchJob?.cancel()
        val currentRequest = ++request
        searching = true
        searched = false
        failed = false
        results = emptyList()
        searchJob = scope.launch {
            try {
                val videos = searchManualMusicVideos(submitted)
                if (request == currentRequest) { results = videos; searched = true }
            } catch (_: TimeoutCancellationException) { if (request == currentRequest) failed = true }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { if (request == currentRequest) failed = true }
            finally { if (request == currentRequest) searching = false }
        }
    }

    InitialMusicVideoSearch(songId, query) { submit(it, allowLink = false) }

    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !saving })) {
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.s89_choose_video), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.s89_video_audio_note), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = query, onValueChange = {
                        query = it
                        searchJob?.cancel()
                        request++
                        searching = false
                        searched = false
                        failed = false
                        results = emptyList()
                    }, enabled = !saving, singleLine = true,
                        label = { Text(stringResource(R.string.s89_video_search_hint)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }), modifier = Modifier.fillMaxWidth())
                    Button(onClick = { submit() }, enabled = !saving && query.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (musicVideoId(query.trim()) != null) R.string.s89_video_use_link else R.string.s89_video_search))
                    }
                    OutlinedButton(onClick = {
                        pickerSongId = songId
                        try { picker.launch(arrayOf("video/*")) }
                        catch (_: Exception) { pickerSongId = null; failed = true }
                    }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.s89_video_local)) }
                    Text(stringResource(R.string.s89_video_local_note), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { save { null } }, enabled = !saving) { Text(stringResource(R.string.s89_video_automatic)) }
                    if (searching || saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (failed) Text(stringResource(R.string.s89_video_selection_failed), color = MaterialTheme.colorScheme.error)
                    if (searched && results.isEmpty()) Text(stringResource(R.string.s89_video_search_empty))
                }
            }
            items(results, key = { it.id }) { video ->
                VideoSearchResult(video = video, enabled = !saving) { save { MusicVideoSelection.YouTube(video.id) } }
            }
        }
    }
}
