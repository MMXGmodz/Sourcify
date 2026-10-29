package com.metrolist.music.jam

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class JamServerTest {
    @Test
    fun twoGuestsShareQueueWithOwnershipAndHostControls() {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val queue = JSONArray()
        var action = ""
        val song = JSONObject().put("id", "abcdefghijk").put("title", "Test song").put("artist", "Artist").put("duration", 180).put("thumbnail", "").put("kind", "song")
        val server = JamServer(
            asset = { "<html>Jam</html>".toByteArray() },
            playback = { JSONObject().put("queue", JSONArray(queue.toString())).put("queueIndex", if (queue.length() == 0) -1 else 0).put("playback", JSONObject().put("paused", true)) },
            search = { listOf(song) },
            add = { track, owner -> queue.put(JSONObject().put("entryId", UUID.randomUUID().toString()).put("id", track.getString("id")).put("track", track).put("addedBy", owner)) },
            remove = { id, _ -> for (i in queue.length() - 1 downTo 0) if (queue.getJSONObject(i).getString("entryId") == id) queue.remove(i) },
            control = { action = it }, dispatcher = dispatcher,
        )
        try {
            runBlocking(dispatcher) { server.start("MMXG") }
            val invitation = runBlocking(dispatcher) { server.links.first() }
            val code = URI(invitation).fragment
            fun request(path: String, body: JSONObject? = null, token: String? = null,
                origin: String? = null, host: String = "127.0.0.1:${URI(invitation).port}", type: String = "application/json"): Pair<Int, JSONObject> {
                java.net.Socket("127.0.0.1", URI(invitation).port).use { socket ->
                    socket.soTimeout = 5000
                    val payload = body?.toString()?.toByteArray() ?: ByteArray(0)
                    val headers = buildString {
                        append("${if (body != null) "POST" else "GET"} /api/$path HTTP/1.1\r\nHost: $host\r\n")
                        if (token != null) append("Authorization: Bearer $token\r\n")
                        if (origin != null) append("Origin: $origin\r\n")
                        if (body != null) append("Content-Type: $type\r\nContent-Length: ${payload.size}\r\n")
                        append("Connection: close\r\n\r\n")
                    }
                    socket.getOutputStream().apply { write(headers.toByteArray()); write(payload); flush() }
                    val reader = socket.getInputStream().bufferedReader()
                    val status = reader.readLine().split(' ')[1].toInt()
                    while (reader.readLine().isNotEmpty()) Unit
                    return status to JSONObject(reader.readText())
                }
            }
            assertEquals(401, request("state").first)
            assertEquals(403, request("join", JSONObject().put("code", "000000").put("name", "Guest")).first)
            fun join(name: String) = request("join", JSONObject().put("code", code).put("name", name)).also { assertEquals(200, it.first) }.second
            val a = join("Alice <script>")
            val b = join("Bob")
            val tokenA = a.getString("token")
            val tokenB = b.getString("token")
            assertEquals(3, request("state", token = tokenA).second.getJSONArray("participants").length())
            assertFalse(request("state", token = tokenA).second.has("code"))
            assertFalse(request("state", token = tokenA).second.has("links"))
            assertEquals(403, request("state", token = tokenA, origin = "http://evil.example").first)
            assertEquals(403, request("state", token = tokenA, host = "evil.example:12345").first)
            assertEquals(415, request("search", JSONObject().put("q", "song"), tokenA, type = "text/plain").first)
            assertEquals(413, request("search", JSONObject().put("q", "x".repeat(9000)), tokenA).first)
            assertEquals(400, request("add", JSONObject().put("id", "file:///private/music"), tokenA).first)
            assertEquals(200, request("search", JSONObject().put("q", "music"), tokenA).first)
            assertEquals(200, request("add", JSONObject().put("id", "abcdefghijk"), tokenA).first)
            assertEquals(200, request("add", JSONObject().put("id", "abcdefghijk"), tokenB).first)
            val shared = request("state", token = tokenA).second.getJSONArray("queue")
            assertEquals(2, shared.length())
            val ownPendingId = shared.getJSONObject(1).getString("entryId")
            assertNotEquals(shared.getJSONObject(0).getString("entryId"), ownPendingId)
            assertEquals(403, request("remove", JSONObject().put("entryId", ownPendingId), tokenA).first)
            assertEquals(200, request("remove", JSONObject().put("entryId", ownPendingId), tokenB).first)
            assertEquals(403, request("remove", JSONObject().put("entryId", shared.getJSONObject(0).getString("entryId")), tokenA).first)
            assertEquals(403, request("control", JSONObject().put("action", "play"), tokenA).first)
            runBlocking(dispatcher) { server.controlAllowed = true }
            assertEquals(200, request("control", JSONObject().put("action", "play"), tokenA).first)
            assertEquals("play", action)
            runBlocking(dispatcher) { server.kick(b.getString("memberId")) }
            assertEquals(401, request("state", token = tokenB).first)
            assertEquals(403, request("join", JSONObject().put("code", code).put("name", "New")).first)
            runBlocking(dispatcher) { server.locked = false }
            repeat(12) { request("join", JSONObject().put("code", "000000").put("name", "Guest")) }
            assertEquals(429, request("join", JSONObject().put("code", "000000").put("name", "Guest")).first)
            runBlocking(dispatcher) { server.stop(); server.start("New host") }
            val newBase = "http://127.0.0.1:${URI(server.links.first()).port}"
            val connection = URL("$newBase/api/state").openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", "Bearer $tokenA")
            try { assertEquals(401, connection.responseCode) } finally { connection.disconnect() }
        } finally {
            runBlocking(dispatcher) { server.stop() }
            dispatcher.close()
        }
    }

    @Test
    fun invitationValidationRejectsPublicHostsAndInjectedLinks() {
        assertTrue(JamServer.validInvite("http://192.168.1.20:12345/#123456"))
        assertTrue(JamServer.validInvite("http://10.0.0.20:12345/"))
        listOf("https://192.168.1.20:12345/", "http://8.8.8.8:12345/", "http://evil.example:12345/",
            "http://user@192.168.1.20:12345/", "http://192.168.1.20:12345/private", "http://192.168.1.20:12345/?secret=1",
            "http://192.168.1.20:12345/#bad", "javascript:alert(1)").forEach { assertFalse(it, JamServer.validInvite(it)) }
        assertFalse(JamServer.privateAddress(InetAddress.getByName("8.8.8.8")))
        assertTrue(JamServer.privateAddress(InetAddress.getByName("127.0.0.1")))
    }
}
