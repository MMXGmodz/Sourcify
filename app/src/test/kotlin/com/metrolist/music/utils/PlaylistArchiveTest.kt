package com.metrolist.music.utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PlaylistArchiveTest {
    private val flac = "fLaC-original-audio".toByteArray()
    private fun track(id: String, title: String = "Track") = PlaylistArchiveTrack(id, title, "Artist", 123)

    @Test
    fun `archive preserves originals and order and reports unavailable tracks`() = runBlocking {
        val tracks = listOf(track("first", "../Same\nTitle"), track("missing"), track("last", "../Same\nTitle"))
        val plan = planPlaylistArchive("Playlist \"name\"", tracks) {
            if (it.id == "missing") null else ArchiveAudio(flac.size.toLong()) { ByteArrayInputStream(flac) }
        }
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Pair<Int, Int>>()
        writePlaylistArchive(plan, output) { done, total -> progress += done to total }
        val files = unzip(output.toByteArray())
        assertEquals(2, plan.available)
        assertEquals(4, files.size)
        val paths = plan.entries.mapNotNull { it.path }
        assertEquals(2, paths.toSet().size)
        assertTrue(paths[1].endsWith(" (2).flac"))
        paths.forEach { path ->
            assertFalse(path.removePrefix("audio/").contains('/'))
            assertFalse(path.contains('\n'))
            assertArrayEquals(flac, files[path])
        }
        val m3u = files.getValue("playlist.m3u8").toString(Charsets.UTF_8)
        assertTrue(m3u.startsWith("#EXTM3U\n#EXTINF:123,"))
        assertTrue(m3u.indexOf(paths[0]) < m3u.indexOf(paths[1]))
        val json = files.getValue("playlist.json").toString(Charsets.UTF_8)
        assertTrue(json.contains("\"name\":\"Playlist \\\"name\\\"\""))
        assertTrue(json.contains("\"path\":null,\"status\":\"missing\""))
        assertEquals(listOf(1 to 2, 2 to 2), progress)
    }

    @Test
    fun `container signatures determine honest file extensions`() {
        assertEquals("flac", archiveAudioExtension(flac))
        assertEquals("wav", archiveAudioExtension("RIFF1234WAVE".toByteArray()))
        assertEquals("m4a", archiveAudioExtension("1234ftypM4A ".toByteArray()))
        assertEquals("opus", archiveAudioExtension("OggS1234OpusHead".toByteArray()))
        assertEquals("ogg", archiveAudioExtension("OggS1234vorbis".toByteArray()))
        assertEquals("webm", archiveAudioExtension(byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte())))
        assertEquals("mp3", archiveAudioExtension("ID3tag".toByteArray()))
        assertEquals("aac", archiveAudioExtension(byteArrayOf(0xff.toByte(), 0xf1.toByte())))
        assertEquals("mp3", archiveAudioExtension(byteArrayOf(0xff.toByte(), 0xfb.toByte())))
        assertEquals(null, archiveAudioExtension("not audio".toByteArray()))
    }

    @Test
    fun `names cannot escape archive directory or collide by case`() = runBlocking {
        assertEquals("Playlist", safeArchiveName("..."))
        val plan = planPlaylistArchive("Name", listOf(track("a", "FILE"), track("b", "file"))) {
            ArchiveAudio(flac.size.toLong()) { ByteArrayInputStream(flac) }
        }
        assertTrue(plan.entries[1].path!!.endsWith(" (2).flac"))
        val name = safeArchiveName("../\\bad:\nname")
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertFalse(name.contains(':'))
        assertFalse(name.contains('\n'))
        assertTrue(safeArchiveName("界".repeat(1_000)).toByteArray().size < 240)
    }

    @Test
    fun `truncated originals fail export rather than creating misleading manifests`() = runBlocking {
        val plan = planPlaylistArchive("Name", listOf(track("a"))) {
            ArchiveAudio(flac.size.toLong() + 1) { ByteArrayInputStream(flac) }
        }
        try {
            writePlaylistArchive(plan, ByteArrayOutputStream())
            fail("Expected size mismatch")
        } catch (_: IOException) { }
    }

    @Test
    fun `streaming reads stay bounded and cancellation stops before remaining tracks`() {
        var opens = 0
        try {
            runBlocking {
                val audio = ByteArray(160_000).also { flac.copyInto(it) }
                val plan = planPlaylistArchive("Name", listOf(track("a"), track("b"))) {
                    ArchiveAudio(audio.size.toLong()) {
                        opens++
                        object : ByteArrayInputStream(audio) {
                            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                                assertTrue(length <= 64 * 1024)
                                return super.read(buffer, offset, length)
                            }
                        }
                    }
                }
                writePlaylistArchive(plan, ByteArrayOutputStream()) { _, _ -> cancel() }
                currentCoroutineContext().ensureActive()
                fail("Expected cancellation")
            }
        } catch (_: CancellationException) { }
        assertEquals(3, opens) // Two header probes and only the first exported track.
    }

    @Test
    fun `unsupported or unreadable files are missing and huge files are never opened`() = runBlocking {
        val plan = planPlaylistArchive("Name", listOf(track("unsupported"), track("unreadable"), track("large"))) {
            when (it.id) {
                "unsupported" -> ArchiveAudio(4) { ByteArrayInputStream("text".toByteArray()) }
                "unreadable" -> ArchiveAudio(10) { throw IOException("Removed") }
                else -> ArchiveAudio(Long.MAX_VALUE) { error("Must not open") }
            }
        }
        assertEquals(0, plan.available)
        try {
            writePlaylistArchive(plan, ByteArrayOutputStream())
            fail("Empty audio archives must not be exported")
        } catch (_: IllegalArgumentException) { }
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes())
                zip.closeEntry()
            }
        }
    }
}
