package com.metrolist.music.utils

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64
import kotlin.math.abs

internal enum class LosslessProvider(val label: String, val defaultEndpoint: String) {
    TIDAL("Tidal", "https://api.monochrome.tf"),
    QOBUZ("Qobuz", "https://qobuz.squid.wtf"),
    DEEZER("Deezer", ""),
}

internal data class LosslessTrack(val provider: LosslessProvider, val id: String, val title: String,
    val artist: String, val duration: Int, val artwork: String?)
internal data class LosslessStream(val url: String)
internal data class FlacInfo(val sampleRate: Int, val bitDepth: Int, val duration: Double, val samples: Long, val channels: Int)

internal fun losslessEndpoint(value: String): HttpUrl = value.trim().toHttpUrl().also {
    require(it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null)
}

private fun safeAudioUrl(value: String): String = try {
    val url = value.toHttpUrl()
    if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) throw IOException("Unsupported audio URL")
    url.toString()
} catch (_: IllegalArgumentException) { throw IOException("Unsupported audio URL") }

private fun JSONObject.objectValue(key: String): JSONObject? = optJSONObject(key)
private fun JSONObject.items(key: String): JSONArray? = optJSONArray(key) ?: optJSONObject(key)?.optJSONArray("items")

internal fun parseLosslessCatalog(provider: LosslessProvider, root: JSONObject): List<LosslessTrack> {
    if (root.has("error") || root.optBoolean("success", true) == false) throw IOException("Provider returned an error")
    val body = root.objectValue("data") ?: root
    val items = if (provider == LosslessProvider.DEEZER) root.optJSONArray("data") else body.items("tracks") ?: body.optJSONArray("items")
    if (items == null) throw IOException("Unsupported catalog response")
    return (0 until minOf(items.length(), 40)).mapNotNull { index ->
        val item = items.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optString("id").takeIf { it.matches(Regex("[0-9]{1,20}")) } ?: return@mapNotNull null
        val title = item.optString("title").take(300).takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val artist = (item.objectValue("performer")?.optString("name") ?: item.objectValue("artist")?.optString("name"))
            ?.take(300).orEmpty()
        val album = item.objectValue("album")
        val image = album?.optString("cover_medium")?.takeIf(String::isNotBlank)
            ?: album?.objectValue("image")?.optString("large")
            ?: album?.optString("cover")?.takeIf { it.isNotBlank() }?.let {
                if (provider == LosslessProvider.TIDAL) "https://resources.tidal.com/images/${it.replace('-', '/')}/320x320.jpg" else it
            }
        val duration = item.optInt("duration").takeIf { it in 1..86400 } ?: return@mapNotNull null
        LosslessTrack(provider, id, title, artist, duration,
            image?.takeIf { runCatching { it.toHttpUrl().isHttps }.getOrDefault(false) })
    }.distinctBy { it.id }
}

internal fun parseLosslessStream(provider: LosslessProvider, root: JSONObject): LosslessStream {
    val body = root.objectValue("data") ?: root
    if (root.has("error") || root.optBoolean("success", true) == false || body.optBoolean("is_preview") ||
        body.optBoolean("preview") || body.optBoolean("requires_client_decryption") ||
        body.optString("assetPresentation").let { it.isNotBlank() && it != "FULL" }) throw IOException("No full unencrypted FLAC available")
    if (provider == LosslessProvider.TIDAL) {
        if (body.optString("assetPresentation") != "FULL") throw IOException("Only full tracks are supported")
        if (body.optString("manifestMimeType") != "application/vnd.tidal.bts") throw IOException("Progressive FLAC is unavailable")
        val text = body.optString("manifest")
        if (text.length > 65536) throw IOException("Manifest is too large")
        val manifest = try { JSONObject(String(Base64.getDecoder().decode(text), Charsets.UTF_8)) }
            catch (_: Exception) { throw IOException("Invalid audio manifest") }
        if (manifest.optString("encryptionType") != "NONE" || manifest.optString("mimeType") != "audio/flac")
            throw IOException("No full unencrypted FLAC available")
        val urls = manifest.optJSONArray("urls") ?: throw IOException("No audio URL")
        if (urls.length() != 1) throw IOException("Segmented audio is unsupported")
        return LosslessStream(safeAudioUrl(urls.optString(0)))
    }
    val format = body.optString("format").ifBlank { body.optString("codec") }.lowercase()
    if (format.isNotBlank() && format !in listOf("flac", "audio/flac", "lossless")) throw IOException("Provider returned compressed audio")
    val url = listOf("url", "download_url", "direct_download_url").firstNotNullOfOrNull { key ->
        body.optString(key).takeIf(String::isNotBlank)
    } ?: throw IOException("No audio URL; provider access may be required")
    return LosslessStream(safeAudioUrl(url))
}

internal fun inspectFlacHeader(bytes: ByteArray, expectedDuration: Int): FlacInfo {
    if (bytes.size < 42 || String(bytes, 0, 4, Charsets.US_ASCII) != "fLaC" || bytes[4].toInt() and 0x7f != 0 ||
        (bytes[5].toInt() and 255) != 0 || (bytes[6].toInt() and 255) != 0 || (bytes[7].toInt() and 255) != 34)
        throw IOException("The source did not return a FLAC file")
    var packed = 0L
    for (index in 18..25) packed = (packed shl 8) or (bytes[index].toLong() and 255)
    val rate = (packed ushr 44).toInt()
    val bits = ((packed ushr 36) and 31).toInt() + 1
    val samples = packed and 0xfffffffffL
    if (rate !in 8000..384000 || bits !in 4..32 || samples == 0L) throw IOException("Invalid FLAC stream information")
    val duration = samples.toDouble() / rate
    if (expectedDuration > 0 && abs(duration - expectedDuration) > maxOf(3.0, expectedDuration * 0.02))
        throw IOException("The source returned a preview or a different version")
    return FlacInfo(rate, bits, duration, samples, ((packed ushr 41) and 7).toInt() + 1)
}

internal suspend fun copyLosslessOriginal(input: InputStream, output: OutputStream, length: Long, duration: Int,
    progress: (Long, Long) -> Unit = { _, _ -> }) {
    val limit = 256L * 1024 * 1024
    if (length > limit) throw IOException("FLAC exceeds the 256 MiB limit")
    val header = ByteArray(42)
    var filled = 0
    while (filled < header.size) {
        currentCoroutineContext().ensureActive()
        val count = input.read(header, filled, header.size - filled)
        if (count < 0) throw IOException("Incomplete FLAC file")
        filled += count
    }
    inspectFlacHeader(header, duration)
    output.write(header)
    var total = header.size.toLong()
    val buffer = ByteArray(65536)
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > limit || length > 0 && total > length) throw IOException("FLAC size mismatch")
        output.write(buffer, 0, count); progress(total, length)
    }
    if (length > 0 && total != length || total <= 42) throw IOException("Incomplete FLAC file")
    output.flush()
}
