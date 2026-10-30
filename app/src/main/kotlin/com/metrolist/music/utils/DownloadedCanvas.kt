package com.metrolist.music.utils

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.metrolist.innertube.YouTube
import com.metrolist.music.playback.CachedStreamUrl
import com.metrolist.music.playback.withResolvedStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class DownloadedCanvas(val file: File, val startMs: Long, val endMs: Long)

/** Fetch a bounded source, then remux only the chosen video interval, without transcoding. */
internal suspend fun downloadCanvasClip(context: Context, source: CanvasVideoSource): DownloadedCanvas {
    val work = File(context.cacheDir, "canvas-download-${UUID.randomUUID()}").apply { check(mkdirs()) }
    val original = File(work, "source.video")
    try {
        copyCanvasSource(context, source, original)
        return trimCanvasFile(source, original, work)
    } catch (error: Throwable) { work.deleteRecursively(); throw error }
}

private suspend fun copyCanvasSource(context: Context, source: CanvasVideoSource, file: File) {
    val client = OkHttpClient.Builder().proxy(YouTube.proxy)
        .proxyAuthenticator { _, response -> YouTube.proxyAuth?.let { response.request.newBuilder().header("Proxy-Authorization", it).build() } }
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()
    val factory = CanvasNetworkPolicy.get(context).guard(DefaultDataSource.Factory(context, OkHttpDataSource.Factory(client)))
    val stream = source.stream?.let { CachedStreamUrl(source.uri.toString(), it.headers, it.clientName,
        it.requireBoundedRange, it.rangeChunkSizeBytes, it.useRangeChunks) }
    copyCanvasBytes(factory, source.uri, stream, file)
}

internal suspend fun copyCanvasBytes(factory: DataSource.Factory, uri: Uri, stream: CachedStreamUrl?, file: File,
    maxBytes: Long = 32L * 1024 * 1024) {
    val chunked = stream != null && (stream.requireBoundedRange || stream.useRangeChunks) && stream.rangeChunkSizeBytes > 0
    var total = 0L
    file.outputStream().use { output ->
        while (true) {
            currentCoroutineContext().ensureActive()
            val dataSource = factory.createDataSource()
            var copied = 0L
            var wholeLength: Long? = null
            var openedLength = C.LENGTH_UNSET.toLong()
            try {
                var spec = DataSpec.Builder().setUri(uri).setPosition(total).build()
                if (stream != null) spec = spec.withResolvedStream(stream)
                openedLength = dataSource.open(spec)
                if (!chunked && openedLength > maxBytes) throw IOException("Canvas source is too large")
                wholeLength = dataSource.responseHeaders.entries.firstOrNull { it.key.equals("Content-Range", true) }
                    ?.value?.firstOrNull()?.substringAfterLast('/')?.toLongOrNull()
                if (wholeLength != null && wholeLength > maxBytes) throw IOException("Canvas source is too large")
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = dataSource.read(buffer, 0, buffer.size)
                    if (count == C.RESULT_END_OF_INPUT) break
                    if (total + copied + count > maxBytes) throw IOException("Canvas source is too large")
                    output.write(buffer, 0, count)
                    copied += count
                }
            } catch (error: HttpDataSource.InvalidResponseCodeException) {
                val length = error.headerFields.entries.firstOrNull { it.key.equals("Content-Range", true) }
                    ?.value?.firstOrNull()?.substringAfterLast('/')?.toLongOrNull()
                if (error.responseCode == 416 && total > 0 && length == total) break
                throw error
            } finally { dataSource.close() }
            total += copied
            if (!chunked && openedLength >= 0 && copied != openedLength) throw IOException("Canvas transfer ended early")
            if (wholeLength != null && (total > wholeLength || (!chunked || copied < (stream?.rangeChunkSizeBytes ?: 0L)) && total < wholeLength)) {
                throw IOException("Canvas transfer ended early")
            }
            if (!chunked || wholeLength?.let { total >= it } == true || copied < stream!!.rangeChunkSizeBytes) break
            if (copied == 0L) throw IOException("Canvas transfer ended early")
        }
    }
    if (total == 0L) throw IOException("Empty Canvas source")
}

private suspend fun trimCanvasFile(source: CanvasVideoSource, original: File, work: File): DownloadedCanvas {
    val extractor = MediaExtractor()
    var muxer: MediaMuxer? = null
    var started = false
    try {
        extractor.setDataSource(original.absolutePath)
        val index = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
        } ?: throw IOException("No Canvas video track")
        val format = extractor.getTrackFormat(index)
        val mime = format.getString(MediaFormat.KEY_MIME)
        val webm = mime == "video/x-vnd.on2.vp8" || mime == "video/x-vnd.on2.vp9"
        val output = File(work, if (webm) "clip.webm" else "clip.mp4")
        val writer = MediaMuxer(output.absolutePath, if (webm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer = writer
        if (format.containsKey(MediaFormat.KEY_ROTATION)) {
            val rotation = format.getInteger(MediaFormat.KEY_ROTATION)
            if (rotation in setOf(0, 90, 180, 270)) writer.setOrientationHint(rotation)
        }
        val track = writer.addTrack(format)
        extractor.selectTrack(index)
        val startUs = Math.multiplyExact(source.clipStartMs.coerceAtLeast(0), 1000L)
        val endUs = if (source.clipEndMs > source.clipStartMs) Math.multiplyExact(source.clipEndMs, 1000L) else Math.addExact(startUs, 8_000_000L)
        require(endUs > startUs && endUs - startUs <= 8_000_000)
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val firstUs = extractor.sampleTime.also { require(it >= 0 && it <= startUs) }
        require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
        val buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()
        writer.start(); started = true
        var samples = 0
        var lastUs = firstUs
        while (extractor.sampleTime in firstUs until endUs) {
            currentCoroutineContext().ensureActive()
            if (++samples > 6000 || (android.os.Build.VERSION.SDK_INT >= 28 && extractor.sampleSize > buffer.capacity())) throw IOException("Unsupported Canvas samples")
            require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0)
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            lastUs = extractor.sampleTime
            info.set(0, size, lastUs - firstUs, if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            writer.writeSampleData(track, buffer, info)
            if (!extractor.advance()) break
        }
        writer.stop(); started = false
        val start = (startUs - firstUs) / 1000
        val end = minOf((endUs - firstUs) / 1000, (lastUs - firstUs) / 1000 + 1)
        require(samples > 0 && end > start && output.length() in 1..8L * 1024 * 1024)
        original.delete()
        return DownloadedCanvas(output, start, end)
    } finally {
        if (started) runCatching { muxer?.stop() }
        muxer?.release()
        extractor.release()
    }
}
