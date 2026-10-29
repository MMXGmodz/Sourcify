package com.metrolist.music.jam

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.security.SecureRandom
import java.util.UUID

/** Ephemeral LAN session. Player callbacks run on the application dispatcher. */
class JamServer(
    private val asset: (String) -> ByteArray,
    private val playback: () -> JSONObject,
    private val search: suspend (String) -> List<JSONObject>,
    private val add: (JSONObject, String) -> Unit,
    private val remove: (String, String) -> Unit,
    private val control: (String) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private data class Guest(val id: String, val name: String)
    private class Rejected(val status: Int, override val message: String) : Exception(message)
    private val random = SecureRandom()
    private val guests = linkedMapOf<String, Guest>()
    private val tracks = linkedMapOf<String, JSONObject>()
    private val attempts = mutableMapOf<String, Pair<Long, Int>>()
    private var server: ServerSocket? = null
    private var scope: CoroutineScope? = null
    private val sockets = java.util.concurrent.ConcurrentHashMap.newKeySet<Socket>()
    private var hosts = emptySet<String>()
    private var sessionId = ""
    private var code = ""
    private var hostName = ""
    var locked = false
    var controlAllowed = false
    val active get() = server != null
    var links = emptyList<String>()
        private set

    fun start(name: String) {
        stop()
        val addresses = lanAddresses()
        require(addresses.isNotEmpty()) { "Verbinde dein Handy mit einem WLAN und versuche es erneut." }
        val listener = ServerSocket(0, 16, InetAddress.getByName("0.0.0.0"))
        server = listener
        sessionId = UUID.randomUUID().toString()
        code = (100000 + random.nextInt(900000)).toString()
        hostName = name.trim().take(32).ifBlank { "Sourcify" }
        locked = false
        controlAllowed = false
        hosts = (addresses + listOf("127.0.0.1", "localhost")).map { "$it:${listener.localPort}" }.toSet()
        links = addresses.map { "http://$it:${listener.localPort}/#$code" }
        val runningScope = CoroutineScope(SupervisorJob() + dispatcher)
        scope = runningScope
        // ponytail: 16 simultaneous short HTTP requests; no persistent connections for 20 guests.
        val slots = Semaphore(16)
        runningScope.launch(Dispatchers.IO) {
            while (isActive && !listener.isClosed) {
                val socket = try { listener.accept() } catch (_: Exception) { break }
                if (!slots.tryAcquire()) { socket.close(); continue }
                sockets.add(socket)
                launch {
                    try { serve(socket) } finally { sockets.remove(socket); socket.close(); slots.release() }
                }
            }
        }
    }

    fun stop() {
        server?.close()
        server = null
        scope?.cancel()
        scope = null
        sockets.forEach { runCatching { it.close() } }
        sockets.clear()
        guests.clear()
        tracks.clear()
        attempts.clear()
        links = emptyList()
    }

    fun kick(id: String) {
        guests.entries.removeAll { it.value.id == id }
        locked = true
    }

    fun state(memberId: String = "host"): JSONObject {
        if (!active) return JSONObject().put("active", false)
        val result = playback()
        val names = guests.values.associate { it.id to it.name } + ("host" to hostName)
        val queue = result.getJSONArray("queue")
        for (i in 0 until queue.length()) {
            val entry = queue.getJSONObject(i)
            entry.put("addedByName", names[entry.optString("addedBy")] ?: "Gast")
        }
        result.put("active", true).put("sessionId", sessionId).put("revision", 0)
            .put("hostName", hostName).put("locked", locked).put("controlAllowed", controlAllowed)
            .put("memberId", memberId).put("participants", JSONArray().apply {
                put(JSONObject().put("id", "host").put("name", hostName).put("host", true))
                guests.values.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("host", false)) }
            })
        if (memberId == "host") result.put("code", code).put("links", JSONArray(links))
        return result
    }

    private suspend fun serve(socket: Socket) {
        socket.soTimeout = 15_000
        try {
            reject(!privateAddress(socket.inetAddress), 403, "Nur im selben WLAN verfügbar.")
            val input = BufferedInputStream(socket.getInputStream())
            var budget = 8192
            fun line(): String {
                val bytes = java.io.ByteArrayOutputStream()
                while (true) {
                    reject(--budget < 0, 413, "Anfrage zu groß.")
                    val byte = input.read()
                    reject(byte < 0, 400, "Unvollständige Anfrage.")
                    if (byte == 10) break
                    if (byte != 13) bytes.write(byte)
                }
                return bytes.toString("UTF-8")
            }
            val request = line().split(' ')
            reject(request.size != 3 || request[2] != "HTTP/1.1", 400, "Ungültige Anfrage.")
            val headers = mutableMapOf<String, String>()
            while (true) {
                val header = line()
                if (header.isEmpty()) break
                val colon = header.indexOf(':')
                reject(colon < 1, 400, "Ungültiger Header.")
                val key = header.substring(0, colon).lowercase()
                reject(headers.containsKey(key), 400, "Doppelter Header.")
                headers[key] = header.substring(colon + 1).trim()
            }
            val host = headers["host"]
            reject(host !in hosts, 403, "Ungültiger Host.")
            headers["origin"]?.let {
                val origin = runCatching { URI(it) }.getOrNull()
                reject(origin?.scheme != "http" || origin.rawAuthority != host, 403, "Ungültiger Ursprung.")
            }
            reject(headers.containsKey("transfer-encoding"), 400, "Ungültige Übertragung.")
            val path = request[1]
            val files = mapOf("/" to ("index.html" to "text/html"), "/jam.js" to ("guest.js" to "text/javascript"),
                "/jam.css" to ("guest.css" to "text/css"), "/mark.svg" to ("mark.svg" to "image/svg+xml"))
            if (request[0] == "GET" && path in files) {
                val file = files.getValue(path)
                respond(socket, 200, file.second, asset(file.first))
                return
            }
            reject(!path.startsWith("/api/"), 404, "Nicht gefunden.")
            reject(request[0] != if (path == "/api/state") "GET" else "POST", 405, "Ungültige Methode.")
            val data = if (request[0] == "POST") {
                reject(headers["content-type"]?.substringBefore(';') != "application/json", 415, "JSON erforderlich.")
                val length = headers["content-length"]?.toIntOrNull()
                reject(length == null || length < 2 || length > 8192, 413, "Anfrage zu groß.")
                val bytes = ByteArray(length!!)
                var read = 0
                while (read < bytes.size) {
                    val count = input.read(bytes, read, bytes.size - read)
                    reject(count < 0, 400, "Unvollständige Anfrage.")
                    read += count
                }
                try { JSONObject(String(bytes, Charsets.UTF_8)) } catch (_: Exception) { throw Rejected(400, "Ungültiges JSON.") }
            } else JSONObject()
            val result = withContext(dispatcher) {
                reject(!active, 401, "Der Jam wurde beendet.")
                if (path == "/api/join") {
                    val ip = socket.inetAddress.hostAddress!!
                    val now = System.currentTimeMillis()
                    attempts.entries.removeAll { now - it.value.first > 60_000 }
                    reject(attempts.size >= 128 && ip !in attempts, 429, "Bitte später erneut versuchen.")
                    val previous = attempts[ip] ?: (now to 0)
                    attempts[ip] = previous.first to (previous.second + 1)
                    reject(previous.second >= 12, 429, "Bitte eine Minute warten.")
                    reject(locked, 403, "Der Host hat neue Beitritte gesperrt.")
                    reject(data.optString("code") != code, 403, "Ungültiger Sitzungscode.")
                    reject(guests.size >= 20, 409, "Der Jam ist voll.")
                    val name = data.optString("name").trim()
                    reject(name.isBlank() || name.length > 32 || name.any { it.isISOControl() }, 400, "Bitte einen Namen mit maximal 32 Zeichen angeben.")
                    val token = ByteArray(24).also(random::nextBytes).joinToString("") { "%02x".format(it) }
                    val guest = Guest(UUID.randomUUID().toString(), name)
                    guests[token] = guest
                    JSONObject().put("token", token).put("sessionId", sessionId).put("memberId", guest.id).put("state", state(guest.id))
                } else {
                    val token = headers["authorization"]?.removePrefix("Bearer ")
                    val guest = guests[token] ?: throw Rejected(401, "Deine Sitzung ist abgelaufen. Bitte erneut beitreten.")
                    when (path) {
                        "/api/state" -> state(guest.id)
                        "/api/leave" -> { guests.remove(token); JSONObject().put("left", true) }
                        "/api/search" -> {
                            val query = data.optString("q").trim()
                            reject(query.isEmpty() || query.length > 200, 400, "Ungültige Suche.")
                            val found = withContext(Dispatchers.IO) { withTimeout(90_000) { search(query) } }.take(40)
                            reject(guests[token] != guest, 401, "Sitzung beendet.")
                            found.forEach { tracks[it.getString("id")] = it }
                            while (tracks.size > 400) tracks.remove(tracks.keys.first())
                            JSONObject().put("tracks", JSONArray(found))
                        }
                        "/api/add" -> {
                            val id = data.optString("id")
                            reject(!id.matches(Regex("[A-Za-z0-9_-]{11}")), 400, "Bitte einen Song aus der Suche auswählen.")
                            val queue = state(guest.id).getJSONArray("queue")
                            reject(queue.length() >= 500, 409, "Die Warteschlange ist voll.")
                            val track = tracks[id] ?: (0 until queue.length()).map { queue.getJSONObject(it).getJSONObject("track") }.firstOrNull { it.optString("id") == id }
                            reject(track == null, 400, "Bitte den Song erneut suchen.")
                            add(track!!, guest.id)
                            state(guest.id)
                        }
                        "/api/remove" -> {
                            val snapshot = state(guest.id)
                            val queue = snapshot.getJSONArray("queue")
                            val id = data.optString("entryId")
                            val index = (0 until queue.length()).firstOrNull { queue.getJSONObject(it).optString("entryId") == id } ?: -1
                            reject(index <= snapshot.getInt("queueIndex") || index < 0 || queue.getJSONObject(index).optString("addedBy") != guest.id, 403, "Du kannst nur eigene kommende Songs entfernen.")
                            remove(id, guest.id)
                            state(guest.id)
                        }
                        "/api/control" -> {
                            reject(!controlAllowed, 403, "Der Host steuert die Wiedergabe.")
                            val action = data.optString("action")
                            reject(action !in listOf("play", "pause", "next", "previous"), 400, "Ungültige Aktion.")
                            control(action)
                            JSONObject().put("accepted", true)
                        }
                        else -> throw Rejected(404, "Nicht gefunden.")
                    }
                }
            }
            respond(socket, 200, "application/json", result.toString().toByteArray())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { respond(socket, (e as? Rejected)?.status ?: 500, "application/json",
                JSONObject().put("error", (e as? Rejected)?.message ?: "Die Anfrage konnte nicht verarbeitet werden. Bitte erneut versuchen.").toString().toByteArray()) }
        }
    }

    private fun respond(socket: Socket, status: Int, type: String, body: ByteArray) {
        socket.getOutputStream().apply {
            write(("HTTP/1.1 $status Response\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' https:; connect-src 'self'; frame-ancestors 'none'\r\n\r\n").toByteArray())
            write(body)
            flush()
        }
    }

    private fun reject(condition: Boolean, status: Int, message: String) { if (condition) throw Rejected(status, message) }

    companion object {
        fun privateAddress(address: InetAddress): Boolean = address is Inet4Address && (address.isSiteLocalAddress || address.isLoopbackAddress)
        fun lanAddresses(): List<String> = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.name.matches(Regex("(rmnet|ccmni|pdp|v4-rmnet).*")) }
            .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("wifi") || it.name.startsWith("ap")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .filter { it is Inet4Address && it.isSiteLocalAddress }.mapNotNull { it.hostAddress }.distinct()
        fun validInvite(value: String): Boolean = runCatching {
            val uri = URI(value.trim())
            uri.scheme == "http" && uri.userInfo == null && uri.host.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}")) &&
                InetAddress.getByName(uri.host).let { it.isSiteLocalAddress && it is Inet4Address } && uri.port in 1..65535 &&
                uri.path in listOf("", "/") && uri.query == null && (uri.fragment == null || uri.fragment.matches(Regex("\\d{6}")))
        }.getOrDefault(false)
    }
}
