package com.metrolist.music.utils

import android.content.Context
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.NetworkCapabilities
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkInfo
import org.robolectric.shadows.ShadowNetworkCapabilities
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class CanvasVideoCacheTest {
    @Suppress("DEPRECATION")
    private fun connect(context: Context, mobile: Boolean) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadow = shadowOf(manager)
        shadow.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
            if (mobile) ConnectivityManager.TYPE_MOBILE else ConnectivityManager.TYPE_WIFI, 0, true, true))
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addTransportType(if (mobile) NetworkCapabilities.TRANSPORT_CELLULAR else NetworkCapabilities.TRANSPORT_WIFI)
        shadow.setNetworkCapabilities(requireNotNull(manager.activeNetwork), caps)
    }

    @Test
    fun switchingNetworksStopsAnOpenRemoteVideoWithoutBlockingLocalReads() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        connect(context, mobile = false)
        val factory = DataSaverPolicy.get(context).guardVideo {
            androidx.media3.datasource.ByteArrayDataSource(byteArrayOf(1, 2, 3))
        }
        val remote = factory.createDataSource()
        try {
            remote.open(DataSpec(Uri.parse("https://example.com/video")))
            assertEquals(1, remote.read(ByteArray(1), 0, 1))
            connect(context, mobile = true)
            assertThrows(java.io.IOException::class.java) { remote.read(ByteArray(1), 0, 1) }
        } finally { remote.close() }
        val local = factory.createDataSource()
        try {
            local.open(DataSpec(Uri.parse("content://downloads/video")))
            assertEquals(1, local.read(ByteArray(1), 0, 1))
        } finally { local.close() }
    }

    @Test
    fun fullHdFormatCannotReadPreviouslyCachedLowResolutionBytes() {
        val requests = AtomicInteger()
        val low = ByteArray(1024) { 24 }
        val fullHd = ByteArray(8192) { 108 }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/loop.mp4") { response ->
            requests.incrementAndGet()
            val bytes = if (response.requestURI.query == "itag=137") fullHd else low
            response.sendResponseHeaders(200, bytes.size.toLong())
            response.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            connect(context, mobile = false)
            val base = "http://127.0.0.1:${server.address.port}/loop.mp4"
            val lowSource = CanvasVideoSource(Uri.parse("$base?itag=133"))
            val fullHdSource = CanvasVideoSource(Uri.parse("$base?itag=137"))
            val lowFactory = CanvasVideoCache.dataSource(context, lowSource)
            val fullHdFactory = CanvasVideoCache.dataSource(context, fullHdSource)
            CanvasVideoCache.clear()
            fun read(factory: androidx.media3.datasource.DataSource.Factory, source: CanvasVideoSource): ByteArray {
                val dataSource = factory.createDataSource()
                val output = java.io.ByteArrayOutputStream()
                try {
                    dataSource.open(DataSpec(source.uri))
                    val buffer = ByteArray(1024)
                    while (true) {
                        val count = dataSource.read(buffer, 0, buffer.size)
                        if (count == C.RESULT_END_OF_INPUT) break
                        output.write(buffer, 0, count)
                    }
                } finally { dataSource.close() }
                return output.toByteArray()
            }
            assertArrayEquals(low, read(lowFactory, lowSource))
            assertArrayEquals(fullHd, read(fullHdFactory, fullHdSource))
            assertArrayEquals(fullHd, read(fullHdFactory, fullHdSource))
            assertArrayEquals(low, read(lowFactory, lowSource))
            assertEquals(2, requests.get())
        } finally { server.stop(0); CanvasVideoCache.clear() }
    }

    @Test
    fun repeatedLoopReadsCacheAndClearingCacheFetchesAgain() {
        val requests = AtomicInteger()
        val bytes = ByteArray(8192) { (it % 127).toByte() }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/loop.mp4") { response ->
            requests.incrementAndGet()
            response.sendResponseHeaders(200, bytes.size.toLong())
            response.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            connect(context, mobile = false)
            val source = CanvasVideoSource(Uri.parse("http://127.0.0.1:${server.address.port}/loop.mp4"))
            val factory = CanvasVideoCache.dataSource(context, source)
            CanvasVideoCache.clear()
            fun read(): ByteArray {
                val dataSource = factory.createDataSource()
                val output = java.io.ByteArrayOutputStream()
                try {
                    dataSource.open(DataSpec(source.uri))
                    val buffer = ByteArray(1024)
                    while (true) {
                        val count = dataSource.read(buffer, 0, buffer.size)
                        if (count == C.RESULT_END_OF_INPUT) break
                        output.write(buffer, 0, count)
                    }
                } finally { dataSource.close() }
                return output.toByteArray()
            }
            assertArrayEquals(bytes, read())
            assertEquals(1, requests.get())
            connect(context, mobile = true)
            assertArrayEquals(bytes, read())
            assertEquals("Metered playback must use the cache", 1, requests.get())
            assertTrue(CanvasVideoCache.bytesUsed(context) >= bytes.size)
            CanvasVideoCache.clear()
            assertThrows(java.io.IOException::class.java) { read() }
            assertEquals("A cache miss on mobile must not contact the server", 1, requests.get())
            connect(context, mobile = false)
            assertArrayEquals(bytes, read())
            assertEquals(2, requests.get())
        } finally { server.stop(0); CanvasVideoCache.clear() }
    }
}
