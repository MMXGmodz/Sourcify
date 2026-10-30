package com.metrolist.music.utils

import android.app.Application
import android.net.Uri
import androidx.media3.datasource.*
import com.metrolist.music.playback.CachedStreamUrl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DownloadedCanvasTransferTest {
    @get:Rule val temporary = TemporaryFolder()
    private val uri = Uri.parse("https://example.org/canvas")
    private val stream = CachedStreamUrl(uri.toString(), mapOf("X-Test" to "present"), "test", true, 4)

    @Test fun boundedTransfersReassembleTheOriginalAndCloseEachSource() = runBlocking {
        val original = ByteArray(11) { it.toByte() }
        val requests = mutableListOf<DataSpec>()
        var closed = 0
        val factory = DataSource.Factory {
            val delegate = ByteArrayDataSource(original)
            object : DataSource by delegate {
                override fun open(dataSpec: DataSpec): Long {
                    requests += dataSpec
                    assertEquals("present", dataSpec.httpRequestHeaders["X-Test"])
                    return delegate.open(dataSpec)
                }
                override fun getResponseHeaders() = mapOf("Content-Range" to listOf("bytes 0-3/${original.size}"))
                override fun close() { closed++; delegate.close() }
            }
        }
        val output = temporary.newFile()
        copyCanvasBytes(factory, uri, stream, output)
        assertArrayEquals(original, output.readBytes())
        assertEquals(listOf(0L, 4L, 8L), requests.map { it.position })
        assertEquals(3, closed)
    }

    @Test fun truncatedSourceWithKnownTotalMustNotBecomeAnOfflineCanvas() = runBlocking {
        var closed = false
        val factory = DataSource.Factory {
            val delegate = ByteArrayDataSource(byteArrayOf(1, 2))
            object : DataSource by delegate {
                override fun getResponseHeaders() = mapOf("Content-Range" to listOf("bytes 0-3/10"))
                override fun close() { closed = true; delegate.close() }
            }
        }
        try {
            copyCanvasBytes(factory, uri, stream, temporary.newFile())
            fail("A partial source must not be accepted")
        } catch (_: IOException) { }
        assertTrue(closed)
    }

    @Test fun oversizedAndUnchunkedIncompleteSourcesAreRejected() = runBlocking {
        val tooLarge = DataSource.Factory { ByteArrayDataSource(ByteArray(20)) }
        try {
            copyCanvasBytes(tooLarge, uri, null, temporary.newFile(), maxBytes = 10)
            fail("Source limit must be enforced")
        } catch (_: IOException) { }
        val incomplete = DataSource.Factory {
            val delegate = ByteArrayDataSource(byteArrayOf(1, 2))
            object : DataSource by delegate {
                override fun open(dataSpec: DataSpec): Long { delegate.open(dataSpec); return 20 }
            }
        }
        try {
            copyCanvasBytes(incomplete, uri, null, temporary.newFile())
            fail("A known-length source must be complete")
        } catch (_: IOException) { }
    }
}
