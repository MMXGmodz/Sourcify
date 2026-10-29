package com.metrolist.music.ui.screens.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.*
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.utils.LocalAudioStore
import com.metrolist.music.utils.LocalAudioTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalAudioScreen(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val player = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { LocalAudioStore.get(context) }
    val tracks by store.tracks.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<LocalAudioTrack?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !busy) scope.launch {
            busy = true
            message = null
            var imported = 0
            val failures = mutableListOf<String>()
            try {
                uris.take(100).forEach { uri ->
                    try {
                        val track = store.importAudio(context, uri)
                        withContext(Dispatchers.IO) { database.insert(track.mediaMetadata) }
                        imported++
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { failures += e.message.orEmpty().take(180) }
                }
                message = context.getString(R.string.local_audio_imported, imported) +
                    failures.take(3).joinToString(separator = "\n", prefix = if (failures.isEmpty()) "" else "\n") {
                        context.getString(R.string.local_audio_import_error, it)
                    }
            } finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
        TopAppBar(
            title = { Text(stringResource(R.string.local_audio_title)) },
            navigationIcon = { IconButton(onClick = { navController.navigateUp() }) {
                Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.cd_back))
            } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
        Text(stringResource(R.string.local_audio_description), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { picker.launch(arrayOf("audio/*")) }, enabled = !busy,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Icon(painterResource(R.drawable.add), null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (busy) R.string.local_audio_importing else R.string.local_audio_import))
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        message?.let { Text(it, Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall) }
        if (tracks.isEmpty() && !busy) Text(stringResource(R.string.local_audio_empty), Modifier.padding(20.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                Row(Modifier.fillMaxWidth().clickable(enabled = player != null && !busy) {
                    player?.playQueue(ListQueue(context.getString(R.string.local_audio_title), tracks.map { it.mediaMetadata.toMediaItem() }, index))
                }.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (track.artworkUri != null) AsyncImage(track.artworkUri, null, Modifier.size(56.dp), contentScale = ContentScale.Crop)
                    else Icon(painterResource(R.drawable.music_note), null, Modifier.size(56.dp).padding(10.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (track.artist.isNotBlank()) Text(track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(track.codec, track.sampleRate?.let { "${it / 1000.0} kHz" },
                            stringResource(if (track.isLossless) R.string.local_audio_lossless else R.string.local_audio_lossy)).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { removing = track }, enabled = !busy) {
                        Icon(painterResource(R.drawable.delete), stringResource(R.string.delete))
                    }
                }
            }
        }
    }
    removing?.let { track ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text(stringResource(R.string.local_audio_delete_title)) },
            text = { Text(stringResource(R.string.local_audio_delete_description)) },
            confirmButton = { TextButton(onClick = {
                removing = null
                scope.launch {
                    busy = true
                    try {
                        player?.player?.let { engine ->
                            if (engine.currentMediaItem?.mediaId == track.id) engine.stop()
                            for (index in engine.mediaItemCount - 1 downTo 0) {
                                if (engine.getMediaItemAt(index).mediaId == track.id) engine.removeMediaItem(index)
                            }
                        }
                        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                            store.remove(track.id)
                            database.songEntity(track.id)?.let { database.delete(it) }
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { message = e.message.orEmpty().take(200) }
                    finally { busy = false }
                }
            }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } })
    }
}
