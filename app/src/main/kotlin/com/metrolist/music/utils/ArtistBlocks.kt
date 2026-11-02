package com.metrolist.music.utils

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.extensions.metadata
import kotlinx.coroutines.flow.map
import org.json.JSONObject

object ArtistBlocks {
 val key = stringPreferencesKey("blockedArtistIds")
 fun decode(raw: String): Map<String, String> = runCatching {
  val json = JSONObject(raw)
  json.keys().asSequence().filter { it.isNotBlank() }.associateWith { json.getString(it) }
 }.getOrDefault(emptyMap())
 fun encode(values: Map<String, String>) = JSONObject(values).toString()
 fun observe(context: Context) = context.dataStore.data.map { decode(it[key] ?: "{}") }
 fun snapshot(context: Context) = decode(context.dataStore.get(key, "{}"))
 suspend fun set(context: Context, id: String, name: String, blocked: Boolean) {
  if (id.isBlank()) return
  context.safeDataStoreEdit { prefs ->
   val values = decode(prefs[key] ?: "{}").toMutableMap()
   if (blocked) values[id] = name else values.remove(id)
   prefs[key] = encode(values)
  }
 }
 fun matches(blocked: Map<String, String>, artists: List<MediaMetadata.Artist>): Boolean = artists.any { artist ->
  if (!artist.id.isNullOrBlank()) artist.id in blocked
  else artist.name.isNotBlank() && blocked.values.any { it.isNotBlank() && normalizeArtistName(it) == normalizeArtistName(artist.name) }
 }
}

internal fun historySeedIds(top: List<String>, recent: List<String>, liked: List<String>, limit: Int): List<String> = buildList {
 for (index in 0 until maxOf(top.size, recent.size)) { top.getOrNull(index)?.let(::add); recent.getOrNull(index)?.let(::add) }
 addAll(liked)
}.filter(String::isNotBlank).distinct().take(limit.coerceAtLeast(0))

internal fun normalizeArtistName(name: String): String = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
 .replace(Regex("\\p{M}+"), "").trim().lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"), " ")

internal fun pruneBlockedTracks(player: androidx.media3.common.Player, blocked: Map<String, String>) {
 for (index in player.mediaItemCount - 1 downTo 0) {
  if (ArtistBlocks.matches(blocked, player.getMediaItemAt(index).metadata?.artists.orEmpty())) player.removeMediaItem(index)
 }
}

internal fun artistFavoriteSongs(songs: List<com.metrolist.music.db.entities.Song>, artistId: String): List<com.metrolist.music.db.entities.Song> =
 songs.filter { it.song.liked && it.artists.any { artist -> artist.id == artistId } }

internal fun stopBlockedAuxiliary(player: androidx.media3.common.Player?, blocked: Map<String, String>): Boolean {
 if (player == null || !ArtistBlocks.matches(blocked, player.currentMediaItem?.metadata?.artists.orEmpty())) return false
 player.playWhenReady = false
 player.stop()
 player.clearMediaItems()
 return true
}
