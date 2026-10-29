package com.metrolist.music.utils

import android.content.Context
import android.net.Uri
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
            assertArrayEquals(bytes, read())
            assertEquals(1, requests.get())
            assertTrue(CanvasVideoCache.bytesUsed(context) >= bytes.size)
            CanvasVideoCache.clear()
            assertArrayEquals(bytes, read())
            assertEquals(2, requests.get())
        } finally { server.stop(0); CanvasVideoCache.clear() }
    }
}
