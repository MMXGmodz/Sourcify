package com.metrolist.music.ui.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.Material3MenuItemData
import com.metrolist.music.ui.component.SongColorOptions
import com.metrolist.music.utils.rememberPlaylistColor
import com.metrolist.music.utils.setPlaylistColor
import kotlinx.coroutines.launch

@Composable
internal fun playlistColorMenuItem(playlistId: String): Material3MenuItemData {
    val selected = rememberPlaylistColor(playlistId)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var opened by remember(playlistId) { mutableStateOf(false) }
    var draft by remember(playlistId) { mutableStateOf<Int?>(null) }
    var failed by remember(playlistId) { mutableStateOf(false) }
    var saving by remember(playlistId) { mutableStateOf(false) }
    if (opened) DefaultDialog(
        onDismiss = { if (!saving) opened = false },
        title = { Text(stringResource(R.string.playlist_main_color)) },
        buttons = {
            TextButton(enabled = !saving, onClick = { opened = false }) { Text(stringResource(android.R.string.cancel)) }
            TextButton(enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    try { if (setPlaylistColor(context, playlistId, draft)) opened = false else failed = true }
                    finally { saving = false }
                }
            }) { Text(stringResource(R.string.sourcify_edit_save)) }
        },
    ) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            if (failed) Text(stringResource(R.string.playlist_color_save_failed), color = MaterialTheme.colorScheme.error)
            SongColorOptions(draft, { draft = it }, enabled = !saving, titleRes = R.string.playlist_main_color)
        }
    }
    return Material3MenuItemData(
        title = { Text(stringResource(R.string.playlist_main_color)) },
        description = { Text(stringResource(R.string.playlist_main_color_description)) },
        icon = { Icon(painterResource(R.drawable.palette), null) },
        onClick = { draft = selected; failed = false; opened = true },
    )
}
