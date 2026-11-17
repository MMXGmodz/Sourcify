package com.metrolist.music.utils

import java.net.URI
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

internal const val PLAYLIST_IMPORT_MAX_TRACKS = 200
internal const val PLAYLIST_IMPORT_MAX_BYTES = 2 * 1024 * 1024

internal enum class PlaylistImportError { UNSUPPORTED, EXPORT_REQUIRED, INVALID_FILE, LIMIT, EMPTY, REQUEST }

internal class PlaylistImportException(val reason: PlaylistImportError) : Exception(reason.name)

internal data class PlaylistImportTrack(val title: String, val artist: String, val duration: Int? = null)
internal data class PlaylistImportLink(val provider: String, val id: String)

internal fun parsePlaylistImportLink(value: String): PlaylistImportLink {
    val uri = runCatching { URI(value.trim()) }.getOrNull()
        ?: throw PlaylistImportException(PlaylistImportError.UNSUPPORTED)
    if (uri.scheme != "https" || uri.userInfo != null || uri.port != -1) {
        throw PlaylistImportException(PlaylistImportError.UNSUPPORTED)
    }
    val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.")
    if (host in setOf("open.spotify.com", "spotify.com", "music.apple.com", "tidal.com", "soundcloud.com")) {
        throw PlaylistImportException(PlaylistImportError.EXPORT_REQUIRED)
    }
    if (host == "deezer.com") {
        val id = Regex("/(?:[a-z]{2}/)?playlist/([0-9]+)/?").matchEntire(uri.path.orEmpty())?.groupValues?.get(1)
        if (id != null) return PlaylistImportLink("deezer", id)
    }
    if (host in setOf("youtube.com", "music.youtube.com") && uri.path == "/playlist") {
        val id = uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("list=") }?.substringAfter('=')
        if (id != null && Regex("[A-Za-z0-9_-]{1,150}").matches(id)) return PlaylistImportLink("youtube", id)
    }
    throw PlaylistImportException(PlaylistImportError.UNSUPPORTED)
}

internal fun parsePlaylistImportFile(text: String): List<PlaylistImportTrack> {
    if (text.toByteArray(Charsets.UTF_8).size > PLAYLIST_IMPORT_MAX_BYTES) throw PlaylistImportException(PlaylistImportError.LIMIT)
    val clean = text.removePrefix("\uFEFF").trimStart()
    val tracks = if (clean.startsWith("#EXTM3U") || clean.startsWith("#EXTINF:")) {
        clean.lineSequence().filter { it.startsWith("#EXTINF:") }.map { line ->
            val info = line.substringAfter(',', "")
            val artist = info.substringBefore(" - ", "").trim()
            val title = info.substringAfter(" - ", "").trim()
            PlaylistImportTrack(title, artist, line.substringAfter(':').substringBefore(',').toIntOrNull()?.takeIf { it > 0 })
        }.toList()
    } else {
        val firstLine = clean.lineSequence().firstOrNull().orEmpty()
        val delimiter = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
        val rows = parseImportCsv(clean, delimiter)
        val header = rows.firstOrNull()?.map { normalizeImportText(it) }.orEmpty()
        fun column(vararg names: String) = header.indexOfFirst { it in names }
        val title = column("title", "track name", "track title", "name")
        val artist = column("artist", "artists", "artist name", "artist names", "artist name s")
        val duration = column("duration", "duration seconds", "duration ms", "duration milliseconds")
        if (title < 0 || artist < 0) throw PlaylistImportException(PlaylistImportError.INVALID_FILE)
        rows.drop(1).filter { row -> row.any { it.isNotBlank() } }.map { row ->
            val rawDuration = row.getOrNull(duration)?.toDoubleOrNull()
            val seconds = rawDuration?.let { if (header[duration] in setOf("duration ms", "duration milliseconds")) it / 1000 else it }
            PlaylistImportTrack(row.getOrNull(title).orEmpty().trim(), row.getOrNull(artist).orEmpty().trim(), seconds?.toInt()?.takeIf { it > 0 })
        }
    }
    if (tracks.size > PLAYLIST_IMPORT_MAX_TRACKS) throw PlaylistImportException(PlaylistImportError.LIMIT)
    if (tracks.isEmpty()) throw PlaylistImportException(PlaylistImportError.EMPTY)
    if (tracks.any { it.title.isBlank() || it.artist.isBlank() || it.title.length > 500 || it.artist.length > 500 }) {
        throw PlaylistImportException(PlaylistImportError.INVALID_FILE)
    }
    return tracks
}

private fun parseImportCsv(text: String, delimiter: Char): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var closedQuote = false
    var index = 0
    fun endField() { row.add(field.toString()); field.setLength(0); closedQuote = false }
    fun endRow() {
        endField(); rows.add(row); row = mutableListOf()
        if (rows.size > PLAYLIST_IMPORT_MAX_TRACKS + 1) throw PlaylistImportException(PlaylistImportError.LIMIT)
    }
    while (index < text.length) {
        val char = text[index++]
        if (quoted) {
            if (char == '"') {
                if (text.getOrNull(index) == '"') { field.append('"'); index++ } else { quoted = false; closedQuote = true }
            } else field.append(char)
        } else when {
            char == '"' && field.isEmpty() && !closedQuote -> quoted = true
            char == delimiter -> endField()
            char == '\n' || char == '\r' -> { if (char == '\r' && text.getOrNull(index) == '\n') index++; endRow() }
            char == '"' || closedQuote -> throw PlaylistImportException(PlaylistImportError.INVALID_FILE)
            else -> field.append(char)
        }
    }
    if (quoted) throw PlaylistImportException(PlaylistImportError.INVALID_FILE)
    if (field.isNotEmpty() || row.isNotEmpty() || closedQuote) endRow()
    return rows
}

internal fun normalizeImportText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

internal fun playlistImportMatchScore(source: PlaylistImportTrack, candidate: PlaylistImportTrack): Double {
    val sourceTitle = normalizeImportText(source.title)
    val candidateTitle = normalizeImportText(candidate.title)
    val versions = setOf("live", "remix", "acoustic", "instrumental", "karaoke", "sped", "slowed")
    if (sourceTitle.split(' ').toSet().intersect(versions) != candidateTitle.split(' ').toSet().intersect(versions)) return 0.0
    if (source.duration != null && candidate.duration != null && abs(source.duration - candidate.duration) > maxOf(8, source.duration / 10)) return 0.0
    fun overlap(left: String, right: String): Double {
        val a = left.split(' ').filter(String::isNotBlank).toSet()
        val b = right.split(' ').filter(String::isNotBlank).toSet()
        return if (a.isEmpty() || b.isEmpty()) 0.0 else 2.0 * a.intersect(b).size / (a.size + b.size)
    }
    val titleScore = overlap(sourceTitle, candidateTitle)
    val artistScore = overlap(normalizeImportText(source.artist), normalizeImportText(candidate.artist))
    return if (titleScore < 0.5 || artistScore == 0.0) 0.0 else 0.7 * titleScore + 0.3 * artistScore
}
