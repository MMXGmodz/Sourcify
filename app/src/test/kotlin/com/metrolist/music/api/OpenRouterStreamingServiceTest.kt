package com.metrolist.music.api

import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterStreamingServiceTest {
    @Test
    fun `stream collected in caller context emits ordered chunks then complete lines`() = runBlocking {
        val first = "{\"lines\":[\"uno\","
        val second = "\"dos\"]}"
        val chunks = collectResponse(event(first) + event(second) + "data: [DONE]\n\n")

        assertEquals(
            listOf(
                OpenRouterStreamingService.StreamChunk.Content(first),
                OpenRouterStreamingService.StreamChunk.Content(second),
                OpenRouterStreamingService.StreamChunk.Complete(listOf("uno", "dos")),
            ),
            chunks,
        )
    }

    @Test
    fun `incomplete translation emits count error instead of complete`() = runBlocking {
        val chunks = collectResponse(event("""{"lines":["uno"]}""") + "data: [DONE]\n\n")

        assertEquals(2, chunks.size)
        assertTrue((chunks.last() as OpenRouterStreamingService.StreamChunk.Error).message.contains("line count mismatch"))
    }

    @Test
    fun `provider HTTP and stream errors are preserved`() = runBlocking {
        val error = """{"error":{"message":"provider unavailable"}}"""
        assertEquals(
            listOf(OpenRouterStreamingService.StreamChunk.Error("Translation failed: provider unavailable")),
            collectResponse(error, "503 Service Unavailable"),
        )
        assertEquals(
            listOf(OpenRouterStreamingService.StreamChunk.Error("provider unavailable")),
            collectResponse("data: $error\n\n"),
        )
    }

    @Test
    fun `cancelling collection closes a blocked streaming socket`() = runBlocking {
        withOpenStream { flow ->
            val received = CompletableDeferred<Unit>()
            val collection = launch {
                flow.collect { received.complete(Unit) }
            }
            withTimeout(5000) {
                received.await()
                collection.cancelAndJoin()
            }
            assertTrue(collection.isCancelled)
        }
    }

    @Test
    fun `collector failure propagates unchanged and closes the stream`() = runBlocking {
        withOpenStream { flow ->
            val failure = IllegalStateException("collector failed")
            val chunks = mutableListOf<OpenRouterStreamingService.StreamChunk>()
            val result = runCatching {
                withTimeout(5000) {
                    flow.collect {
                        chunks.add(it)
                        throw failure
                    }
                }
            }
            val thrown = result.exceptionOrNull()
            assertTrue("Collector failure must propagate through coroutine stacktrace recovery",
                thrown === failure || thrown?.cause === failure)
            assertEquals(listOf(OpenRouterStreamingService.StreamChunk.Content("partial")), chunks)
        }
    }

    private fun event(content: String) =
        "data: {\"choices\":[{\"delta\":{\"content\":${JsonPrimitive(content)}}}]}\n\n"

    private fun translation(port: Int): Flow<OpenRouterStreamingService.StreamChunk> =
        OpenRouterStreamingService.streamTranslation(
            text = "one\ntwo",
            targetLanguage = "Spanish",
            apiKey = "",
            baseUrl = "http://127.0.0.1:$port/",
            model = "model",
            mode = "Translated",
        )

    private suspend fun collectResponse(
        body: String,
        status: String = "200 OK",
    ): List<OpenRouterStreamingService.StreamChunk> = coroutineScope {
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val response = async(Dispatchers.IO) {
                server.accept().use { socket ->
                    consumeRequest(socket)
                    val bytes = body.toByteArray()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 $status\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes)
                        flush()
                    }
                }
            }
            withTimeout(5000) {
                translation(server.localPort).toList().also { response.await() }
            }
        }
    }

    private suspend fun withOpenStream(block: suspend (Flow<OpenRouterStreamingService.StreamChunk>) -> Unit) = coroutineScope {
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val closed = async(Dispatchers.IO) {
                server.accept().use { socket ->
                    val reader = consumeRequest(socket)
                    val bytes = event("partial").toByteArray()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n".toByteArray())
                        write("${bytes.size.toString(16)}\r\n".toByteArray())
                        write(bytes)
                        write("\r\n".toByteArray())
                        flush()
                    }
                    try {
                        reader.read()
                    } catch (_: SocketException) {
                        -1
                    }
                }
            }
            block(translation(server.localPort))
            withTimeout(5000) { assertEquals(-1, closed.await()) }
        }
    }

    private fun consumeRequest(socket: Socket): java.io.BufferedReader {
        socket.soTimeout = 5000
        val reader = socket.getInputStream().bufferedReader()
        var length = 0
        while (true) {
            val line = reader.readLine() ?: error("Request ended before headers")
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                length = line.substringAfter(':').trim().toInt()
            }
        }
        repeat(length) { check(reader.read() != -1) }
        return reader
    }
}
