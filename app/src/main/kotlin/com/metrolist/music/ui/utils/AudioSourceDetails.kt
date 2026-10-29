package com.metrolist.music.ui.utils

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.R
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.utils.AudioEncoding
import com.metrolist.music.utils.LocalAudioStore
import com.metrolist.music.utils.audioEncoding

@Composable
internal fun AudioSourceDetails(id: String) {
    val context = LocalContext.current
    val store = remember(context) { LocalAudioStore.get(context) }
    val tracks by store.tracks.collectAsStateWithLifecycle()
    val local = tracks.firstOrNull { it.id == id }
    val isLocal = LocalAudioStore.isLocalId(id)
    val format by LocalDatabase.current.format(id).collectAsStateWithLifecycle(initialValue = null)
    val download by LocalDownloadUtil.current.getDownload(id).collectAsStateWithLifecycle(initialValue = null)
    val encoding = audioEncoding(local?.mimeType ?: format?.mimeType, local?.codec ?: format?.codecs)
    val label = stringResource(when (encoding) {
        AudioEncoding.LOSSLESS -> R.string.local_audio_lossless
        AudioEncoding.LOSSY -> R.string.local_audio_lossy
        AudioEncoding.UNKNOWN -> R.string.local_audio_unknown
    })
    val source = stringResource(when {
        isLocal -> R.string.audio_source_local
        download?.state == Download.STATE_COMPLETED -> R.string.audio_source_download
        else -> R.string.audio_source_stream
    })
    val rows = listOfNotNull(
        stringResource(R.string.codecs) to (local?.codec ?: format?.codecs ?: stringResource(R.string.unknown)),
        (local?.sampleRate ?: format?.sampleRate)?.takeIf { it > 0 }?.let { stringResource(R.string.sample_rate) to "$it Hz" },
        local?.bitDepth?.let { stringResource(R.string.audio_bit_depth) to "$it bit" },
        local?.channelCount?.let { stringResource(R.string.audio_channels) to it.toString() },
        format?.bitrate?.takeIf { !isLocal && it > 0 }?.let { stringResource(R.string.bitrate) to "${it / 1000} kbps" },
        (local?.size ?: format?.contentLength)?.takeIf { it > 0 }?.let { stringResource(R.string.file_size) to Formatter.formatShortFileSize(context, it) },
    )
    Material3SettingsGroup(title = stringResource(R.string.audio_source_quality), items = listOf(
        Material3SettingsItem(title = { Text(label) }, description = { Column {
            Text(source)
            Spacer(Modifier.height(8.dp))
            rows.forEach { (key, value) -> Text("$key: $value") }
        } }, icon = painterResource(R.drawable.graphic_eq)),
    ))
    Text(stringResource(R.string.audio_source_note), Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
