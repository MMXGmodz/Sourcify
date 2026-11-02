package com.metrolist.music.ui.component

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.CanvasVideoStore
import com.metrolist.music.utils.SongEditStore
import com.metrolist.music.utils.songEditArtistNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SongEditAction(metadata: MediaMetadata, onDismiss: () -> Unit) {
    var editing by remember(metadata.id) { mutableStateOf(false) }
    Material3MenuGroup(listOf(Material3MenuItemData(
        title = { Text(stringResource(R.string.sourcify_edit_song)) },
        icon = { Icon(painterResource(R.drawable.edit), null) },
        onClick = { editing = true },
    )))
    if (editing) SongEditDialog(metadata, onDismiss = { editing = false }, onSaved = {
        editing = false
        onDismiss()
    })
}

@Composable
private fun SongEditDialog(metadata: MediaMetadata, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val connection = LocalPlayerConnection.current
    val store = remember(context) { SongEditStore.get(context) }
    val initial = remember(metadata.id) { store.resolve(metadata) }
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initial.title) }
    var artists by remember { mutableStateOf(initial.artists.joinToString("\n") { it.name }) }
    var cover by remember { mutableStateOf(store.cover(metadata.id)) }
    val initialCanvas = remember { store.canvas(metadata.id) }
    var canvas by remember { mutableStateOf(initialCanvas) }
    var busy by remember { mutableStateOf(false) }
    val imported = remember { mutableListOf<Uri>() }
    DisposableEffect(store, metadata.id) {
        onDispose { imported.forEach(store::discard) }
    }
    fun importPickedMedia(uri: Uri?, imageOnly: Boolean) {
        if (uri == null || busy) return
        scope.launch {
            busy = true
            try {
                val media = store.importMedia(uri, imageOnly)
                imported.add(media.uri)
                if (imageOnly) cover = media.uri else canvas = media
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { Toast.makeText(context, R.string.sourcify_edit_media_failed, Toast.LENGTH_LONG).show() }
            finally { busy = false }
        }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { importPickedMedia(it, true) }
    val canvasPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { importPickedMedia(it, false) }
    fun save(reset: Boolean = false) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                val updated = withContext(Dispatchers.IO) {
                    val song = store.updateSong(database, metadata, title, songEditArtistNames(artists), cover, canvas, reset)
                    if (!reset && canvas != null && canvas != initialCanvas) CanvasVideoStore(context).setEnabled(metadata.id, true)
                    song
                }
                connection?.refreshSongMetadata(updated)
                onSaved()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { Toast.makeText(context, R.string.sourcify_edit_failed, Toast.LENGTH_LONG).show() }
            finally { busy = false }
        }
    }
    val names = songEditArtistNames(artists)
    val valid = title.trim().isNotEmpty() && title.trim().length <= 300 && names.size <= 30 && names.all { it.length <= 300 }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.sourcify_edit_song)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, enabled = !busy, singleLine = true,
                    label = { Text(stringResource(R.string.sourcify_edit_title)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(artists, { artists = it }, enabled = !busy, minLines = 2, maxLines = 5,
                    label = { Text(stringResource(R.string.sourcify_edit_artists)) },
                    supportingText = { Text(stringResource(R.string.sourcify_edit_artists_hint)) }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.sourcify_edit_cover), style = MaterialTheme.typography.titleSmall)
                (cover?.toString() ?: store.original(metadata).thumbnailUrl)?.let { artwork ->
                    AsyncImage(artwork, stringResource(R.string.sourcify_edit_cover), Modifier.size(88.dp), contentScale = ContentScale.Crop)
                }
                Text(stringResource(if (cover != null) R.string.sourcify_edit_custom_selected else R.string.sourcify_edit_original), style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(enabled = !busy, onClick = { coverPicker.launch(arrayOf("image/*")) }) {
                        Text(stringResource(R.string.sourcify_edit_choose_image))
                    }
                    if (cover != null) TextButton(enabled = !busy, onClick = { cover = null }) { Text(stringResource(R.string.sourcify_edit_reset)) }
                }
                Text(stringResource(R.string.sourcify_edit_canvas), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(when (canvas?.isImage) {
                    true -> R.string.sourcify_edit_image_selected
                    false -> R.string.sourcify_edit_video_selected
                    null -> R.string.sourcify_edit_original
                }), style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(enabled = !busy, onClick = { canvasPicker.launch(arrayOf("image/*", "video/*")) }) {
                        Text(stringResource(R.string.sourcify_edit_choose_canvas))
                    }
                    if (canvas != null) TextButton(enabled = !busy, onClick = { canvas = null }) { Text(stringResource(R.string.sourcify_edit_reset)) }
                }
                Text(stringResource(R.string.sourcify_edit_local_only), style = MaterialTheme.typography.bodySmall)
                if (store.hasEdits(metadata.id)) TextButton(enabled = !busy, onClick = { save(reset = true) }) {
                    Text(stringResource(R.string.sourcify_edit_reset_all))
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = !busy && valid, onClick = { save() }) { Text(stringResource(R.string.sourcify_edit_save)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
