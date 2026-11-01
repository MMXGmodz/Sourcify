package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
        LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding())
}

private val QualityGreen = Color(0xFF1ED760)
private val QualityMuted = Color(0xFFAAAAAA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaQualityContent(wifi: AudioQuality, mobile: AudioQuality, download: AudioQuality, wifiHeight: Int, mobileHeight: Int,
    onWifi: (AudioQuality) -> Unit, onMobile: (AudioQuality) -> Unit, onDownload: (AudioQuality) -> Unit,
    onWifiVideo: (Int) -> Unit, onMobileVideo: (Int) -> Unit, onBack: () -> Unit, onLossless: () -> Unit, bottom: Dp = 0.dp) {
    Column(Modifier.fillMaxSize().background(Color(0xFF101010))) {
        CenterAlignedTopAppBar(title = { Text(stringResource(R.string.s8_media_quality), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                FilledIconButton(onClick = onBack, shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF292929), contentColor = Color.White)) {
                    Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.cd_back))
                }
            }, colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFF101010), titleContentColor = Color.White))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.device_phone), null, tint = QualityGreen)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.s8_this_device), color = QualityGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.s8_device_only), color = QualityMuted)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = bottom + 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            QualitySection(stringResource(R.string.s8_audio_streaming))
            QualityNote(stringResource(R.string.s8_audio_note))
            AudioQualityChoices(stringResource(R.string.s8_wifi_stream), wifi, onWifi)
            AudioQualityChoices(stringResource(R.string.s8_mobile_stream), mobile, onMobile)
            HorizontalDivider(color = Color(0xFF292929))
            QualitySection(stringResource(R.string.s8_download_quality))
            QualityNote(stringResource(R.string.s8_download_note))
            AudioQualityChoices(stringResource(R.string.s8_download_audio), download, onDownload)
            Row(Modifier.fillMaxWidth().clickable(onClick = onLossless).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.s8_lossless), color = Color.White)
                    Text(stringResource(R.string.s8_lossless_note), color = QualityMuted, style = MaterialTheme.typography.bodyMedium)
                }
                Icon(painterResource(R.drawable.arrow_forward), stringResource(R.string.integrations), tint = QualityGreen)
            }
            HorizontalDivider(color = Color(0xFF292929))
            QualitySection(stringResource(R.string.s8_video_quality))
            QualityNote(stringResource(R.string.s8_video_note))
            VideoQualityChoices(stringResource(R.string.s8_wifi_stream), wifiHeight, onWifiVideo)
            VideoQualityChoices(stringResource(R.string.s8_mobile_stream), mobileHeight, onMobileVideo)
        }
    }
}

@Composable
private fun QualitySection(title: String) { Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }

@Composable
private fun QualityNote(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.info), null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AudioQualityChoices(title: String, selected: AudioQuality, onSelect: (AudioQuality) -> Unit) {
    Column {
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
        AudioQuality.entries.forEach { value ->
            val name = when (value) { AudioQuality.AUTO -> R.string.audio_quality_auto; AudioQuality.LOW -> R.string.audio_quality_low; AudioQuality.HIGH -> R.string.audio_quality_high }
            val description = when (value) { AudioQuality.AUTO -> R.string.s8_quality_auto; AudioQuality.LOW -> R.string.s8_quality_low; AudioQuality.HIGH -> R.string.s8_quality_high }
            QualityOption(stringResource(name), stringResource(description), value == selected) { onSelect(value) }
        }
    }
}

@Composable
private fun VideoQualityChoices(title: String, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
        supportedCanvasHeights.forEachIndexed { index, height ->
            val name = listOf(R.string.audio_quality_low, R.string.s8_quality_normal, R.string.audio_quality_high, R.string.s8_quality_very_high)[index]
            QualityOption(stringResource(name), stringResource(R.string.s8_video_height, height), height == selected) { onSelect(height) }
        }
    }
}

@Composable
private fun QualityOption(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White)
            Text(description, color = QualityMuted, style = MaterialTheme.typography.bodyMedium)
        }
        if (selected) Icon(painterResource(R.drawable.check), null, tint = QualityGreen, modifier = Modifier.size(24.dp))
    }
}
