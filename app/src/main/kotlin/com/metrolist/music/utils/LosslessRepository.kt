package com.metrolist.music.utils

import android.content.Context
import android.net.Uri
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

private fun losslessApiClient() = OkHttpClient.Builder().proxy(YouTube.proxy).followRedirects(false)
    .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()

internal class LosslessRepository(private val context: Context,
    private val api: OkHttpClient = losslessApiClient(),
    private val audio: OkHttpClient = api.newBuilder().followRedirects(true).followSslRedirects(false).callTimeout(180, TimeUnit.SECONDS).build(),
) {
    private val settings = context.getSharedPreferences("sourcify_lossless_providers", Context.MODE_PRIVATE)

    fun endpoint(provider: LosslessProvider): String = settings.getString("${provider.name}.url", provider.defaultEndpoint).orEmpty()
    fun saveEndpoint(provider: LosslessProvider, endpoint: String) {
        if (endpoint.isNotBlank()) losslessEndpoint(endpoint)
        settings.edit().putString("${provider.name}.url", endpoint.trim()).apply()
    }

    fun saved(track: LosslessTrack): LocalAudioTrack? = settings.getString("saved.${track.provider.name}.${track.id}", null)
        ?.let { LocalAudioStore.get(context).trackForId(it) }

    private fun url(endpoint: String, path: String, params: Map<String, String>): HttpUrl {
        val builder = losslessEndpoint(endpoint).newBuilder()
        if (path.isNotEmpty()) builder.addPathSegments(path)
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    private suspend fun <T> withResponse(client: OkHttpClient, request: Request, block: suspend (Response) -> T): T = coroutineScope {
        val call = client.newCall(request)
        // Keep cancellation attached until the response body has been consumed and closed.
        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try { awaitResponse(call).use { block(it) } } finally { cancellation.cancel() }
    }

    private suspend fun awaitResponse(call: Call): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response, onCancellation = { _, value, _ -> value.close() })
            }
        })
    }

    private suspend fun json(url: HttpUrl): JSONObject = withResponse(api, Request.Builder().url(url).build()) { response ->
        if (!response.isSuccessful || response.code == 202) throw IOException("Anbieter nicht bereit (HTTP ${response.code})")
        val body = response.body ?: throw IOException("Leere Anbieterantwort")
        if (body.contentLength() > 1024 * 1024) throw IOException("Anbieterantwort zu groß")
        val bytes = body.byteStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > 1024 * 1024) throw IOException("Anbieterantwort zu groß")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        try { JSONObject(String(bytes, Charsets.UTF_8)) } catch (_: Exception) { throw IOException("Unbekannte Anbieterantwort") }
    }

    suspend fun search(provider: LosslessProvider, endpoint: String, query: String): List<LosslessTrack> = withContext(Dispatchers.IO) {
        val uri = when (provider) {
            LosslessProvider.TIDAL -> url(endpoint, "search/", mapOf("s" to query.take(200)))
            LosslessProvider.QOBUZ -> url(endpoint, "api/get-music", mapOf("q" to query.take(200), "offset" to "0"))
            LosslessProvider.DEEZER -> {
                losslessEndpoint(endpoint) // A resolver must be configured before its catalog can be used.
                url("https://api.deezer.com", "search", mapOf("q" to query.take(200), "limit" to "30"))
            }
        }
        parseLosslessCatalog(provider, json(uri))
    }

    private suspend fun stream(track: LosslessTrack, endpoint: String, bestQuality: Boolean): LosslessStream {
        suspend fun fetch(best: Boolean): LosslessStream {
            val uri = when (track.provider) {
                LosslessProvider.TIDAL -> url(endpoint, "track/", mapOf("id" to track.id, "quality" to if (best) "HI_RES_LOSSLESS" else "LOSSLESS"))
                LosslessProvider.QOBUZ -> url(endpoint, "api/download-music", mapOf("track_id" to track.id, "quality" to if (best) "27" else "6"))
                LosslessProvider.DEEZER -> url(endpoint, "", mapOf("id" to track.id, "quality" to "FLAC"))
            }
            return parseLosslessStream(track.provider, json(uri))
        }
        return try { fetch(bestQuality) } catch (error: IOException) {
            if (bestQuality && track.provider != LosslessProvider.DEEZER) fetch(false) else throw error
        }
    }

    suspend fun test(provider: LosslessProvider, endpoint: String): Unit = withContext(Dispatchers.IO) {
        val track = search(provider, endpoint, "Daft Punk Get Lucky").firstOrNull() ?: throw IOException("Keine Katalogantwort")
        val source = stream(track, endpoint, false)
        withResponse(audio, Request.Builder().url(source.url).header("Range", "bytes=0-41").build()) { response ->
            if (!response.isSuccessful) throw IOException("Audiozugang nicht verfügbar (HTTP ${response.code})")
            val bytes = ByteArray(42)
            val input = response.body?.byteStream() ?: throw IOException("Keine Audiodaten")
            var count = 0
            while (count < bytes.size) {
                val n = input.read(bytes, count, bytes.size - count)
                if (n < 0) throw IOException("Keine vollständigen FLAC-Metadaten")
                count += n
            }
            inspectFlacHeader(bytes, track.duration)
        }
    }

    suspend fun acquire(track: LosslessTrack, endpoint: String, best: Boolean, progress: (Long, Long) -> Unit): LocalAudioTrack = withContext(Dispatchers.IO) {
        saved(track)?.let { return@withContext it }
        val file = File(context.cacheDir, "lossless-${UUID.randomUUID()}.flac")
        try {
            val source = stream(track, endpoint, best)
            withResponse(audio, Request.Builder().url(source.url).build()) { response ->
                if (response.code != 200) throw IOException("Audiodownload nicht verfügbar (HTTP ${response.code})")
                val body = response.body ?: throw IOException("Keine Audiodaten")
                FileOutputStream(file).use { out ->
                    body.byteStream().use { copyLosslessOriginal(it, out, body.contentLength(), track.duration) { read, length ->
                        if (context.cacheDir.usableSpace < 64L * 1024 * 1024) throw IOException("Not enough free storage")
                        progress(read, length)
                    } }
                    out.fd.sync()
                }
            }
            validateCompleteFlac(file, track.duration)
            // The existing importer validates this with Android's extractor and commits it atomically.
            withContext(NonCancellable) {
                val imported = LocalAudioStore.get(context).importAudio(context, Uri.fromFile(file), track.title, track.artist)
                if (imported.mimeType != "audio/flac") {
                    LocalAudioStore.get(context).remove(imported.id)
                    throw IOException("Die Datei enthält keine FLAC-Audiospur")
                }
                settings.edit().putString("saved.${track.provider.name}.${track.id}", imported.id).commit()
                imported
            }
        } finally { file.delete() }
    }
}
