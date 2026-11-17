package com.metrolist.music.ui.menu

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.utils.ExternalPlaylistImporter
import com.metrolist.music.utils.PLAYLIST_IMPORT_MAX_BYTES
import com.metrolist.music.utils.PlaylistImportCollection
import com.metrolist.music.utils.PlaylistImportError
import com.metrolist.music.utils.PlaylistImportException
import com.metrolist.music.utils.PlaylistImportMatch
import com.metrolist.music.utils.parsePlaylistImportFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

@Composable
fun ExternalPlaylistImportDialog(onDismiss: () -> Unit, onImported: (String) -> Unit) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val defaultTitle = stringResource(R.string.import_playlist)
    var link by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(defaultTitle) }
    var matches by remember { mutableStateOf<List<PlaylistImportMatch>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun preview(load: suspend () -> PlaylistImportCollection) {
        busy = true
        error = null
        progress = null
        job = scope.launch {
            try {
                val collection = withContext(Dispatchers.IO) { load() }
                name = collection.title.ifBlank { defaultTitle }
                matches = ExternalPlaylistImporter.match(collection) { current, total -> progress = current to total }
            } catch (_: TimeoutCancellationException) {
                error = R.string.playlist_import_failed
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                error = when ((exception as? PlaylistImportException)?.reason) {
                    PlaylistImportError.UNSUPPORTED -> R.string.playlist_import_unsupported
                    PlaylistImportError.EXPORT_REQUIRED -> R.string.playlist_import_export_required
                    PlaylistImportError.INVALID_FILE -> R.string.playlist_import_invalid_file
                    PlaylistImportError.LIMIT -> R.string.playlist_import_limit
                    PlaylistImportError.EMPTY -> R.string.playlist_import_empty
                    else -> R.string.playlist_import_failed
                }
            } finally {
                busy = false
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) preview {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    if (output.size() + read > PLAYLIST_IMPORT_MAX_BYTES) throw PlaylistImportException(PlaylistImportError.LIMIT)
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            } ?: throw PlaylistImportException(PlaylistImportError.INVALID_FILE)
            val text = try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: java.nio.charset.CharacterCodingException) {
                throw PlaylistImportException(PlaylistImportError.INVALID_FILE)
            }
            PlaylistImportCollection(defaultTitle, parsePlaylistImportFile(text))
        }
    }
    val selected = matches.orEmpty().mapNotNull { match -> match.candidates.find { it.id == match.selectedId } }
    val dismiss = { job?.cancel(); onDismiss() }

    AlertDialog(
        onDismissRequest = { if (!saving) dismiss() },
        properties = DialogProperties(dismissOnBackPress = !saving, dismissOnClickOutside = !saving),
        title = { Text(stringResource(R.string.import_playlist)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (matches == null) {
                    Text(stringResource(R.string.playlist_import_sources), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        label = { Text(stringResource(R.string.playlist_import_link)) },
                        enabled = !busy,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(enabled = !busy, onClick = {
                        filePicker.launch(arrayOf("text/*", "audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "application/vnd.ms-excel", "application/octet-stream"))
                    }) { Text(stringResource(R.string.playlist_import_file)) }
                } else {
                    OutlinedTextField(value = name, onValueChange = { name = it }, enabled = !busy,
                        label = { Text(stringResource(R.string.playlist_import_name)) }, singleLine = true)
                    Text(stringResource(R.string.playlist_import_summary, selected.size,
                        matches.orEmpty().count { it.selectedId == null && it.candidates.isNotEmpty() },
                        matches.orEmpty().count { it.candidates.isEmpty() }), style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        itemsIndexed(matches.orEmpty()) { index, match ->
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Text("${index + 1}. ${match.source.artist} — ${match.source.title}", style = MaterialTheme.typography.titleSmall)
                                if (match.candidates.isEmpty()) {
                                    Text(stringResource(R.string.playlist_import_missing), style = MaterialTheme.typography.bodySmall)
                                } else {
                                    if (match.selectedId == null) Text(stringResource(R.string.playlist_import_review), style = MaterialTheme.typography.labelSmall)
                                    (listOf(null) + match.candidates).forEach { candidate ->
                                        val select = {
                                            matches = matches.orEmpty().mapIndexed { row, item ->
                                                if (row == index) item.copy(selectedId = candidate?.id) else item
                                            }
                                        }
                                        Row(Modifier.fillMaxWidth().clickable(enabled = !busy, onClick = select)) {
                                            RadioButton(selected = match.selectedId == candidate?.id, enabled = !busy, onClick = select)
                                            Text(candidate?.let { "${it.artists.joinToString { artist -> artist.name }} — ${it.title}" }
                                                ?: stringResource(R.string.playlist_import_skip),
                                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(progress?.let { stringResource(R.string.playlist_import_progress, it.first, it.second) }
                        ?: stringResource(R.string.playlist_import_loading), style = MaterialTheme.typography.bodySmall)
                }
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && if (matches == null) link.isNotBlank() else selected.isNotEmpty() && name.isNotBlank(), onClick = {
                if (matches == null) preview { ExternalPlaylistImporter.loadLink(link) }
                else {
                    busy = true
                    saving = true
                    error = null
                    job = scope.launch {
                        try {
                            val playlist = PlaylistEntity(name = name.trim())
                            withContext(Dispatchers.IO) {
                                database.withTransaction {
                                    insert(playlist)
                                    selected.forEachIndexed { position, song ->
                                        insert(song.toMediaMetadata())
                                        insert(PlaylistSongMap(playlistId = playlist.id, songId = song.id, position = position))
                                    }
                                }
                            }
                            onImported(playlist.id)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            error = R.string.playlist_import_save_failed
                        } finally {
                            busy = false
                            saving = false
                        }
                    }
                }
            }) { Text(if (matches == null) stringResource(R.string.playlist_import_preview) else stringResource(R.string.playlist_import_save, selected.size)) }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
