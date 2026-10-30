package com.metrolist.music.ui.menu

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.R
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData

@Composable
internal fun DownloadExtrasAction(id: String) {
    val util = LocalDownloadUtil.current
    val download by util.getDownload(id).collectAsStateWithLifecycle(initialValue = null)
    val pending by util.extraDownloads.collectAsStateWithLifecycle()
    if (download?.state != Download.STATE_COMPLETED) return
    Material3MenuGroup(listOf(Material3MenuItemData(
        title = { Text(stringResource(R.string.sourcify_offline_extras)) },
        description = { Text(stringResource(if (id in pending) R.string.sourcify_offline_extras_loading else R.string.sourcify_offline_extras_description)) },
        icon = { Icon(painterResource(R.drawable.offline), null) },
        onClick = { if (id !in pending) util.requestCompanionDownload(id) },
    )))
}
