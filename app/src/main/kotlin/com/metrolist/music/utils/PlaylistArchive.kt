package com.metrolist.music.utils

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class PlaylistArchiveTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Int,
)

internal data class ArchiveAudio(val size: Long, val open: () -> InputStream)
internal data class ArchiveEntry(val track: PlaylistArchiveTrack, val path: String?, val audio: ArchiveAudio?)
internal data class PlaylistArchivePlan(val name: String, val entries: List<ArchiveEntry>) {
    val available: Int = entries.count { it.audio != null }
}

internal suspend fun planPlaylistArchive(
    name: String,
    tracks: List<PlaylistArchiveTrack>,
    resolve: suspend (PlaylistArchiveTrack) -> ArchiveAudio?,
): PlaylistArchivePlan {
    require(tracks.size <= 10_000) { "Playlist is too large" }
    require(name.length <= 4_000 && tracks.all {
        it.id.length <= 4_000 && it.title.length <= 4_000 && it.artist.length <= 4_000
    }) { "Playlist metadata is too large" }
    val names = mutableSetOf<String>()
    var total = 0L
    val entries = tracks.map { track ->
        currentCoroutineContext().ensureActive()
        val audio = resolve(track)?.takeIf { it.size in 1..2_147_483_647L }
        val extension = try {
            audio?.open?.invoke()?.use { input ->
                val header = ByteArray(64)
                var read = 0
                while (read < header.size) {
                    val count = input.read(header, read, header.size - read)
                    if (count <= 0) break
                    read += count
                }
                archiveAudioExtension(header.copyOf(read))
            }
        } catch (_: IOException) { null } catch (_: SecurityException) { null }
        if (audio == null || extension == null) ArchiveEntry(track, null, null) else {
            total += audio.size
            require(total <= 32L * 1024 * 1024 * 1024) { "Playlist audio exceeds 32 GB" }
            val base = safeArchiveName("${track.artist} - ${track.title}")
            var path = "audio/$base.$extension"
            var suffix = 2
            while (!names.add(path.lowercase(Locale.ROOT))) path = "audio/$base (${suffix++}).$extension"
            ArchiveEntry(track, path, audio)
        }
    }
    return PlaylistArchivePlan(name, entries)
}

internal fun safeArchiveName(name: String): String = name
    .replace(Regex("[\\p{Cntrl}\\\\/:*?\"<>|]"), "_")
    .trim(' ', '.').take(70).trimEnd(' ', '.').ifBlank { "Playlist" }

// Use the container header: Opus may be WebM or Ogg, and AAC may be M4A or ADTS.
internal fun archiveAudioExtension(header: ByteArray): String? {
    fun at(offset: Int, text: String) = header.size >= offset + text.length &&
        text.indices.all { header[offset + it].toInt() and 255 == text[it].code }
    return when {
        at(0, "fLaC") -> "flac"
        at(0, "RIFF") && at(8, "WAVE") -> "wav"
        at(0, "OggS") -> if (header.toString(Charsets.ISO_8859_1).contains("OpusHead")) "opus" else "ogg"
        at(4, "ftyp") -> "m4a"
        header.size >= 4 && header.take(4).map { it.toInt() and 255 } == listOf(0x1a, 0x45, 0xdf, 0xa3) -> "webm"
        at(0, "ID3") -> "mp3"
        header.size >= 2 && header[0].toInt() and 255 == 255 && header[1].toInt() and 0xf6 == 0xf0 -> "aac"
        header.size >= 2 && header[0].toInt() and 255 == 255 && header[1].toInt() and 0xe0 == 0xe0 -> "mp3"
        else -> null
    }
}

internal suspend fun writePlaylistArchive(
    plan: PlaylistArchivePlan,
    output: OutputStream,
    progress: (Int, Int) -> Unit = { _, _ -> },
) {
    require(plan.available > 0) { "No audio is available" }
    ZipOutputStream(output).use { zip ->
        zip.setLevel(0)
        val buffer = ByteArray(64 * 1024)
        var completed = 0
        plan.entries.forEach { entry ->
            currentCoroutineContext().ensureActive()
            val audio = entry.audio ?: return@forEach
            zip.putNextEntry(ZipEntry(requireNotNull(entry.path)))
            audio.open().use { input ->
                var copied = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    if (copied > audio.size - count) throw IOException("Audio size changed during export")
                    zip.write(buffer, 0, count)
                    copied += count
                }
                if (copied != audio.size) throw IOException("Audio is incomplete")
            }
            zip.closeEntry()
            progress(++completed, plan.available)
        }
        currentCoroutineContext().ensureActive()
        zip.putNextEntry(ZipEntry("playlist.m3u8"))
        zip.writeUtf8("#EXTM3U\n")
        plan.entries.forEach { entry ->
            currentCoroutineContext().ensureActive()
            if (entry.path != null) {
                zip.writeUtf8("#EXTINF:${entry.track.durationSeconds},${manifestLine(entry.track.artist)} - ${manifestLine(entry.track.title)}\n${entry.path}\n")
            }
        }
        zip.closeEntry()
        zip.putNextEntry(ZipEntry("playlist.json"))
        zip.writeUtf8("{\"version\":1,\"name\":${jsonString(plan.name)},\"tracks\":[")
        plan.entries.forEachIndexed { index, entry ->
            currentCoroutineContext().ensureActive()
            zip.writeUtf8(buildString {
                if (index > 0) append(',')
                append("{\"id\":${jsonString(entry.track.id)},\"title\":${jsonString(entry.track.title)},\"artist\":${jsonString(entry.track.artist)},")
                append("\"durationSeconds\":${entry.track.durationSeconds},\"path\":${entry.path?.let(::jsonString) ?: "null"},")
                append("\"status\":${jsonString(if (entry.audio == null) "missing" else "included")}}")
            })
        }
        zip.writeUtf8("]}")
        zip.closeEntry()
    }
}

private fun OutputStream.writeUtf8(value: String) = write(value.toByteArray(Charsets.UTF_8))
private fun manifestLine(value: String) = value.replace('\r', ' ').replace('\n', ' ')
private fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            in '\u0000'..'\u001f' -> append("\\u${char.code.toString(16).padStart(4, '0')}")
            else -> append(char)
        }
    }
    append('"')
}
