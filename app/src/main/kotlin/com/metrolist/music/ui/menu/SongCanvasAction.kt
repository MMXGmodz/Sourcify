package com.metrolist.music.ui.menu

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData
import com.metrolist.music.utils.CanvasVideoStore
import com.metrolist.music.utils.rememberCanvasEnabled

@Composable
internal fun SongCanvasAction(songId: String) {
    val context = LocalContext.current
    val enabled = rememberCanvasEnabled(songId)
    val store = remember(context) { CanvasVideoStore(context) }
    Material3MenuGroup(listOf(Material3MenuItemData(
        title = { Text(stringResource(R.string.sourcify_canvas_this_song)) },
        icon = { Icon(painterResource(R.drawable.slow_motion_video), null) },
        trailingContent = { Switch(enabled, { store.setEnabled(songId, it) }) },
        onClick = { store.setEnabled(songId, !enabled) },
    )))
}
