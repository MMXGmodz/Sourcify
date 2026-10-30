package com.metrolist.music.utils

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.metrolist.innertube.models.MediaInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal fun downloadFileKey(id: String): String = MessageDigest.getInstance("SHA-256")
    .digest(id.toByteArray()).joinToString("") { "%02x".format(it) }

internal fun writeDownloadJson(file: File, value: JSONObject) {
    check(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
    val bytes = value.toString().toByteArray()
    require(bytes.size <= 131_072)
    val atomic = AtomicFile(file)
    val stream = atomic.startWrite()
    try { stream.write(bytes); atomic.finishWrite(stream) }
    catch (error: Throwable) { atomic.failWrite(stream); throw error }
}

internal fun readDownloadJson(file: File, maxBytes: Long = 131_072): JSONObject? = runCatching {
    require(maxBytes in 1 until Int.MAX_VALUE.toLong())
    AtomicFile(file).openRead().use { input ->
        require(input.channel.size() in 1..maxBytes)
        val bytes = ByteArray(maxBytes.toInt() + 1)
        var length = 0
        while (length < bytes.size) {
            val count = input.read(bytes, length, bytes.size - length)
            if (count < 0) break
            length += count
        }
        require(length.toLong() in 1..maxBytes)
        JSONObject(String(bytes, 0, length, Charsets.UTF_8))
    }
}.getOrNull()

/** These files belong to saved downloads and survive normal image/video cache eviction. */
internal class DownloadCompanionStore(private val directory: File) {
    private val changes = MutableStateFlow(0L)
    val revision = changes.asStateFlow()
    private fun folder(id: String) = File(directory, downloadFileKey(id))
    private fun retained(id: String) = File(folder(id), ".owner").isFile

    @Synchronized fun retain(id: String) {
        val folder = folder(id)
        check(folder.mkdirs() || folder.isDirectory)
        File(folder, ".owner").apply { if (!exists()) writeText("1") }
    }

    @Synchronized fun retainOnly(ids: Set<String>) {
        val keys = ids.map(::downloadFileKey).toSet()
        directory.listFiles().orEmpty().filter { it.isDirectory && it.name !in keys }.forEach { it.deleteRecursively() }
        changes.value++
    }

    @Synchronized fun remove(id: String) { folder(id).deleteRecursively(); changes.value++ }
    @Synchronized fun lastAttempt(id: String): Long = readDownloadJson(File(folder(id), "attempt.json"))?.optLong("time") ?: 0
    @Synchronized fun markAttempt(id: String) {
        if (retained(id)) writeDownloadJson(File(folder(id), "attempt.json"), JSONObject().put("time", System.currentTimeMillis()))
    }

    @Synchronized fun saveInfo(id: String, info: MediaInfo) {
        if (!retained(id)) return
        require(info.videoId == id)
        val json = JSONObject().put("id", id)
        mapOf("title" to info.title, "author" to info.author, "authorId" to info.authorId,
            "authorThumbnail" to info.authorThumbnail, "description" to info.description,
            "uploadDate" to info.uploadDate, "subscribers" to info.subscribers).forEach { (key, value) ->
            if (value != null) json.put(key, value.take(if (key == "description") 64_000 else 2_000))
        }
        mapOf("views" to info.viewCount, "likes" to info.like, "dislikes" to info.dislike).forEach { (key, value) ->
            if (value != null) json.put(key, value)
        }
        writeDownloadJson(File(folder(id), "info.json"), json)
        changes.value++
    }

    @Synchronized fun info(id: String): MediaInfo? {
        val json = readDownloadJson(File(folder(id), "info.json")) ?: return null
        if (json.optString("id") != id) return null
        fun text(key: String) = json.optString(key).takeIf { it.isNotBlank() }
        fun number(key: String) = if (json.has(key)) json.optInt(key) else null
        return MediaInfo(id, text("title"), text("author"), text("authorId"), text("authorThumbnail"),
            text("description"), text("uploadDate"), text("subscribers"), number("views"), number("likes"), number("dislikes"))
    }

    @Synchronized fun additionalCredits(id: String): JSONObject? = readDownloadJson(File(folder(id), "credits.json"))
    @Synchronized fun saveAdditionalCredits(id: String, credits: JSONObject) {
        if (retained(id) && credits.toString().toByteArray().size <= 131_072) {
            writeDownloadJson(File(folder(id), "credits.json"), credits)
            changes.value++
        }
    }

    @Synchronized fun saveCanvas(id: String, originalSelection: CanvasSelection?, file: File, start: Long, end: Long): Boolean {
        if (!retained(id) || file.length() !in 1..MAX_CANVAS_BYTES) return false
        require(start >= 0 && end > start && end - start <= 8_000)
        val used = directory.walkTopDown().filter { it.isFile && it.name.startsWith("canvas.") && it.extension != "json" }.sumOf { it.length() }
        val target = File(folder(id), if (file.extension == "webm") "canvas.webm" else "canvas.mp4")
        if (used - target.length() + file.length() > MAX_TOTAL_CANVAS_BYTES) return false
        val atomic = AtomicFile(target)
        val out = atomic.startWrite()
        try { file.inputStream().use { it.copyTo(out) }; atomic.finishWrite(out) }
        catch (error: Throwable) { atomic.failWrite(out); throw error }
        writeDownloadJson(File(folder(id), "canvas.json"), JSONObject()
            .put("selection", selectionIdentity(originalSelection)).put("file", target.name).put("start", start).put("end", end))
        changes.value++
        return true
    }

    @Synchronized fun playbackSelection(id: String, manual: CanvasSelection?, automaticEnabled: Boolean = true): CanvasSelection? {
        if (manual == null && !automaticEnabled) return null
        val json = readDownloadJson(File(folder(id), "canvas.json"), 4096) ?: return manual
        if (json.optString("selection") != selectionIdentity(manual)) return manual
        val name = json.optString("file").takeIf { it in setOf("canvas.mp4", "canvas.webm") } ?: return manual
        val file = File(folder(id), name)
        val start = json.optLong("start", -1)
        val end = json.optLong("end", -1)
        if (!file.isFile || file.length() !in 1..MAX_CANVAS_BYTES || start < 0 || end <= start || end - start > 8000) return manual
        return CanvasSelection(Uri.fromFile(file), startMs = start, endMs = end)
    }

    private fun selectionIdentity(selection: CanvasSelection?): String = selection?.let {
        "${it.uri ?: it.videoId}:${it.startMs}:${it.endMs}"
    } ?: "automatic"

    companion object {
        private const val MAX_CANVAS_BYTES = 8L * 1024 * 1024
        private const val MAX_TOTAL_CANVAS_BYTES = 512L * 1024 * 1024
        @Volatile private var instance: DownloadCompanionStore? = null
        fun get(context: Context): DownloadCompanionStore = instance ?: synchronized(this) {
            instance ?: DownloadCompanionStore(File(context.applicationContext.filesDir, "download-extras")).also { instance = it }
        }
    }
}

internal suspend fun runOptionalDownloadTasks(vararg tasks: suspend () -> Unit) {
    for (task in tasks) try { task() } catch (error: CancellationException) { throw error } catch (_: Exception) { }
}
