package com.metrolist.music.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Base64

class DownloadArtworkStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val url = "https://lh3.googleusercontent.com/album=w60-h60-l90-rj"
    private val png = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=",
    )

    @Test
    fun `downloaded cover survives store restart and resolves resized requests`() {
        val directory = temporaryFolder.newFolder()
        val store = DownloadArtworkStore(directory)
        store.retain("track", listOf(url))
        assertTrue(store.save(url, "image/png; charset=binary", png.size.toLong(), png.inputStream()))
        val restarted = DownloadArtworkStore(directory)
        assertArrayEquals(png, restarted.fileFor(url.replace("60", "1080"))!!.readBytes())
    }

    @Test
    fun `known CDN sizes share identity while origins and signed queries remain separate`() {
        val key = DownloadArtworkStore.artworkKey(url)
        assertEquals(key, DownloadArtworkStore.artworkKey(url.replace("w60-h60-l90-rj", "s1080-c")))
        assertNotEquals(key, DownloadArtworkStore.artworkKey(url.replace("lh3.", "yt3.")))
        assertNotEquals(key, DownloadArtworkStore.artworkKey("$url?token=one"))
        assertNotEquals(DownloadArtworkStore.artworkKey("$url?token=one"),
            DownloadArtworkStore.artworkKey("$url?token=two"))
        assertNotEquals(DownloadArtworkStore.artworkKey("https://example.com/image=s60"),
            DownloadArtworkStore.artworkKey("https://example.com/image=s1080"))
        assertNull(DownloadArtworkStore.artworkKey("file:///secret"))
        assertNull(DownloadArtworkStore.artworkKey("https://user:pass@example.com/image"))
    }

    @Test
    fun `shared album is deleted only after its last downloaded track is removed`() {
        val store = DownloadArtworkStore(temporaryFolder.newFolder())
        store.retain("first", listOf(url))
        store.retain("second", listOf(url))
        assertTrue(store.save(url, "image/png", -1, png.inputStream()))
        store.remove("first")
        assertNotNull(store.fileFor(url))
        store.remove("second")
        assertNull(store.fileFor(url))
    }

    @Test
    fun `startup reconciles removed downloads without dropping retained artwork`() {
        val directory = temporaryFolder.newFolder()
        val store = DownloadArtworkStore(directory)
        val other = "https://example.com/other.png"
        store.retain("first", listOf(url))
        store.retain("second", listOf(other))
        assertTrue(store.save(url, "image/png", -1, png.inputStream()))
        assertTrue(store.save(other, "image/png", -1, png.inputStream()))
        DownloadArtworkStore(directory).retainOnly(setOf("second"))
        assertNull(store.fileFor(url))
        assertNotNull(store.fileFor(other))
    }

    @Test
    fun `HTML and mismatched image MIME are rejected without replacing a valid cover`() {
        val store = DownloadArtworkStore(temporaryFolder.newFolder())
        store.retain("track", listOf(url))
        assertTrue(store.save(url, "image/png", -1, png.inputStream()))
        assertFalse(store.save(url, "text/html", -1, "<html>error</html>".byteInputStream()))
        assertFalse(store.save(url, "image/png", -1, "<html>error</html>".byteInputStream()))
        assertFalse(store.save(url, "image/jpeg", -1, png.inputStream()))
        assertArrayEquals(png, store.fileFor(url)!!.readBytes())
    }

    @Test
    fun `unknown length oversized and truncated responses never become cached artwork`() {
        val directory = temporaryFolder.newFolder()
        val store = DownloadArtworkStore(directory, maxImageBytes = png.size.toLong())
        store.retain("track", listOf(url))
        assertFalse(store.save(url, "image/png", -1, (png + png).inputStream()))
        assertFalse(store.save(url, "image/png", png.size.toLong(), png.copyOf(16).inputStream()))
        assertNull(store.fileFor(url))
        assertTrue(directory.listFiles()!!.none { it.extension == "tmp" })
    }

    @Test
    fun `interrupted transfer preserves existing cover and removes temporary file`() {
        val directory = temporaryFolder.newFolder()
        val store = DownloadArtworkStore(directory)
        store.retain("track", listOf(url))
        assertTrue(store.save(url, "image/png", -1, png.inputStream()))
        val failingInput = object : InputStream() {
            override fun read(): Int = throw IOException("interrupted")
        }
        runCatching { store.save(url, "image/png", -1, failingInput) }
            .onSuccess { throw AssertionError("Expected interrupted transfer") }
        assertArrayEquals(png, store.fileFor(url)!!.readBytes())
        assertTrue(directory.listFiles()!!.none { it.extension == "tmp" })
    }

    @Test
    fun `storage cap keeps existing downloaded covers and rejects additional bytes`() {
        val store = DownloadArtworkStore(temporaryFolder.newFolder(), maxTotalBytes = png.size.toLong())
        val other = "https://example.com/other.png"
        store.retain("first", listOf(url))
        store.retain("second", listOf(other))
        assertTrue(store.save(url, "image/png", -1, png.inputStream()))
        assertFalse(store.save(other, "image/png", -1, png.inputStream()))
        assertNotNull(store.fileFor(url))
        assertNull(store.fileFor(other))
    }

    @Test
    fun `removed download cannot commit an artwork response still in flight`() {
        val store = DownloadArtworkStore(temporaryFolder.newFolder())
        store.retain("track", listOf(url))
        val input = object : ByteArrayInputStream(png) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                store.remove("track")
                return super.read(bytes, offset, length)
            }
        }
        assertFalse(store.save(url, "image/png", -1, input))
        assertNull(store.fileFor(url))
    }

    @Test
    fun `different artwork replaces track reference and clears unused previous file`() {
        val store = DownloadArtworkStore(temporaryFolder.newFolder())
        store.retain("track", listOf(url))
        assertTrue(store.save(url, "image/png", -1, png.inputStream()))
        store.retain("track", listOf("https://example.com/new.png"))
        assertNull(store.fileFor(url))
    }
}
