package com.metrolist.music.ui.menu

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import com.metrolist.music.constants.CoverLyricsEnabledKey
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData
import com.metrolist.music.utils.rememberPreference

@Composable
internal fun CoverLyricsAction(onDismiss: () -> Unit) {
    val (enabled, change) = rememberPreference(CoverLyricsEnabledKey, false)
    Material3MenuGroup(items = listOf(Material3MenuItemData(
        title = { Text(stringResource(if (enabled) R.string.s11_lyrics_on else R.string.s11_lyrics_off)) },
        icon = { Icon(painterResource(R.drawable.lyrics), null) },
        onClick = { change(!enabled); onDismiss() },
    )))
}
