package com.metrolist.music.ui.player

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.constants.*
import com.metrolist.music.playback.MusicService
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicVideoSettingsSheet(service: MusicService, onDismiss: () -> Unit) {
    val state by service.musicVideoState.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF242628), contentColor = Color.White) {
        MusicVideoSettingsContent(state.videoHeight)
    }
}

@Composable
internal fun MusicVideoSettingsContent(decodedHeight: Int) {
    val (quality, selectQuality) = rememberEnumPreference(MusicVideoQualityKey, MusicVideoQuality.AUTO)
    val (large, setLarge) = rememberPreference(MusicVideoLargeMiniPlayerKey, false)
    val (pip, setPip) = rememberPreference(MusicVideoPipEnabledKey, true)
    val context = LocalContext.current
    val pipSupported = !com.metrolist.music.BuildConfig.TV_INTERFACE && context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    var qualityPicker by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(stringResource(if (qualityPicker) R.string.s86_video_quality else R.string.s86_video_settings),
            Modifier.align(Alignment.CenterHorizontally).padding(24.dp), style = MaterialTheme.typography.titleMedium, color = Color.White)
        HorizontalDivider(color = Color.White.copy(alpha = .1f))
        if (qualityPicker) {
            Column(Modifier.selectableGroup()) {
                MusicVideoQuality.entries.forEach { choice ->
                    Row(Modifier.fillMaxWidth().selectable(selected = quality == choice, role = Role.RadioButton,
                        onClick = { selectQuality(choice); qualityPicker = false }).padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(videoQualityLabel(choice), color = Color.White)
                            if (choice == MusicVideoQuality.AUTO) Text(stringResource(R.string.s86_quality_auto_description),
                                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .65f))
                        }
                        RadioButton(selected = quality == choice, onClick = null)
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().clickable { qualityPicker = true }.padding(24.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(painterResource(R.drawable.s86_video_quality), null, tint = Color.White)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.s86_video_quality), color = Color.White)
                    Text(if (decodedHeight > 0) stringResource(R.string.s86_quality_summary, videoQualityLabel(quality), decodedHeight)
                        else videoQualityLabel(quality), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .65f))
                }
                Icon(painterResource(R.drawable.navigate_next), null, tint = Color.White)
            }
            VideoSettingToggle(R.drawable.fullscreen, R.string.s86_large_video, R.string.s86_large_video_description, large, setLarge)
            VideoSettingToggle(R.drawable.s86_pip, R.string.s86_pip, R.string.s86_pip_description, pip, setPip, pipSupported)
            Text(stringResource(if (pipSupported) R.string.s86_pip_note else R.string.s86_pip_unavailable),
                Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .6f))
            if (pipSupported) TextButton(onClick = {
                try { context.startActivity(Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS", Uri.parse("package:" + context.packageName))) }
                catch (_: android.content.ActivityNotFoundException) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
                }
            }, modifier = Modifier.padding(horizontal = 12.dp)) { Text(stringResource(R.string.s86_pip_android_settings)) }
        }
    }
}

@Composable
private fun videoQualityLabel(quality: MusicVideoQuality): String =
    if (quality == MusicVideoQuality.AUTO) stringResource(R.string.s86_quality_auto) else quality.maxHeight.toString() + "p"

@Composable
private fun VideoSettingToggle(icon: Int, title: Int, description: Int, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(painterResource(icon), null, tint = Color.White.copy(alpha = if (enabled) 1f else .4f))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), color = Color.White.copy(alpha = if (enabled) 1f else .4f))
            Spacer(Modifier.height(4.dp))
            Text(stringResource(description), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .65f))
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF21D56B), checkedThumbColor = Color.White,
                checkedBorderColor = Color.Transparent, uncheckedTrackColor = Color(0xFF606060),
                uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent))
    }
}
