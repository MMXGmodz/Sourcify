package com.metrolist.music.spotify

import com.metrolist.music.utils.PLAYLIST_IMPORT_MAX_BYTES
import com.metrolist.music.utils.PlaylistImportTrack
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SpotifyPlaylistClientTest {
    private val id = "37i9dQZF1DZ06evO41HwPk"

    @Test
    fun `modern metadata keeps order duplicates all artists album and seconds`() = runBlocking {
        val requests = mutableListOf<Request>()
        val tokens = Tokens()
        val client = client(tokens, requests) {
            response(it, head(page(listOf(track("First"), track("First"), track("Last")), total = 3)))
        }
        val result = client.load(id)
        assertEquals("Playlist", result.title)
        assertEquals(listOf("First", "First", "Last"), result.tracks.map { it.title })
        assertEquals(PlaylistImportTrack("First", "Artist Guest", 123, "Album"), result.tracks.first())
        assertEquals(0, result.skippedCount)
        assertEquals("https://api.spotify.com/v1/playlists/$id", requests.single().url.toString())
        assertEquals("Bearer token", requests.single().header("Authorization"))
        assertEquals("GET", requests.single().method)
    }

    @Test
    fun `pagination counts unavailable local and episode rows and constructs trusted offsets`() = runBlocking {
        val requests = mutableListOf<Request>()
        val rows = listOf(track("First"), "{\"item\":null}", "{\"is_local\":true}",
            "{\"item\":{\"type\":\"episode\"}}", "{\"item\":{\"is_playable\":false}}")
        val client = client(Tokens(), requests) {
            if (requests.size == 1) response(it, head(page(rows, total = 6, next = "https://evil.invalid/steal")))
            else response(it, page(listOf(track("Last")), total = 6, offset = 5))
        }
        val result = client.load(id)
        assertEquals(listOf("First", "Last"), result.tracks.map { it.title })
        assertEquals(4, result.skippedCount)
        assertEquals("https://api.spotify.com/v1/playlists/$id/items?offset=5&limit=50", requests.last().url.toString())
    }

    @Test
    fun `legacy response uses deprecated endpoint only when metadata exposes legacy tracks`() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = client(Tokens(), requests) {
            if (requests.size == 1) response(it, head(page(listOf(track("First", legacy = true)), 2, next = "legacy"), legacy = true))
            else response(it, page(listOf(track("Last", legacy = true)), 2, offset = 1))
        }
        assertEquals(listOf("First", "Last"), client.load(id).tracks.map { it.title })
        assertEquals("/v1/playlists/$id/tracks", requests.last().url.encodedPath)
    }

    @Test
    fun `authorization refreshes once and retries with new bearer`() = runBlocking {
        val requests = mutableListOf<Request>()
        val tokens = Tokens()
        val client = client(tokens, requests) {
            if (requests.size == 1) response(it, "", 401) else response(it, head(page(emptyList(), 0)))
        }
        assertTrue(client.load(id).tracks.isEmpty())
        assertEquals(listOf(false, true), tokens.refreshes)
        assertEquals(listOf("Bearer token", "Bearer fresh"), requests.map { it.header("Authorization") })
    }

    @Test
    fun `HTTP failures have stable reasons and never retry rate limits or redirects`() = runBlocking {
        listOf(401 to SpotifyImportFailure.AUTH_REQUIRED, 403 to SpotifyImportFailure.ACCESS_DENIED,
            404 to SpotifyImportFailure.NOT_FOUND, 429 to SpotifyImportFailure.RATE_LIMIT,
            500 to SpotifyImportFailure.UNAVAILABLE, 302 to SpotifyImportFailure.UNAVAILABLE).forEach { (status, reason) ->
            val requests = mutableListOf<Request>()
            val client = client(Tokens(), requests) { response(it, "", status).newBuilder().header("Location", "https://evil.invalid").build() }
            assertFailure(reason) { client.load(id) }
            assertEquals(if (status == 401) 2 else 1, requests.size)
        }
    }

    @Test
    fun `limits include skipped source entries and fail rather than truncate`() = runBlocking {
        listOf(head(page(emptyList(), 201)), head(page(List(201) { "{\"item\":null}" }, 200))).forEach { json ->
            val client = client(Tokens(), mutableListOf()) { response(it, json) }
            assertFailure(SpotifyImportFailure.LIMIT) { client.load(id) }
        }
        val rows = List(200) { "{\"item\":null}" }
        val client = client(Tokens(), mutableListOf()) { response(it, head(page(rows, 200))) }
        assertEquals(200, client.load(id).skippedCount)
    }

    @Test
    fun `response byte limits cover advertised and unknown body length`() = runBlocking {
        val oversized = " ".repeat(PLAYLIST_IMPORT_MAX_BYTES + 1)
        listOf(false, true).forEach { unknownLength ->
            val client = client(Tokens(), mutableListOf()) {
                val body = oversized.toResponseBody("application/json".toMediaType())
                response(it, "").newBuilder().body(if (!unknownLength) body else object : ResponseBody() {
                    override fun contentType() = body.contentType()
                    override fun contentLength() = -1L
                    override fun source() = body.source()
                }).build()
            }
            assertFailure(SpotifyImportFailure.LIMIT) { client.load(id) }
        }
    }

    @Test
    fun `malformed metadata incomplete pagination and repeated offsets cannot import partial data`() = runBlocking {
        listOf("not json", "[]", head(page(listOf(track("First")), 2)),
            head(page(emptyList(), 1, next = "next")), head(page(listOf("{\"item\":{}}"), 1)),
            head(page(listOf(track("First")), 1, offset = 1))).forEach { json ->
            val client = client(Tokens(), mutableListOf()) { response(it, json) }
            assertFailure(SpotifyImportFailure.INVALID) { client.load(id) }
        }
        val requests = mutableListOf<Request>()
        val client = client(Tokens(), requests) { response(it, if (requests.size == 1)
            head(page(listOf(track("First")), 2, next = "next")) else page(listOf(track("Repeated")), 2)) }
        assertFailure(SpotifyImportFailure.INVALID) { client.load(id) }
        assertEquals(2, requests.size)
    }

    @Test
    fun `missing playlist contents reports access restrictions and invalid IDs never use tokens`() = runBlocking {
        val requests = mutableListOf<Request>()
        val tokens = Tokens()
        val client = client(tokens, requests) { response(it, "{\"name\":\"Playlist\"}") }
        assertFailure(SpotifyImportFailure.ACCESS_DENIED) { client.load(id) }
        assertFailure(SpotifyImportFailure.INVALID) { client.load("../other") }
        assertEquals(1, requests.size)
        assertEquals(1, tokens.refreshes.size)
    }

    @Test
    fun `cancellation remains attached while response body is blocked and body closes`() = runBlocking {
        val reading = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val source = object : Source {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    reading.countDown()
                    while (!chain.call().isCanceled()) Thread.sleep(5)
                    throw IOException("Canceled")
                }
                override fun timeout() = Timeout.NONE
                override fun close() { closed.countDown() }
            }.buffer()
            response(chain.request(), "").newBuilder().body(object : ResponseBody() {
                override fun contentType() = "application/json".toMediaType()
                override fun contentLength() = -1L
                override fun source(): BufferedSource = source
            }).build()
        }.build()
        val pending = async { SpotifyPlaylistClient(Tokens(), http).load(id) }
        // Let the child start before blocking this test thread on the network callback.
        kotlinx.coroutines.yield()
        assertTrue(reading.await(3, TimeUnit.SECONDS))
        pending.cancelAndJoin()
        assertTrue(pending.isCancelled)
        assertTrue(closed.await(3, TimeUnit.SECONDS))
    }

    private class Tokens : SpotifyTokens {
        val refreshes = Collections.synchronizedList(mutableListOf<Boolean>())
        override suspend fun accessToken(forceRefresh: Boolean): String {
            refreshes.add(forceRefresh)
            return if (forceRefresh) "fresh" else "token"
        }
    }

    private fun client(tokens: Tokens, requests: MutableList<Request>, respond: (Request) -> Response) =
        SpotifyPlaylistClient(tokens, OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            assertEquals("api.spotify.com", chain.request().url.host)
            requests.add(chain.request())
            respond(chain.request())
        }).build())

    private fun response(request: Request, text: String, status: Int = 200) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(status).message("test")
        .body(text.toResponseBody("application/json".toMediaType())).build()

    private fun track(title: String, legacy: Boolean = false) = """{"${if (legacy) "track" else "item"}":{"type":"track","name":"$title","artists":[{"name":"Artist"},{"name":"Guest"}],"album":{"name":"Album"},"duration_ms":123999}}"""
    private fun head(page: String, legacy: Boolean = false) = """{"name":"Playlist","${if (legacy) "tracks" else "items"}":$page}"""
    private fun page(rows: List<String>, total: Int, offset: Int = 0, next: String? = null) =
        """{"items":[${rows.joinToString(",")}],"total":$total,"offset":$offset,"next":${next?.let { "\"$it\"" } ?: "null"}}"""

    private suspend fun assertFailure(reason: SpotifyImportFailure, block: suspend () -> Unit) {
        try { block(); fail("Expected $reason") } catch (error: SpotifyImportException) { assertEquals(reason, error.reason) }
    }
}
