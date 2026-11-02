package com.metrolist.music.ui.screens.settings

import com.metrolist.music.ui.utils.backToMain

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.*
import com.metrolist.music.utils.*

@Composable
fun MediaQualitySettings(navController: NavController) {
    val context = LocalContext.current
    val (download, setDownload) = rememberPreference(AudioQualityKey, AudioQuality.AUTO.name)
    val (wifi, setWifi) = rememberPreference(StreamingWifiQualityKey, download)
    val (mobile, setMobile) = rememberPreference(StreamingMobileQualityKey, download)
    val (wifiHeight, setWifiHeight) = rememberPreference(CanvasWifiHeightKey, 720)
    val (mobileHeight, setMobileHeight) = rememberPreference(CanvasMobileHeightKey, 720)
    LaunchedEffect(Unit) {
        context.dataStore.edit { prefs ->
            val previous = readAudioQuality(prefs[AudioQualityKey]) ?: AudioQuality.AUTO
            if (prefs[AudioQualityKey] == "VERY_HIGH") prefs[AudioQualityKey] = previous.name
            if (prefs[StreamingWifiQualityKey] == null) prefs[StreamingWifiQualityKey] = previous.name
            if (prefs[StreamingMobileQualityKey] == null) prefs[StreamingMobileQualityKey] = previous.name
        }
    }
    MediaQualityContent(readAudioQuality(wifi) ?: readAudioQuality(download) ?: AudioQuality.AUTO, readAudioQuality(mobile) ?: readAudioQuality(download) ?: AudioQuality.AUTO,
        readAudioQuality(download) ?: AudioQuality.AUTO, normalizedCanvasHeight(wifiHeight), normalizedCanvasHeight(mobileHeight),
        { setWifi(it.name) }, { setMobile(it.name) }, { setDownload(it.name) }, setWifiHeight, setMobileHeight,
        { navController.navigateUp() }, { navController.navigate("settings/integrations") },
        LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding(), onBackToMain = navController::backToMain)
}

@Composable
internal fun MediaQualityContent(
    wifi: AudioQuality, mobile: AudioQuality, download: AudioQuality, wifiHeight: Int, mobileHeight: Int,
    onWifi: (AudioQuality) -> Unit, onMobile: (AudioQuality) -> Unit, onDownload: (AudioQuality) -> Unit,
    onWifiVideo: (Int) -> Unit, onMobileVideo: (Int) -> Unit, onBack: () -> Unit, onLossless: () -> Unit, bottom: Dp = 0.dp, onBackToMain: () -> Unit = onBack,
) {
    SettingsPage(stringResource(R.string.s8_media_quality), onBack, bottom, onBackToMain) {
        com.metrolist.music.ui.component.Material3SettingsGroup(items = listOf(
            com.metrolist.music.ui.component.Material3SettingsItem(
                icon = painterResource(R.drawable.device_phone),
                title = { Text(stringResource(R.string.s8_this_device)) },
                description = { Text(stringResource(R.string.s8_device_only)) },
            ),
        ))
        QualityNote(stringResource(R.string.s8_audio_note))
        com.metrolist.music.ui.component.Material3SettingsGroup(
            title = stringResource(R.string.s8_audio_streaming),
            items = listOf(
                AudioQualitySetting(stringResource(R.string.s8_wifi_stream), wifi, onWifi),
                AudioQualitySetting(stringResource(R.string.s8_mobile_stream), mobile, onMobile),
            ),
        )
        QualityNote(stringResource(R.string.s8_download_note))
        com.metrolist.music.ui.component.Material3SettingsGroup(
            title = stringResource(R.string.s8_download_quality),
            items = listOf(
                AudioQualitySetting(stringResource(R.string.s8_download_audio), download, onDownload),
                com.metrolist.music.ui.component.Material3SettingsItem(
                    icon = painterResource(R.drawable.graphic_eq),
                    title = { Text(stringResource(R.string.s8_lossless)) },
                    description = { Text(stringResource(R.string.s8_lossless_note)) },
                    onClick = onLossless,
                ),
            ),
        )
        QualityNote(stringResource(R.string.s8_video_note))
        com.metrolist.music.ui.component.Material3SettingsGroup(
            title = stringResource(R.string.s8_video_quality),
            items = listOf(
                VideoQualitySetting(stringResource(R.string.s8_wifi_stream), wifiHeight, onWifiVideo),
                VideoQualitySetting(stringResource(R.string.s8_mobile_stream), mobileHeight, onMobileVideo),
            ),
        )
    }
}

@Composable
private fun QualityNote(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.info), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun audioQualityName(value: AudioQuality): String = stringResource(when (value) {
    AudioQuality.AUTO -> R.string.audio_quality_auto
    AudioQuality.LOW -> R.string.audio_quality_low
    AudioQuality.HIGH -> R.string.audio_quality_high
})

@Composable
private fun audioQualityDescription(value: AudioQuality): String = stringResource(when (value) {
    AudioQuality.AUTO -> R.string.s8_quality_auto
    AudioQuality.LOW -> R.string.s8_quality_low
    AudioQuality.HIGH -> R.string.s8_quality_high
})

@Composable
private fun AudioQualitySetting(title: String, selected: AudioQuality, onSelect: (AudioQuality) -> Unit): com.metrolist.music.ui.component.Material3SettingsItem {
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) com.metrolist.music.ui.component.EnumDialog(
        title = title, current = selected, values = AudioQuality.entries.toList(),
        valueText = { audioQualityName(it) }, valueDescription = { audioQualityDescription(it) },
        onDismiss = { showDialog = false }, onSelect = { onSelect(it); showDialog = false },
    )
    return com.metrolist.music.ui.component.Material3SettingsItem(
        icon = painterResource(R.drawable.graphic_eq), title = { Text(title) },
        description = { Text(audioQualityName(selected)) }, onClick = { showDialog = true },
    )
}

@Composable
private fun videoQualityName(height: Int): String {
    val name = listOf(R.string.audio_quality_low, R.string.s8_quality_normal,
        R.string.audio_quality_high, R.string.s8_quality_very_high)[supportedCanvasHeights.indexOf(height).coerceAtLeast(0)]
    return stringResource(name) + " · " + stringResource(R.string.s8_video_height, height)
}

@Composable
private fun VideoQualitySetting(title: String, selected: Int, onSelect: (Int) -> Unit): com.metrolist.music.ui.component.Material3SettingsItem {
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) com.metrolist.music.ui.component.EnumDialog(
        title = title, current = selected, values = supportedCanvasHeights,
        valueText = { videoQualityName(it) },
        onDismiss = { showDialog = false }, onSelect = { onSelect(it); showDialog = false },
    )
    return com.metrolist.music.ui.component.Material3SettingsItem(
        icon = painterResource(R.drawable.slow_motion_video), title = { Text(title) },
        description = { Text(videoQualityName(selected)) }, onClick = { showDialog = true },
    )
}
