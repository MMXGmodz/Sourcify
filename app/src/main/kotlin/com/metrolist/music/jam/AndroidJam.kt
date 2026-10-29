package com.metrolist.music.jam

import android.os.Bundle
import com.metrolist.music.R
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.extensions.getCurrentQueueIndex
import com.metrolist.music.extensions.getQueueWindows
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.MusicService
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AndroidJam(private val service: MusicService) {
    private companion object { const val ENTRY_KEY = "sourcify.jam.entry" }
    private data class Entry(val id: String = UUID.randomUUID().toString(), val owner: String = "host")
    private val entries = mutableMapOf<String, Entry>()
    private val player get() = service.player
    val shuffleEnabled get() = player.shuffleModeEnabled
    val server = JamServer(
        asset = { service.assets.open("jam/$it").use { stream -> stream.readBytes() } },
        playback = ::snapshot,
        search = { query ->
            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrThrow().items
                .filterIsInstance<SongItem>().take(40).map { track(it.toMediaMetadata()) }
        },
        add = { item, owner ->
            val metadata = MediaMetadata(
                id = item.getString("id"), title = item.getString("title"),
                artists = listOf(MediaMetadata.Artist(null, item.getString("artist"))),
                duration = item.optInt("duration", -1), thumbnailUrl = item.optString("thumbnail").ifBlank { null },
            )
            val entry = Entry(owner = owner)
            entries[entry.id] = entry
            val mediaItem = tagged(metadata.toMediaItem(), entry.id)
            // Keep duplicate occurrences distinct and enqueue without replacing the host's current song.
            player.addMediaItem(mediaItem)
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) player.prepare()
        },
        remove = { id, owner ->
            index(id)?.let { if (entries[id]?.owner == owner) player.removeMediaItem(it) }
        },
        control = ::control,
    )

    fun start(name: String) {
        check(!service.listenTogetherManager.isInRoom) { service.getString(R.string.sourcify_jam_leave_online) }
        entries.clear()
        try { server.start(name) } catch (e: IllegalArgumentException) {
            throw IllegalStateException(service.getString(R.string.sourcify_jam_wifi_required), e)
        }
    }

    fun stop() { server.stop(); entries.clear() }

    fun control(action: String) {
        when (action) {
            "play" -> { if (player.playbackState == Player.STATE_IDLE) player.prepare(); player.play() }
            "pause" -> player.pause()
            "next" -> player.seekToNextMediaItem()
            "previous" -> player.seekToPreviousMediaItem()
        }
    }

    private fun entryId(item: MediaItem): String? = item.mediaMetadata.extras?.getString(ENTRY_KEY)
    private fun tagged(item: MediaItem, id: String): MediaItem = item.buildUpon().setMediaMetadata(
        item.mediaMetadata.buildUpon().setExtras(Bundle(item.mediaMetadata.extras ?: Bundle()).apply { putString(ENTRY_KEY, id) }).build(),
    ).build()
    private fun index(id: String): Int? = (0 until player.mediaItemCount).firstOrNull { entryId(player.getMediaItemAt(it)) == id }
    fun remove(id: String) { index(id)?.let(player::removeMediaItem) }
    fun move(id: String, offset: Int) {
        // Move by the displayed playback order, including shuffle, while Media3 stores physical indices.
        val windows = player.getQueueWindows()
        val from = windows.indexOfFirst { entryId(it.mediaItem) == id }
        if (from >= 0 && from + offset in windows.indices) {
            val first = index(id) ?: return
            val second = index(entryId(windows[from + offset].mediaItem) ?: return) ?: return
            player.moveMediaItem(first, second)
        }
    }

    private fun track(metadata: MediaMetadata) = JSONObject().put("id", metadata.id).put("title", metadata.title)
        .put("artist", metadata.artists.joinToString { it.name }).put("duration", metadata.duration.coerceAtLeast(0))
        .put("thumbnail", metadata.thumbnailUrl?.takeIf { it.startsWith("https://") } ?: "").put("kind", "song")

    fun snapshot(): JSONObject {
        val alive = mutableSetOf<String>()
        for (index in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(index)
            val id = entryId(item)
            if (id == null || id !in entries || !alive.add(id)) {
                val entry = Entry()
                entries[entry.id] = entry
                alive.add(entry.id)
                // Metadata-only replacement keeps playback intact and survives favorite/metadata edits.
                player.replaceMediaItem(index, tagged(item, entry.id))
            }
        }
        entries.keys.retainAll(alive)
        val windows = player.getQueueWindows()
        val queue = JSONArray()
        windows.forEach { window ->
            val item = window.mediaItem
            val metadata = item.metadata
            val entry = entries.getValue(entryId(item)!!)
            // Local songs are visible as titles only; never expose file URIs to LAN guests.
            val publicTrack = metadata?.let(::track) ?: JSONObject().put("id", item.mediaId)
                .put("title", item.mediaMetadata.title?.toString() ?: "Song").put("artist", "").put("duration", 0).put("thumbnail", "").put("kind", "song")
            if (!item.mediaId.matches(Regex("[A-Za-z0-9_-]{11}"))) publicTrack.put("id", "")
            queue.put(JSONObject().put("entryId", entry.id).put("id", publicTrack.getString("id"))
                .put("addedBy", entry.owner).put("track", publicTrack))
        }
        return JSONObject().put("queue", queue).put("queueIndex", if (windows.isEmpty()) -1 else player.getCurrentQueueIndex())
            .put("playback", JSONObject().put("id", player.currentMediaItem?.mediaId?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) } ?: JSONObject.NULL)
                .put("position", player.currentPosition.coerceAtLeast(0) / 1000.0)
                .put("duration", player.duration.coerceAtLeast(0) / 1000.0).put("paused", !player.playWhenReady)
                .put("loading", player.playbackState == Player.STATE_BUFFERING).put("at", System.currentTimeMillis()))
    }
}
