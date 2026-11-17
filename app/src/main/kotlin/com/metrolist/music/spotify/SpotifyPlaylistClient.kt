package com.metrolist.music.spotify

import com.metrolist.music.utils.PLAYLIST_IMPORT_MAX_BYTES
import com.metrolist.music.utils.PLAYLIST_IMPORT_MAX_TRACKS
import com.metrolist.music.utils.PlaylistImportCollection
import com.metrolist.music.utils.PlaylistImportTrack
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class SpotifyPlaylistClient(
    private val tokens: SpotifyTokens,
    httpClient: OkHttpClient = OkHttpClient(),
) {
    private val client = httpClient.newBuilder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS).build()

    suspend fun load(id: String): PlaylistImportCollection {
        if (!Regex("[A-Za-z0-9]{22}").matches(id)) fail(SpotifyImportFailure.INVALID)
        val path = "https://api.spotify.com/v1/playlists/$id"
        val head = getJson(path)
        val title = head.text("name")?.takeIf { it.isNotBlank() && it.length <= 500 }
            ?: fail(SpotifyImportFailure.INVALID)
        val legacy = !head.containsKey("items") && head["tracks"] is JsonObject
        var page = (if (legacy) head["tracks"] else head["items"]) as? JsonObject
            ?: fail(SpotifyImportFailure.ACCESS_DENIED)
        val endpoint = "$path/${if (legacy) "tracks" else "items"}"
        val tracks = mutableListOf<PlaylistImportTrack>()
        var consumed = 0
        var skipped = 0
        var expectedTotal: Int? = null
        while (true) {
            currentCoroutineContext().ensureActive()
            val total = page.number("total") ?: fail(SpotifyImportFailure.INVALID)
            if (total > PLAYLIST_IMPORT_MAX_TRACKS) fail(SpotifyImportFailure.LIMIT)
            if (total < 0 || (expectedTotal != null && total != expectedTotal) || page.number("offset") != consumed) {
                fail(SpotifyImportFailure.INVALID)
            }
            expectedTotal = total
            val rows = page["items"] as? JsonArray ?: fail(SpotifyImportFailure.INVALID)
            if (consumed + rows.size > PLAYLIST_IMPORT_MAX_TRACKS) fail(SpotifyImportFailure.LIMIT)
            if (consumed + rows.size > total) fail(SpotifyImportFailure.INVALID)
            rows.forEach { row ->
                currentCoroutineContext().ensureActive()
                val track = when (row) {
                    JsonNull -> null
                    is JsonObject -> parseTrack(row)
                    else -> fail(SpotifyImportFailure.INVALID)
                }
                if (track == null) skipped++ else tracks.add(track)
            }
            consumed += rows.size
            // Construct offsets ourselves: never send credentials to a response-provided URL.
            val next = page["next"] ?: fail(SpotifyImportFailure.INVALID)
            val hasNext = when (next) {
                JsonNull -> false
                is JsonPrimitive -> if (next.isString && next.content.isNotBlank()) true else fail(SpotifyImportFailure.INVALID)
                else -> fail(SpotifyImportFailure.INVALID)
            }
            if (!hasNext) {
                if (consumed != total) fail(SpotifyImportFailure.INVALID)
                break
            }
            if (consumed >= PLAYLIST_IMPORT_MAX_TRACKS) fail(SpotifyImportFailure.LIMIT)
            if (rows.isEmpty() || consumed >= total) fail(SpotifyImportFailure.INVALID)
            page = getJson("$endpoint?offset=$consumed&limit=50")
        }
        return PlaylistImportCollection(title, tracks, skippedCount = skipped)
    }

    private fun parseTrack(row: JsonObject): PlaylistImportTrack? {
        if (row.flag("is_local") == true) return null
        val value = when {
            row.containsKey("item") -> row["item"]
            row.containsKey("track") -> row["track"]
            else -> fail(SpotifyImportFailure.INVALID)
        }
        if (value == JsonNull) return null
        val track = value as? JsonObject ?: fail(SpotifyImportFailure.INVALID)
        if (track.flag("is_local") == true || track.flag("is_playable") == false) return null
        when (track.text("type")) {
            "episode" -> return null
            "track" -> Unit
            else -> fail(SpotifyImportFailure.INVALID)
        }
        val duration = (track["duration_ms"] as? JsonPrimitive)?.longOrNull
            ?: fail(SpotifyImportFailure.INVALID)
        if (duration == 0L) return null
        if (duration < 0 || duration / 1000 > Int.MAX_VALUE) fail(SpotifyImportFailure.INVALID)
        val title = track.text("name") ?: fail(SpotifyImportFailure.INVALID)
        val artists = track["artists"] as? JsonArray ?: fail(SpotifyImportFailure.INVALID)
        val artist = artists.joinToString(" ") {
            (it as? JsonObject)?.text("name")?.takeIf(String::isNotBlank)
                ?: fail(SpotifyImportFailure.INVALID)
        }
        val album = (track["album"] as? JsonObject)?.text("name")
        if (title.isBlank() || title.length > 500 || artist.isBlank() || artist.length > 500 || (album?.length ?: 0) > 500) {
            fail(SpotifyImportFailure.INVALID)
        }
        return PlaylistImportTrack(title, artist, (duration / 1000).toInt(), album)
    }

    private suspend fun getJson(url: String): JsonObject {
        var token = tokens.accessToken()
        repeat(2) { attempt ->
            val response = request(url, token)
            when (response.first) {
                200 -> return try {
                    Json.parseToJsonElement(response.second) as? JsonObject ?: fail(SpotifyImportFailure.INVALID)
                } catch (error: IllegalArgumentException) {
                    fail(SpotifyImportFailure.INVALID)
                }
                401 -> if (attempt == 0) token = tokens.accessToken(forceRefresh = true) else fail(SpotifyImportFailure.AUTH_REQUIRED)
                403 -> fail(SpotifyImportFailure.ACCESS_DENIED)
                404 -> fail(SpotifyImportFailure.NOT_FOUND)
                429 -> fail(SpotifyImportFailure.RATE_LIMIT)
                else -> fail(SpotifyImportFailure.UNAVAILABLE)
            }
        }
        fail(SpotifyImportFailure.AUTH_REQUIRED)
    }

    private suspend fun request(url: String, token: String): Pair<Int, String> = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).get()
            .header("Authorization", "Bearer $token").header("Accept", "application/json").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(SpotifyImportException(SpotifyImportFailure.UNAVAILABLE))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        val body = it.body
                        val text = if (it.code == 200) {
                            val source = body.source()
                            if (body.contentLength() > PLAYLIST_IMPORT_MAX_BYTES || source.request(PLAYLIST_IMPORT_MAX_BYTES.toLong() + 1)) {
                                fail(SpotifyImportFailure.LIMIT)
                            }
                            source.readUtf8()
                        } else ""
                        it.code to text
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(
                        error as? SpotifyImportException ?: SpotifyImportException(SpotifyImportFailure.UNAVAILABLE),
                    )
                }
            }
        })
    }

    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.number(key: String): Int? = (get(key) as? JsonPrimitive)?.intOrNull
    private fun JsonObject.flag(key: String): Boolean? = (get(key) as? JsonPrimitive)?.booleanOrNull
    private fun fail(reason: SpotifyImportFailure): Nothing = throw SpotifyImportException(reason)
}
