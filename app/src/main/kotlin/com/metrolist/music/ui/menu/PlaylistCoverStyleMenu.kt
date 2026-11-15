package com.metrolist.music.ui.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.metrolist.music.utils.setClassicPlaylistCover
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.Material3MenuItemData
import com.metrolist.music.utils.rememberClassicPlaylistCover

@Composable
internal fun playlistCoverStyleMenuItem(
    playlistId: String,
    defaultClassic: Boolean = false,
): Material3MenuItemData {
    val classic by rememberClassicPlaylistCover(playlistId, defaultClassic)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showDialog by remember(playlistId) { mutableStateOf(false) }

    if (showDialog) {
        DefaultDialog(
            onDismiss = { showDialog = false },
            title = { Text(stringResource(R.string.playlist_cover_style)) },
            buttons = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        ) {
            Column(Modifier.selectableGroup()) {
                listOf(false, true).forEach { useClassic ->
                    Row(
                        modifier = Modifier.fillMaxWidth().selectable(
                            selected = classic == useClassic,
                            role = Role.RadioButton,
                            onClick = {
                                scope.launch {
                                    if (setClassicPlaylistCover(context, playlistId, useClassic)) showDialog = false
                                }
                            },
                        ).padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = classic == useClassic, onClick = null)
                        Text(
                            stringResource(if (useClassic) R.string.playlist_cover_classic else R.string.playlist_cover_dynamic),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
    }

    return Material3MenuItemData(
        title = { Text(stringResource(R.string.playlist_cover_style)) },
        description = {
            Text(stringResource(if (classic) R.string.playlist_cover_classic else R.string.playlist_cover_dynamic))
        },
        icon = { Icon(painterResource(R.drawable.insert_photo), contentDescription = null) },
        onClick = { showDialog = true },
    )
}
