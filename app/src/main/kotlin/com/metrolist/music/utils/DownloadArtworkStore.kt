package com.metrolist.music.utils

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Download artwork belongs to the download, not the evictable image cache. */
internal class DownloadArtworkStore(
    private val directory: File,
    private val maxImageBytes: Long = 4L * 1024 * 1024,
    private val maxTotalBytes: Long = 512L * 1024 * 1024,
) {
    private var references: MutableMap<String, Set<String>>? = null

    fun fileFor(url: String): File? = artworkKey(url)?.let { key ->
        File(directory, "$key.image").takeIf { it.isFile && it.length() in 1..maxImageBytes }
    }

    @Synchronized
    fun retain(trackId: String, urls: List<String>) {
        val keys = urls.mapNotNull(::artworkKey).take(2).toSet()
        val trackKey = sha256(trackId)
        val index = references()
        if (index[trackKey] == keys) return
        ensureDirectory()
        val temporary = File.createTempFile("track-", ".tmp", directory)
        try {
            temporary.writeText(keys.joinToString("\n"))
            replace(temporary, File(directory, "$trackKey.refs"))
            val previous = index.put(trackKey, keys).orEmpty()
            previous.filterNot { key -> index.values.any { key in it } }.forEach(::deleteImage)
        } finally {
            temporary.delete()
        }
    }

    @Synchronized
    fun remove(trackId: String) {
        val index = references()
        val previous = index.remove(sha256(trackId)).orEmpty()
        File(directory, "${sha256(trackId)}.refs").delete()
        previous.filterNot { key -> index.values.any { key in it } }.forEach(::deleteImage)
    }

    @Synchronized
    fun retainOnly(trackIds: Set<String>) {
        val retained = trackIds.map(::sha256).toSet()
        val index = references()
        index.keys.toList().filterNot { it in retained }.forEach { key ->
            index.remove(key)
            File(directory, "$key.refs").delete()
        }
        val used = index.values.flatten().toSet()
        directory.listFiles()?.filter { it.extension == "image" && it.nameWithoutExtension !in used }
            ?.forEach { it.delete() }
        directory.listFiles()?.filter { it.extension == "tmp" &&
            System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }
            ?.forEach { it.delete() }
    }

    /** Network I/O and bounded copying happen outside the index lock. */
    fun save(url: String, contentType: String?, contentLength: Long, input: InputStream): Boolean {
        val key = artworkKey(url) ?: return false
        val mime = contentType?.substringBefore(';')?.trim()?.lowercase()
        if (mime !in IMAGE_MIME_TYPES || contentLength > maxImageBytes) return false
        ensureDirectory()
        val temporary = File.createTempFile("image-", ".tmp", directory)
        try {
            val header = ByteArray(16)
            var headerSize = 0
            var copied = 0L
            temporary.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    if (copied > maxImageBytes) return false
                    if (headerSize < header.size) {
                        val added = minOf(count, header.size - headerSize)
                        buffer.copyInto(header, headerSize, 0, added)
                        headerSize += added
                    }
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
            if (copied == 0L || (contentLength >= 0 && copied != contentLength) ||
                !matchesImageMime(header.copyOf(headerSize), mime)
            ) return false
            synchronized(this) {
                // A download may have been removed while its image request was in flight.
                if (references().values.none { key in it }) return false
                val destination = File(directory, "$key.image")
                val total = directory.listFiles()?.filter { it.extension == "image" }
                    ?.sumOf { it.length() } ?: 0L
                // Preserve artwork for existing downloads instead of evicting it like an LRU.
                if (total - destination.length() + copied > maxTotalBytes) return false
                replace(temporary, destination)
            }
            return true
        } finally {
            temporary.delete()
        }
    }

    @Synchronized
    fun invalidate(url: String) {
        artworkKey(url)?.let(::deleteImage)
    }

    private fun references(): MutableMap<String, Set<String>> {
        references?.let { return it }
        return directory.listFiles().orEmpty()
            .filter { it.extension == "refs" && HASH.matches(it.nameWithoutExtension) && it.length() <= 1024 }
            .associate { file ->
                file.nameWithoutExtension to runCatching {
                    file.readLines().filter { HASH.matches(it) }.take(2).toSet()
                }.getOrDefault(emptySet())
            }.toMutableMap().also { references = it }
    }

    private fun ensureDirectory() {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Cannot create download artwork directory")
        }
    }

    private fun deleteImage(key: String) {
        File(directory, "$key.image").delete()
    }

    private fun replace(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE)
        } catch (ignored: AtomicMoveNotSupportedException) {
            if (!source.renameTo(destination)) throw IOException("Cannot commit download artwork")
        }
    }

    companion object {
        @Volatile private var instance: DownloadArtworkStore? = null

        fun getInstance(filesDir: File): DownloadArtworkStore = instance ?: synchronized(this) {
            instance ?: DownloadArtworkStore(File(filesDir, "download-artwork")).also { instance = it }
        }

        private val HASH = Regex("[a-f0-9]{64}")
        private val IMAGE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
        private val SIZE_SUFFIX = Regex("=(?:w\\d+-h\\d+|s\\d+)(?:-[a-zA-Z0-9]+)*$")

        internal fun artworkKey(url: String): String? {
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            if (uri.scheme !in setOf("https", "http") || uri.host == null || uri.userInfo != null) return null
            val path = if (uri.host in setOf("lh3.googleusercontent.com", "yt3.googleusercontent.com", "yt3.ggpht.com")) {
                uri.rawPath.replace(SIZE_SUFFIX, "")
            } else uri.rawPath
            // Keep the query and origin: arbitrary URLs and signed URLs must never collide.
            return sha256("${uri.scheme}://${uri.rawAuthority}$path${uri.rawQuery?.let { "?$it" }.orEmpty()}")
        }

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        private fun matchesImageMime(bytes: ByteArray, mime: String?): Boolean {
            fun startsWith(vararg expected: Int) = bytes.size >= expected.size &&
                expected.indices.all { (bytes[it].toInt() and 0xff) == expected[it] }
            fun text(offset: Int, length: Int) = if (bytes.size >= offset + length) {
                String(bytes, offset, length, Charsets.US_ASCII)
            } else ""
            return when (mime) {
                "image/jpeg" -> startsWith(0xff, 0xd8, 0xff)
                "image/png" -> startsWith(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
                "image/webp" -> text(0, 4) == "RIFF" && text(8, 4) == "WEBP"
                "image/gif" -> text(0, 6) in setOf("GIF87a", "GIF89a")
                else -> false
            }
        }
    }
}
