package com.metrolist.music.ui.player

import android.util.LruCache
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.MediaInfo
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.*

internal enum class CreditGroup { COMPOSITION, PRODUCTION }
internal data class SongCredit(val name: String, val role: String, val group: CreditGroup)
internal data class SongCredits(val entries: List<SongCredit>, val sources: List<String>)

internal fun parseSongCredits(description: String?): SongCredits {
    val roles = mapOf(
        "composer" to ("Komponist*in" to CreditGroup.COMPOSITION),
        "lyricist" to ("Texter*in" to CreditGroup.COMPOSITION),
        "writer" to ("Songwriting" to CreditGroup.COMPOSITION),
        "songwriter" to ("Songwriting" to CreditGroup.COMPOSITION),
        "composer lyricist" to ("Komposition · Songtext" to CreditGroup.COMPOSITION),
        "producer" to ("Produzent*in" to CreditGroup.PRODUCTION),
        "mixing engineer" to ("Mixing Engineer" to CreditGroup.PRODUCTION),
        "mastering engineer" to ("Mastering Engineer" to CreditGroup.PRODUCTION),
        "recording engineer" to ("Recording Engineer" to CreditGroup.PRODUCTION),
    )
    val credits = mutableListOf<SongCredit>()
    val sources = mutableListOf<String>()
    description.orEmpty().take(64_000).lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.startsWith("℗") || line.startsWith("©")) sources += line.take(500)
        val label = line.substringBefore(':', "").lowercase().replace(',', ' ').replace(Regex("\\s+"), " ").trim()
        val role = roles[label] ?: return@forEach
        val name = line.substringAfter(':', "").trim().take(300)
        if (name.isNotEmpty()) credits += SongCredit(name, role.first, role.second)
    }
    return SongCredits(credits.distinct().take(120), sources.distinct().take(20))
}

internal data class PlayerContextData(val artist: ArtistPage?, val media: MediaInfo?)
internal object PlayerContextRepository {
    private data class Entry(val value: PlayerContextData, val timestamp: Long)
    private val tracks = LruCache<String, Entry>(24)
    private val artists = LruCache<String, ArtistPage>(16)
    suspend fun load(song: MediaMetadata, force: Boolean = false, context: android.content.Context? = null): PlayerContextData = coroutineScope {
        tracks.get(song.id)?.takeIf { !force && System.currentTimeMillis() - it.timestamp < 600_000 }?.let { return@coroutineScope it.value }
        val online = context?.let { com.metrolist.music.playback.hasDownloadInternet(it) } ?: true
        val saved = context?.let { com.metrolist.music.utils.DownloadCompanionStore.get(it) }
        val artistId = song.artists.firstOrNull()?.id
        val artist = async(Dispatchers.IO) {
            if (artistId == null || !online) null else artists.get(artistId)?.takeIf { !force } ?: run {
                val value = withTimeoutOrNull(15_000) { YouTube.artist(artistId).getOrNull() }
                value?.also { artists.put(artistId, it) }
            }
        }
        val media = async(Dispatchers.IO) {
            val previous = saved?.info(song.id)
            if (previous != null && !force || !online) previous
            else withTimeoutOrNull(15_000) { YouTube.getMediaInfo(song.id).getOrNull() }?.also { saved?.saveInfo(song.id, it) } ?: previous
        }
        val value = PlayerContextData(artist.await(), media.await())
        if (value.artist != null && value.media != null) tracks.put(song.id, Entry(value, System.currentTimeMillis()))
        value
    }
}

internal fun previewCreditRows(artists: List<String>, credits: SongCredits, mainRole: String): List<Pair<String, String>> {
    val rows = linkedMapOf<String, Pair<String, MutableList<String>>>()
    fun add(name: String, role: String) {
        val key = name.trim().lowercase(java.util.Locale.ROOT)
        val row = rows.getOrPut(key) { name to mutableListOf() }
        if (role !in row.second) row.second += role
    }
    artists.forEach { add(it, mainRole) }
    credits.entries.forEach { add(it.name, it.role) }
    return rows.values.map { it.first to it.second.joinToString(" · ") }
}
