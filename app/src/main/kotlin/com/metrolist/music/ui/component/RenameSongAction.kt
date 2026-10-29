package com.metrolist.music.ui.component

import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.renameSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@Composable
fun RenameSongAction(metadata: MediaMetadata, onDismiss: () -> Unit) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by rememberSaveable(metadata.id) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var currentTitle by remember(metadata.id, metadata.title) { mutableStateOf(metadata.title) }
    Material3MenuGroup(items = listOf(Material3MenuItemData(
        title = { Text(stringResource(R.string.sourcify_rename_song)) },
        icon = { Icon(painterResource(R.drawable.edit), contentDescription = null) },
        onClick = {
            scope.launch {
                currentTitle = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    database.song(metadata.id).first()?.song?.title ?: metadata.title
                }
                showDialog = true
            }
        },
    )))
    if (showDialog) TextFieldDialog(
        title = { Text(stringResource(R.string.sourcify_rename_song)) },
        initialTextFieldValue = TextFieldValue(currentTitle),
        isInputValid = { !saving && it.trim().isNotEmpty() && it.trim().length <= 300 },
        autoDismiss = false,
        onDone = { title ->
            if (!saving) scope.launch {
                saving = true
                try {
                    val song = database.renameSong(metadata, title)
                    if (com.metrolist.music.utils.LocalAudioStore.isLocalId(metadata.id)) {
                        com.metrolist.music.utils.LocalAudioStore.get(context).rename(metadata.id, title)
                    }
                    playerConnection?.refreshSongMetadata(song)
                    showDialog = false
                    onDismiss()
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { Toast.makeText(context, R.string.sourcify_rename_failed, Toast.LENGTH_LONG).show() }
                finally { saving = false }
            }
        },
        onDismiss = { if (!saving) showDialog = false },
    )
}
