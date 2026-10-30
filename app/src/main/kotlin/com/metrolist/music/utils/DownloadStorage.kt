package com.metrolist.music.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.FileNotFoundException
import java.security.MessageDigest

internal data class SavedAudioDocument(val uri: Uri, val folder: Uri, val size: Long)

internal class DownloadStorage(private val context: Context, private val directory: File) {
    private val preferences = context.getSharedPreferences("sourcify_download_location", Context.MODE_PRIVATE)
    private val changes = MutableStateFlow(0L)
    private val activeReads = mutableMapOf<String, Int>()
    val revision = changes.asStateFlow()
    val folderUri: Uri? get() = preferences.getString("folder", null)?.let(Uri::parse)
    private fun recordFile(id: String) = File(directory, "${downloadFileKey(id)}.json")
    private fun planFile(id: String) = File(directory, "${downloadFileKey(id)}.pending")

    fun setFolder(uri: Uri?) {
        require(uri == null || (uri.scheme == "content" && DocumentsContract.isTreeUri(uri)))
        preferences.edit().putString("folder", uri?.toString()).apply()
        changes.value++
    }

    @Synchronized fun plan(id: String) {
        if (planFile(id).isFile) return
        val target = folderUri
        writeDownloadJson(planFile(id), JSONObject().put("folder", target?.toString().orEmpty()))
    }

    @Synchronized fun targetFolder(id: String): Uri? = readDownloadJson(planFile(id), 16_384)
        ?.optString("folder")?.takeIf { it.isNotBlank() }?.let(Uri::parse)

    @Synchronized private fun stagedDocument(id: String): Uri? = readDownloadJson(planFile(id), 16_384)
        ?.optString("document")?.takeIf { it.isNotBlank() }?.let(Uri::parse)?.takeIf { it.scheme == "content" }

    @Synchronized private fun clearStagedDocument(id: String, uri: Uri) {
        val plan = readDownloadJson(planFile(id), 16_384) ?: return
        if (plan.optString("document") == uri.toString()) {
            plan.remove("document")
            writeDownloadJson(planFile(id), plan)
        }
    }

    private fun recoverStagedDocument(id: String) {
        val previous = stagedDocument(id) ?: return
        if (record(id)?.uri != previous) {
            try {
                if (!DocumentsContract.deleteDocument(context.contentResolver, previous)) throw IOException("Could not remove interrupted export")
            } catch (_: FileNotFoundException) { }
        }
        clearStagedDocument(id, previous)
    }

    @Synchronized fun register(id: String, uri: Uri, folder: Uri, size: Long) {
        require(id.isNotBlank() && uri.scheme == "content" && folder.scheme == "content" && size > 0)
        val json = JSONObject().put("id", id).put("uri", uri.toString()).put("folder", folder.toString()).put("size", size)
        require(json.toString().toByteArray().size <= 16_384)
        writeDownloadJson(recordFile(id), json)
        check(record(id)?.uri == uri)
        planFile(id).delete()
        changes.value++
    }

    @Synchronized fun record(id: String): SavedAudioDocument? = runCatching {
        val json = readDownloadJson(recordFile(id), 16_384) ?: return null
        require(json.getString("id") == id)
        val uri = Uri.parse(json.getString("uri"))
        val folder = Uri.parse(json.getString("folder"))
        val size = json.getLong("size")
        require(uri.scheme == "content" && folder.scheme == "content" && size > 0)
        SavedAudioDocument(uri, folder, size)
    }.getOrNull()

    @Synchronized fun storedBytes(): Long = directory.listFiles().orEmpty().filter { it.extension == "json" }
        .sumOf { readDownloadJson(it, 16_384)?.optLong("size")?.coerceAtLeast(0) ?: 0 }

    @Synchronized fun acquireRead(id: String) { activeReads[id] = (activeReads[id] ?: 0) + 1 }
    @Synchronized fun releaseRead(id: String) {
        val remaining = (activeReads[id] ?: 1) - 1
        if (remaining <= 0) activeReads.remove(id) else activeReads[id] = remaining
    }
    @Synchronized fun hasActiveReads(id: String): Boolean = (activeReads[id] ?: 0) > 0

    fun isReadable(id: String): Boolean {
        val file = record(id) ?: return false
        return runCatching { context.contentResolver.openFileDescriptor(file.uri, "r")?.use { it.statSize == file.size } == true }.getOrDefault(false)
    }

    suspend fun export(id: String, title: String, mime: String, source: InputStream, size: Long,
        stillOwned: () -> Boolean = { true }): Boolean = withContext(Dispatchers.IO) {
        recoverStagedDocument(id)
        val folder = targetFolder(id) ?: return@withContext false
        val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val extension = if (mime.contains("webm")) "webm" else if (mime.contains("ogg")) "ogg" else "m4a"
        val name = title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(70).ifBlank { "Song" }
        val document = DocumentsContract.createDocument(context.contentResolver, parent, mime,
            "Sourcify-$name-${downloadFileKey(id).take(8)}.$extension") ?: return@withContext false
        try {
            synchronized(this@DownloadStorage) {
                val plan = readDownloadJson(planFile(id), 16_384) ?: throw IOException("Download was removed during export")
                if (targetFolder(id) != folder || !stillOwned()) throw IOException("Download was removed during export")
                // Journal the new URI before writing so a killed process can clean its partial copy.
                writeDownloadJson(planFile(id), plan.put("document", document.toString()))
            }
            val expectedDigest = context.contentResolver.openFileDescriptor(document, "wt")?.let { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use {
                    val digest = copyDownloadedOriginal(source, it, size)
                    it.fd.sync()
                    digest
                }
            } ?: throw IOException("Download folder is not writable")
            val actualDigest = context.contentResolver.openInputStream(document)?.use { stream ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > size) throw IOException("Exported audio size mismatch")
                    digest.update(buffer, 0, count)
                }
                if (total != size) throw IOException("Exported audio is incomplete")
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            if (actualDigest != expectedDigest) throw IOException("Exported audio checksum mismatch")
            currentCoroutineContext().ensureActive()
            synchronized(this@DownloadStorage) {
                if (targetFolder(id) != folder || stagedDocument(id) != document || !stillOwned()) throw IOException("Download was removed during export")
                register(id, document, folder, size)
            }
            true
        } catch (error: Throwable) {
            if (runCatching { DocumentsContract.deleteDocument(context.contentResolver, document) }.getOrDefault(false)) {
                clearStagedDocument(id, document)
            }
            throw error
        }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        val previous = synchronized(this@DownloadStorage) {
            val previous = listOfNotNull(record(id)?.uri, stagedDocument(id)).distinct()
            recordFile(id).delete(); planFile(id).delete(); changes.value++
            previous
        }
        previous.forEach { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) } }
    }

    companion object {
        @Volatile private var instance: DownloadStorage? = null
        fun get(context: Context): DownloadStorage = instance ?: synchronized(this) {
            instance ?: DownloadStorage(context.applicationContext, File(context.filesDir, "download-documents")).also { instance = it }
        }
    }
}

internal suspend fun copyDownloadedOriginal(input: InputStream, output: OutputStream, expectedLength: Long): String {
    require(expectedLength > 0)
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        if (total > expectedLength - count) throw IOException("Downloaded audio size mismatch")
        output.write(buffer, 0, count)
        digest.update(buffer, 0, count)
        total += count
    }
    if (total != expectedLength) throw IOException("Downloaded audio is incomplete")
    output.flush()
    return digest.digest().joinToString("") { "%02x".format(it) }
}
