package com.metrolist.music.playback

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

internal sealed interface MusicVideoSelection {
    data class YouTube(val videoId: String) : MusicVideoSelection
    data class Local(val uri: Uri, val durationMs: Long) : MusicVideoSelection
}

internal fun musicVideoId(input: String): String? {
    val text = input.trim()
    val id = Regex("[A-Za-z0-9_-]{11}")
    if (id.matches(text)) return text
    val uri = Uri.parse(text)
    if (uri.scheme !in setOf("https", "http") || uri.userInfo != null || uri.port !in setOf(-1, 80, 443)) return null
    val candidate = when (uri.host?.lowercase(Locale.ROOT)) {
        "youtu.be", "www.youtu.be" -> uri.pathSegments.singleOrNull()
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" -> when {
            uri.path == "/watch" -> uri.getQueryParameter("v")
            uri.pathSegments.size == 2 && uri.pathSegments.first() in setOf("shorts", "embed", "live") -> uri.pathSegments[1]
            else -> null
        }
        else -> null
    }
    return candidate?.takeIf(id::matches)
}

internal class MusicVideoSelectionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("sourcify_music_video_selections", Context.MODE_PRIVATE)

    fun get(songId: String): MusicVideoSelection? = runCatching {
        val record = JSONObject(preferences.getString(songId, null) ?: return null)
        val source = when (record.getString("type")) {
            "youtube" -> MusicVideoSelection.YouTube(record.getString("id"))
            "local" -> MusicVideoSelection.Local(Uri.parse(record.getString("uri")), record.getLong("duration"))
            else -> return null
        }
        validate(source)
        source
    }.getOrNull()

    fun set(songId: String, selection: MusicVideoSelection?) {
        require(songId.isNotBlank())
        val edit = preferences.edit()
        if (selection == null) edit.remove(songId) else {
            validate(selection)
            val record = JSONObject()
            when (selection) {
                is MusicVideoSelection.YouTube -> record.put("type", "youtube").put("id", selection.videoId)
                is MusicVideoSelection.Local -> record.put("type", "local").put("uri", selection.uri.toString()).put("duration", selection.durationMs)
            }
            edit.putString(songId, record.toString())
        }
        if (!edit.commit()) throw IOException("Could not save music video selection")
    }

    private fun validate(selection: MusicVideoSelection) {
        when (selection) {
            is MusicVideoSelection.YouTube -> require(Regex("[A-Za-z0-9_-]{11}").matches(selection.videoId))
            is MusicVideoSelection.Local -> require(selection.uri.scheme == "content" && !selection.uri.authority.isNullOrBlank() && selection.durationMs > 0)
        }
    }
}

/** The selected provider file stays local; only its durable read grant is retained. */
internal fun localMusicVideoSelection(context: Context, uri: Uri): MusicVideoSelection.Local {
    require(uri.scheme == "content" && !uri.authority.isNullOrBlank())
    require(context.contentResolver.getType(uri)?.startsWith("video/") == true)
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes")
        require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes")
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        require(duration > 0)
        MusicVideoSelection.Local(uri, duration)
    } finally { retriever.release() }
}
