package com.metrolist.music.ui.screens.settings

import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.*
import com.metrolist.music.R
import com.metrolist.music.constants.*
import com.metrolist.music.utils.*
import coil3.imageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.annotation.DelicateCoilApi
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Green = Color(0xFF1ED760)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReferencePage(title: String, nav: NavController, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF101010))) {
        CenterAlignedTopAppBar(title = { Text(title, style = MaterialTheme.typography.titleMedium) }, navigationIcon = {
            FilledIconButton(onClick = { nav.navigateUp() }, shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF292929), contentColor = Color.White)) {
                Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.cd_back))
            }
        }, colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFF101010), titleContentColor = Color.White))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp,
            bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
    }
}
@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { change(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = Color.White); Text(description, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.width(16.dp))
        Switch(checked, change, colors = SwitchDefaults.colors(checkedTrackColor = Green, checkedThumbColor = Color.White))
    }
}
@Composable
private fun SettingLink(text: String, action: () -> Unit) { TextButton(onClick = action) { Text(text, color = Color.White) } }

@Composable
fun ContentDisplaySettings(nav: NavController) {
    val (canvas, setCanvas) = rememberPreference(CanvasEnabledKey, true)
    val (auto, setAuto) = rememberPreference(AutomaticCanvasKey, true)
    val (wifi, setWifi) = rememberPreference(CanvasWifiOnlyKey, false)
    val (explicit, setExplicit) = rememberPreference(HideExplicitKey, false)
    val (videos, setVideos) = rememberPreference(HideVideoSongsKey, false)
    val (shorts, setShorts) = rememberPreference(HideYoutubeShortsKey, false)
    ReferencePage(stringResource(R.string.s9_content), nav) {
        Text(stringResource(R.string.s9_videos), color = Color.White, style = MaterialTheme.typography.titleLarge)
        SettingSwitch(stringResource(R.string.sourcify_canvas), stringResource(R.string.s9_canvas_desc), canvas, setCanvas)
        SettingSwitch(stringResource(R.string.s9_auto_canvas), stringResource(R.string.s9_auto_desc), auto, setAuto)
        SettingSwitch(stringResource(R.string.canvas_wifi_only), stringResource(R.string.canvas_wifi_only_description), wifi, setWifi)
        SettingSwitch(stringResource(R.string.s9_music_titles), stringResource(R.string.s9_music_desc), !videos) { setVideos(!it) }
        SettingSwitch(stringResource(R.string.s9_shorts), stringResource(R.string.s9_shorts_desc), !shorts) { setShorts(!it) }
        Text(stringResource(R.string.s9_audio_only), color = Color.LightGray)
        HorizontalDivider()
        Text(stringResource(R.string.s9_content_options), color = Color.White, style = MaterialTheme.typography.titleLarge)
        SettingSwitch(stringResource(R.string.s9_explicit), stringResource(R.string.s9_explicit_desc), !explicit) { setExplicit(!it) }
        SettingLink(stringResource(R.string.sourcify_canvas)) { nav.navigate("settings/canvas") }
        SettingLink(stringResource(R.string.ai_lyrics_translation)) { nav.navigate("settings/ai") }
        SettingLink(stringResource(R.string.s9_advanced_content)) { nav.navigate("settings/content/advanced") }
    }
}

@OptIn(ExperimentalCoilApi::class, DelicateCoilApi::class)
@Composable
fun DataOfflineSettings(nav: NavController) {
    val context = LocalContext.current
    val connection = LocalPlayerConnection.current
    val (mobile, setMobile) = rememberPreference(DownloadMobileAllowedKey, true)
    val (background, setBackground) = rememberPreference(DownloadBackgroundAllowedKey, true)
    val (wifiCanvas, setWifiCanvas) = rememberPreference(CanvasWifiOnlyKey, false)
    val util = if (connection != null) LocalDownloadUtil.current else null
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var refresh by remember { mutableIntStateOf(0) }
    var removeDialog by remember { mutableStateOf(false) }
    var cacheDialog by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf(false) }
    val snapshot by produceState<Pair<StorageUsage, Long>?>(null, connection, refresh) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                try {
                    value = withContext(Dispatchers.IO) {
                        val fs = StatFs(context.filesDir.absolutePath)
                        fun folderSize(name: String) = java.io.File(context.filesDir, name).walkTopDown().filter { it.isFile }.sumOf { it.length() }
                        val downloads = (connection?.service?.downloadCache?.cacheSpace ?: 0) + folderSize("download-extras") + folderSize("download-artwork")
                        val cache = (connection?.service?.playerCache?.cacheSpace ?: 0) + (context.imageLoader.diskCache?.size ?: 0) + CanvasVideoCache.bytesUsed(context)
                        val external = util?.downloads?.value?.keys.orEmpty().sumOf { DownloadStorage.get(context).record(it)?.size ?: 0 }
                        storageUsage(fs.totalBytes, fs.availableBytes, downloads, cache) to external
                    }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { value = null }
                delay(5000)
            }
        }
    }
    val usage = snapshot?.first
    fun bytes(value: Long) = Formatter.formatFileSize(context, value)
    ReferencePage(stringResource(R.string.s9_data), nav) {
        Text(stringResource(R.string.s9_downloads), color = Color.White, style = MaterialTheme.typography.titleLarge)
        SettingSwitch(stringResource(R.string.s9_mobile_downloads), stringResource(R.string.s9_mobile_desc), mobile, setMobile)
        SettingSwitch(stringResource(R.string.s9_background_downloads), stringResource(R.string.s9_background_desc), background, setBackground)
        SettingSwitch(stringResource(R.string.canvas_wifi_only), stringResource(R.string.canvas_wifi_only_description), wifiCanvas, setWifiCanvas)
        Text(stringResource(R.string.s9_audio_only), color = Color.LightGray)
        SettingLink(stringResource(R.string.s9_saved_songs)) { nav.navigate("auto_playlist/downloaded") }
        HorizontalDivider()
        Text(stringResource(R.string.s9_storage), color = Color.White, style = MaterialTheme.typography.titleLarge)
        usage?.let { data ->
            val parts = listOf(data.other to Color(0xFF0D78F2), data.downloads to Green, data.cache to Color.LightGray, data.free to Color.DarkGray)
            Row(Modifier.fillMaxWidth().height(12.dp)) { parts.filter { it.first > 0 }.forEach { (size, color) -> Box(Modifier.weight(size.toFloat() / data.total.coerceAtLeast(1).toFloat()).fillMaxHeight().background(color)) } }
            listOf(R.string.s9_other to data.other, R.string.s9_app_downloads to data.downloads, R.string.s9_playback_cache to data.cache, R.string.s9_free to data.free).forEach { (title, size) ->
                Text(stringResource(title) + " · " + bytes(size), color = Color.White)
            }
            Text(stringResource(R.string.s9_storage_scope), color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        }
        snapshot?.second?.takeIf { it > 0 }?.let { Text(stringResource(R.string.s9_external_downloads) + " · " + bytes(it), color = Color.White) }
        if (failure) Text(stringResource(R.string.s9_storage_failed), color = Color.White)
        StorageAction(stringResource(R.string.s9_remove_downloads), stringResource(R.string.s9_remove_downloads_desc),
            stringResource(R.string.s9_remove), connection != null && !busy) { removeDialog = true }
        StorageAction(stringResource(R.string.s9_clear_cache), stringResource(R.string.s9_cache_scope),
            stringResource(R.string.s9_clear), !busy) { cacheDialog = true }
        SettingLink(stringResource(R.string.s9_manage_storage)) { nav.navigate("settings/storage/advanced") }
        SettingLink(stringResource(R.string.s8_media_quality)) { nav.navigate("settings/media_quality") }
    }
    if (removeDialog) com.metrolist.music.ui.component.ActionPromptDialog(
        title = stringResource(R.string.clear_all_downloads), onDismiss = { removeDialog = false }, onCancel = { removeDialog = false },
        content = { Text(stringResource(R.string.clear_downloads_dialog)) }, onConfirm = {
            removeDialog = false
            runCatching { androidx.media3.exoplayer.offline.DownloadService.sendRemoveAllDownloads(context,
                com.metrolist.music.playback.ExoDownloadService::class.java, false) }.onFailure { failure = true }
            refresh++
        })
    if (cacheDialog) com.metrolist.music.ui.component.ActionPromptDialog(
        title = stringResource(R.string.s9_clear_cache), onDismiss = { cacheDialog = false }, onCancel = { cacheDialog = false },
        content = { Text(stringResource(R.string.s9_cache_scope)) }, onConfirm = {
            cacheDialog = false; busy = true; failure = false
            scope.launch {
                try { withContext(Dispatchers.IO) {
                    connection?.service?.playerCache?.let { cache -> cache.keys.toList().forEach(cache::removeResource) }
                    context.imageLoader.diskCache?.clear(); context.imageLoader.memoryCache?.clear(); CanvasVideoCache.clear()
                } } catch (error: CancellationException) { throw error }
                catch (_: Exception) { failure = true }
                finally { busy = false; refresh++ }
            }
        })
}

@Composable
private fun StorageAction(title: String, description: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = Color.White); Text(description, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.width(16.dp))
        OutlinedButton(onClick = onClick, enabled = enabled) { Text(label, color = Color.White) }
    }
}
