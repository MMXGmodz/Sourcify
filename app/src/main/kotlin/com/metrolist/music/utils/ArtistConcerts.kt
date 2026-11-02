package com.metrolist.music.utils

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.Year
import java.time.YearMonth

internal data class ArtistConcert(
    val id: String,
    val title: String,
    val date: String,
    val place: String,
    val city: String = "",
    val time: String = "",
) {
    val url: String get() = "https://musicbrainz.org/event/$id"
    val day: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()
}

internal object ArtistConcerts {
    private val client = HttpClient { expectSuccess = true; install(HttpTimeout) { requestTimeoutMillis = 15000 } }
    private val lock = Mutex()
    private val cache = mutableMapOf<String, Pair<Long, List<ArtistConcert>>>()
    private var lastRequest = 0L
    private val eventId = Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")

    suspend fun find(artist: String): List<ArtistConcert> = withContext(Dispatchers.IO) {
        if (artist.isBlank()) return@withContext emptyList()
        lock.withLock {
            val today = LocalDate.now()
            val key = "${normalizeArtistName(artist)}:$today"
            val now = System.currentTimeMillis()
            cache[key]?.takeIf { now - it.first < 60 * 60 * 1000 }?.let { return@withLock it.second }
            val escaped = artist.replace("\\", "\\\\").replace("\"", "\\\"")
            throttle()
            val response = client.get("https://musicbrainz.org/ws/2/event/") {
                header("User-Agent", "Sourcify (https://github.com/MMXGmodz/Sourcify)")
                parameter("query", "artist:\"$escaped\" AND begin:[$today TO *]")
                parameter("fmt", "json")
                parameter("limit", 25)
            }.bodyAsText()
            val events = parse(response, artist, today).toMutableList()
            // Search omits time and venue area; bound lookups to keep provider traffic small.
            withTimeoutOrNull(8000) {
                for (event in events.take(3)) {
                    try {
                        throttle()
                        val detail = client.get(event.url.replace("musicbrainz.org/event/", "musicbrainz.org/ws/2/event/")) {
                            header("User-Agent", "Sourcify (https://github.com/MMXGmodz/Sourcify)")
                            parameter("inc", "artist-rels+place-rels")
                            parameter("fmt", "json")
                        }.bodyAsText()
                        val index = events.indexOfFirst { it.id == event.id }
                        val verified = parseEvent(JSONObject(detail), artist, today)
                        if (verified == null) events.removeAt(index) else events[index] = verified
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Keep the verified search result when optional details are unavailable.
                    }
                }
            }
            cache.entries.removeAll { now - it.value.first >= 60 * 60 * 1000 }
            if (cache.size >= 32) cache.remove(cache.keys.first())
            events.sortedBy { it.date }.also { cache[key] = System.currentTimeMillis() to it }
        }
    }

    private suspend fun throttle() {
        delay((1100 - (System.currentTimeMillis() - lastRequest)).coerceAtLeast(0))
        lastRequest = System.currentTimeMillis()
    }

    fun parse(raw: String, artist: String, today: LocalDate): List<ArtistConcert> {
        val events = JSONObject(raw).optJSONArray("events") ?: return emptyList()
        return (0 until events.length()).mapNotNull { index ->
            events.optJSONObject(index)?.let { parseEvent(it, artist, today) }
        }.distinctBy { it.id }.sortedBy { it.date }
    }

    private fun parseEvent(event: JSONObject, artist: String, today: LocalDate): ArtistConcert? {
        if (event.optBoolean("cancelled")) return null
        val id = event.text("id").takeIf { eventId.matches(it) } ?: return null
        val date = event.optJSONObject("life-span")?.text("begin").orEmpty()
        val earliest = runCatching {
            when (date.length) {
                4 -> Year.parse(date).atDay(1)
                7 -> YearMonth.parse(date).atDay(1)
                10 -> LocalDate.parse(date)
                else -> null
            }
        }.getOrNull() ?: return null
        if (earliest.isBefore(today)) return null
        val relations = event.optJSONArray("relations") ?: return null
        var matches = false
        var place = ""
        var city = ""
        for (index in 0 until relations.length()) {
            val relation = relations.optJSONObject(index) ?: continue
            val performer = relation.optJSONObject("artist")?.text("name").orEmpty()
            val role = relation.text("type")
            if (role in setOf("", "main performer", "performer", "guest performer", "support act") &&
                performer.isNotBlank() && normalizeArtistName(performer) == normalizeArtistName(artist)) matches = true
            if (relation.text("type") in setOf("", "held at")) relation.optJSONObject("place")?.let { venue ->
                place = venue.text("name")
                city = venue.optJSONObject("area")?.takeIf { it.text("type") in setOf("City", "Municipality") }?.text("name").orEmpty()
            }
        }
        val title = event.text("name")
        if (!matches || title.isBlank()) return null
        val time = runCatching { LocalTime.parse(event.text("time")).toString().take(5) }.getOrDefault("")
        return ArtistConcert(id, title, date, place, city, time)
    }

    private fun JSONObject.text(key: String) = if (isNull(key)) "" else optString(key).trim()
}

internal fun concertProviderArtistName(
    artistId: String?,
    fallback: String,
    cachedArtist: com.metrolist.music.db.entities.ArtistEntity?,
    aliases: Map<String, String> = ArtistNameAliases.aliases.value,
): String {
    val matchingArtist = cachedArtist?.takeIf { it.id == artistId }
    val providerTitle = matchingArtist?.cachedPageJson?.let { raw ->
        runCatching { com.metrolist.music.db.entities.deserializeArtistPage(raw).artist }.getOrNull()
            ?.takeIf { it.id == artistId || it.id == matchingArtist.channelId }?.title
    }
    if (!providerTitle.isNullOrBlank()) return providerTitle
    val knownAlias = artistId?.let { aliases[it] == fallback } == true ||
        matchingArtist?.channelId?.let { aliases[it] == fallback } == true
    return if (knownAlias) "" else fallback
}
