package com.metrolist.music.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

data class CanvasSelection(val uri: Uri? = null, val videoId: String? = null, val startMs: Long = 0, val endMs: Long = 8_000)

internal fun canvasClip(startMs: Long, durationMs: Long): LongRange {
    require(durationMs > 0)
    val length = minOf(8_000L, durationMs)
    val start = startMs.coerceIn(0, durationMs - length)
    return start until start + length
}

class CanvasVideoStore(context: Context) {
    private val context = context.applicationContext
    internal val preferences = this.context.getSharedPreferences("sourcify_canvas", Context.MODE_PRIVATE)

    internal val clips = this.context.getSharedPreferences("sourcify_canvas_clips", Context.MODE_PRIVATE)
    internal val enabledPreferences = this.context.getSharedPreferences("sourcify_canvas_enabled", Context.MODE_PRIVATE)

    fun isEnabled(songId: String): Boolean = enabledPreferences.getBoolean(songId, true)

    fun setEnabled(songId: String, enabled: Boolean) {
        require(songId.isNotBlank())
        enabledPreferences.edit().putBoolean(songId, enabled).apply()
    }

    fun selection(songId: String): CanvasSelection? {
        val uri = get(songId)
        val json = clips.getString(songId, null)?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
        val videoId = json?.optString("videoId")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        if (uri == null && videoId == null) return null
        val start = json?.optLong("start", 0)?.coerceAtLeast(0)?.coerceAtMost(Long.MAX_VALUE - 8_000) ?: 0
        val end = (json?.optLong("end", start + 8_000) ?: (start + 8_000)).coerceIn(start + 1, start + 8_000)
        return CanvasSelection(uri, videoId, start, end)
    }

    fun saveClip(songId: String, selection: CanvasSelection) {
        require(songId.isNotBlank() && selection.startMs >= 0 && selection.endMs > selection.startMs && selection.endMs - selection.startMs <= 8_000)
        require((selection.uri != null) != (selection.videoId != null))
        if (selection.videoId != null) require(selection.videoId.matches(Regex("[A-Za-z0-9_-]{11}")))
        if (selection.uri != null) set(songId, selection.uri) else remove(songId)
        val json = org.json.JSONObject().put("start", selection.startMs).put("end", selection.endMs)
        selection.videoId?.let { json.put("videoId", it) }
        clips.edit().putString(songId, json.toString()).apply()
    }

    fun get(songId: String): Uri? = preferences.getString(songId, null)
        ?.let(Uri::parse)
        ?.takeIf { it.scheme == "content" }

    fun set(songId: String, uri: Uri) {
        require(songId.isNotBlank() && uri.scheme == "content")
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)
        require(mimeType == null || mimeType.startsWith("video/"))
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val previous = get(songId)
        preferences.edit().putString(songId, uri.toString()).apply()
        clips.edit().remove(songId).apply()
        if (previous != null && previous != uri) releaseIfUnused(previous)
    }

    fun remove(songId: String) {
        val previous = get(songId)
        preferences.edit().remove(songId).apply()
        clips.edit().remove(songId).apply()
        previous?.let(::releaseIfUnused)
    }

    private fun releaseIfUnused(uri: Uri) {
        if (preferences.all.values.none { it == uri.toString() }) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}

@Composable
fun rememberCanvasEnabled(songId: String?): Boolean {
    val context = LocalContext.current
    val store = remember(context) { CanvasVideoStore(context) }
    var enabled by remember(store, songId) { mutableStateOf(songId?.let(store::isEnabled) ?: false) }
    DisposableEffect(store, songId) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == songId) enabled = songId?.let(store::isEnabled) ?: false
        }
        store.enabledPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.enabledPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return enabled
}

@Composable
fun rememberCanvasVideoUri(songId: String?): Uri? {
    val context = LocalContext.current
    val store = remember(context) { CanvasVideoStore(context) }
    var uri by remember(store, songId) { mutableStateOf(songId?.let(store::get)) }
    DisposableEffect(store, songId) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == songId) uri = songId?.let(store::get)
        }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return uri
}

@Composable
fun rememberCanvasSelection(songId: String?): CanvasSelection? {
    val context = LocalContext.current
    val store = remember(context) { CanvasVideoStore(context) }
    var selection by remember(store, songId) { mutableStateOf(songId?.let(store::selection)) }
    DisposableEffect(store, songId) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == songId) selection = songId?.let(store::selection)
        }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        store.clips.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            store.preferences.unregisterOnSharedPreferenceChangeListener(listener)
            store.clips.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return selection
}

@Composable
fun rememberPlaybackCanvasSelection(songId: String?, automaticEnabled: Boolean = true): CanvasSelection? {
    val manual = rememberCanvasSelection(songId)
    val context = LocalContext.current
    val store = remember(context) { DownloadCompanionStore.get(context) }
    val revision by store.revision.collectAsState()
    val value by produceState<Triple<String?, CanvasSelection?, CanvasSelection?>?>(null, songId, manual, revision, automaticEnabled) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Triple(songId, manual, songId?.let { store.playbackSelection(it, manual, automaticEnabled) })
        }
    }
    if (manual == null && !automaticEnabled) return null
    return value?.takeIf { it.first == songId && it.second == manual }?.third ?: manual
}
