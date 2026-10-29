package com.metrolist.music.ui.player

import com.metrolist.music.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

internal data class MusicBrainzSongKey(val title: String, val artists: List<String>, val durationSeconds: Int)

internal sealed interface MusicBrainzCreditsResult {
    data class Found(val recordingId: String, val credits: SongCredits) : MusicBrainzCreditsResult {
        val sourceUrl: String get() = "https://musicbrainz.org/recording/$recordingId"
    }
    data object Unavailable : MusicBrainzCreditsResult
    data object Error : MusicBrainzCreditsResult
}

internal data class MusicBrainzResponse(val status: Int, val body: String, val retryAfterSeconds: Long? = null)

private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject.array(key: String): List<JsonElement> = (get(key) as? JsonArray).orEmpty()
private fun normalizedCreditName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

private fun matchingMusicBrainzRecording(song: MusicBrainzSongKey, recording: JsonObject): Boolean {
    if (song.durationSeconds <= 0 || song.title.isBlank() || song.artists.isEmpty()) return false
    if ((recording["video"] as? JsonPrimitive)?.booleanOrNull == true) return false
    val length = (recording["length"] as? JsonPrimitive)?.longOrNull ?: return false
    if (abs(length - song.durationSeconds * 1000L) > 2000L) return false
    if (normalizedCreditName(recording.text("title").orEmpty()) != normalizedCreditName(song.title)) return false
    val expectedArtists = song.artists.map(::normalizedCreditName).filter(String::isNotBlank).toSet()
    val actualArtists = recording.array("artist-credit").mapNotNull { credit ->
        (credit as? JsonObject)?.let { it.text("name") ?: (it["artist"] as? JsonObject)?.text("name") }
    }.map(::normalizedCreditName).filter(String::isNotBlank).toSet()
    return expectedArtists.isNotEmpty() && actualArtists == expectedArtists
}

internal fun selectMusicBrainzRecording(song: MusicBrainzSongKey, response: String): String? {
    val root = Json.parseToJsonElement(response) as? JsonObject ?: return null
    return root.array("recordings").take(10).mapNotNull { it as? JsonObject }
        .filter { matchingMusicBrainzRecording(song, it) }
        .mapNotNull { it.text("id")?.takeIf(::isMusicBrainzId) }.distinct().singleOrNull()
}

private fun isMusicBrainzId(value: String): Boolean = value.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))

internal fun parseMusicBrainzCredits(response: String, recordingId: String): SongCredits {
    val root = Json.parseToJsonElement(response) as? JsonObject ?: return SongCredits(emptyList(), emptyList())
    if (root.text("id") != recordingId) return SongCredits(emptyList(), emptyList())
    val entries = mutableListOf<SongCredit>()
    fun addRelations(relations: List<JsonElement>, group: CreditGroup) {
        relations.take(200).forEach { relation ->
            val obj = relation as? JsonObject ?: return@forEach
            val role = when (obj.text("type")) {
                "composer" -> "Komponist*in".takeIf { group == CreditGroup.COMPOSITION }
                "lyricist" -> "Texter*in".takeIf { group == CreditGroup.COMPOSITION }
                "writer" -> "Songwriting".takeIf { group == CreditGroup.COMPOSITION }
                "producer" -> "Produzent*in".takeIf { group == CreditGroup.PRODUCTION }
                "mix" -> "Mixing Engineer".takeIf { group == CreditGroup.PRODUCTION }
                "mastering" -> "Mastering Engineer".takeIf { group == CreditGroup.PRODUCTION }
                "recording" -> "Recording Engineer".takeIf { group == CreditGroup.PRODUCTION }
                "engineer" -> "Engineer".takeIf { group == CreditGroup.PRODUCTION }
                else -> null
            } ?: return@forEach
            val artist = obj["artist"] as? JsonObject ?: return@forEach
            val name = (obj.text("target-credit")?.takeIf(String::isNotBlank) ?: artist.text("name"))
                ?.trim()?.take(300)?.takeIf(String::isNotEmpty) ?: return@forEach
            entries += SongCredit(name, role, group)
        }
    }
    val relations = root.array("relations")
    addRelations(relations, CreditGroup.PRODUCTION)
    relations.take(200).mapNotNull { it as? JsonObject }
        .filter { it.text("type") == "performance" }
        .mapNotNull { it["work"] as? JsonObject }
        .forEach { addRelations(it.array("relations"), CreditGroup.COMPOSITION) }
    return SongCredits(entries.distinct().take(120), emptyList())
}

internal class MusicBrainzCreditsRepository(
    private val request: suspend (String) -> MusicBrainzResponse = MusicBrainzNetwork::get,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private data class Cached(val value: MusicBrainzCreditsResult, val expiresAt: Long)
    private val cache = LinkedHashMap<MusicBrainzSongKey, Cached>()
    private val mutex = Mutex()
    private var nextRequestAt = 0L

    suspend fun load(song: MusicBrainzSongKey, force: Boolean = false): MusicBrainzCreditsResult = mutex.withLock {
        cache[song]?.takeIf { it.expiresAt > nowMillis() && !force }?.let { return@withLock it.value }
        val result = try {
            withTimeout(18_000L) {
                if (song.title.isBlank() || song.artists.isEmpty() || song.durationSeconds <= 0) return@withTimeout MusicBrainzCreditsResult.Unavailable
                fun quoted(value: String) = "\"${value.take(300).replace("\\", "\\\\").replace("\"", "\\\"")}\""
                val query = "recording:${quoted(song.title)} AND artist:${quoted(song.artists.first())}"
                val searchUrl = "https://musicbrainz.org/ws/2/recording/".toHttpUrl().newBuilder()
                    .addQueryParameter("query", query).addQueryParameter("limit", "10").addQueryParameter("fmt", "json").build().toString()
                val id = selectMusicBrainzRecording(song, fetch(searchUrl)) ?: return@withTimeout MusicBrainzCreditsResult.Unavailable
                val detailUrl = "https://musicbrainz.org/ws/2/recording/$id".toHttpUrl().newBuilder()
                    .addQueryParameter("inc", "artist-credits+artist-rels+work-rels+work-level-rels")
                    .addQueryParameter("fmt", "json").build().toString()
                val detail = fetch(detailUrl)
                val recording = Json.parseToJsonElement(detail) as? JsonObject
                if (recording == null || recording.text("id") != id || !matchingMusicBrainzRecording(song, recording)) {
                    MusicBrainzCreditsResult.Unavailable
                } else MusicBrainzCreditsResult.Found(id, parseMusicBrainzCredits(detail, id))
            }
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            MusicBrainzCreditsResult.Error
        }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { MusicBrainzCreditsResult.Error }
        val ttl = if (result == MusicBrainzCreditsResult.Error) 30_000L else 24 * 60 * 60_000L
        if (cache.size >= 48) cache.remove(cache.keys.first())
        cache[song] = Cached(result, nowMillis() + ttl)
        result
    }

    private suspend fun fetch(url: String): String {
        val wait = (nextRequestAt - nowMillis()).coerceAtLeast(0L)
        if (wait > 0L) pause(wait)
        nextRequestAt = nowMillis() + 1100L
        val response = request(url)
        if (response.status == 429 || response.status == 503) {
            nextRequestAt = maxOf(nextRequestAt, nowMillis() + (response.retryAfterSeconds ?: 5L).coerceAtLeast(1L) * 1000L)
        }
        if (response.status !in 200..299 || response.body.toByteArray(Charsets.UTF_8).size > 1_048_576) throw IOException("MusicBrainz request unavailable")
        return response.body
    }

    companion object { val shared by lazy { MusicBrainzCreditsRepository() } }
}

private object MusicBrainzNetwork {
    private val client by lazy { OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS).build() }

    suspend fun get(url: String): MusicBrainzResponse = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).header("Accept", "application/json")
            .header("User-Agent", "Sourcify/${BuildConfig.VERSION_NAME} (https://github.com/MMXGmodz/Sourcify)").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val value = response.use {
                        val body = it.body
                        val source = body.source()
                        if (body.contentLength() > 1_048_576 || source.request(1_048_577)) throw IOException("MusicBrainz response too large")
                        MusicBrainzResponse(it.code, source.readUtf8(), it.header("Retry-After")?.toLongOrNull()?.coerceIn(1L, 86400L))
                    }
                    if (continuation.isActive) continuation.resume(value)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }
}

internal fun mergeMusicBrainzCredits(primary: SongCredits, extra: MusicBrainzCreditsResult?): SongCredits {
    val confirmed = (extra as? MusicBrainzCreditsResult.Found)?.credits ?: return primary
    val missingGroups = CreditGroup.entries.filter { group -> primary.entries.none { it.group == group } }
    return primary.copy(entries = (primary.entries + confirmed.entries.filter { it.group in missingGroups }).distinct())
}
