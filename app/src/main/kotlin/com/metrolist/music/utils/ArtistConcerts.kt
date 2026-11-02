package com.metrolist.music.utils

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import org.json.JSONObject
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay

internal data class ArtistConcert(val id: String, val title: String, val date: String, val place: String) {
 val url: String get() = "https://musicbrainz.org/event/$id"
}
internal object ArtistConcerts {
 private val client = HttpClient { expectSuccess = true; install(HttpTimeout) { requestTimeoutMillis = 15000 } }
 private val lock = Mutex()
 private val cache = mutableMapOf<String, Pair<Long, List<ArtistConcert>>>()
 private var lastRequest = 0L
 suspend fun find(artist: String): List<ArtistConcert> = withContext(Dispatchers.IO) { lock.withLock {
  val now = System.currentTimeMillis()
  cache[artist]?.takeIf { now - it.first < 60 * 60 * 1000 }?.let { return@withLock it.second }
  delay((1100 - (now - lastRequest)).coerceAtLeast(0))
  lastRequest = System.currentTimeMillis()
  val escaped = artist.replace("\\", "\\\\").replace("\"", "\\\"")
  val response = client.get("https://musicbrainz.org/ws/2/event/") {
   header("User-Agent", "Sourcify/13.8.0 (https://github.com/MMXGmodz/Sourcify)")
   parameter("query", "artist:\"$escaped\" AND begin:[${LocalDate.now()} TO *]")
   parameter("fmt", "json"); parameter("limit", 25)
  }.bodyAsText()
  parse(response, artist, LocalDate.now()).also { cache[artist] = now to it }
 } }
 fun parse(raw: String, artist: String, today: LocalDate): List<ArtistConcert> {
  val events = JSONObject(raw).optJSONArray("events") ?: return emptyList()
  return buildList {
   for (index in 0 until events.length()) {
    val event = events.optJSONObject(index) ?: continue
    if (event.optBoolean("cancelled")) continue
    val id = event.optString("id")
    if (!Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}").matches(id)) continue
    val date = event.optJSONObject("life-span")?.optString("begin").orEmpty()
    val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: continue
    if (day.isBefore(today)) continue
    val relations = event.optJSONArray("relations") ?: continue
    var matches = false
    var place = ""
    for (relationIndex in 0 until relations.length()) {
     val relation = relations.optJSONObject(relationIndex) ?: continue
     val performer = relation.optJSONObject("artist")?.optString("name")
     if (performer != null && normalizeArtistName(performer) == normalizeArtistName(artist)) matches = true
     relation.optJSONObject("place")?.optString("name")?.let { place = it }
    }
    val title = event.optString("name")
    if (matches && title.isNotBlank()) add(ArtistConcert(id, title, date, place))
   }
  }.distinctBy { it.id }.sortedBy { it.date }
 }
}
