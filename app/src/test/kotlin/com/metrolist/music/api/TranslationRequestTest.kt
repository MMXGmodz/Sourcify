package com.metrolist.music.api

import java.net.ServerSocket
import java.net.SocketException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationRequestTest {
    @Test
    fun `cancelling a streaming response interrupts the blocked socket read`() = runBlocking {
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val bodyReadStarted = CompletableDeferred<Unit>()
            val closed = async(Dispatchers.IO) {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) Unit
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n".toByteArray())
                        flush()
                    }
                    try {
                        reader.read()
                    } catch (_: SocketException) {
                        // Socket cancellation can close with EOF or a TCP reset.
                        -1
                    }
                }
            }
            val request = launch(Dispatchers.IO) {
                withTranslationResponse(OkHttpClient(), Request.Builder().url("http://127.0.0.1:${server.localPort}/").build()) {
                    bodyReadStarted.complete(Unit)
                    it.body.source().readUtf8Line()
                }
            }
            withTimeout(5000) {
                bodyReadStarted.await()
                request.cancelAndJoin()
                assertTrue(request.isCancelled)
                assertEquals(-1, closed.await())
            }
        }
    }
}
