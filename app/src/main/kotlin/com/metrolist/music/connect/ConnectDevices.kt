package com.metrolist.music.connect

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// Android requires both Nearby Devices grants to expose and switch system Bluetooth routes.
internal fun connectBluetoothPermissions(sdk: Int): Array<String> = if (sdk >= 31)
    arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_SCAN) else emptyArray()

internal const val CONNECT_BLUE = 0xFF8ED8FF.toInt()
internal enum class DeviceIcon { PHONE, HEADPHONES, EARBUDS, SPEAKER, CAR, TV, COMPUTER }
internal data class AudioRoute(val id: String, val name: String, val icon: DeviceIcon, val selected: Boolean = false)
internal data class SavedDevice(val id: String, val name: String, val alias: String? = null, val icon: DeviceIcon? = null)
internal data class ConnectPreferences(val devices: List<SavedDevice> = emptyList(), val color: Int? = null)
internal data class ConnectState(val routes: List<AudioRoute> = emptyList(), val preferences: ConnectPreferences = ConnectPreferences()) {
    val active: AudioRoute? get() = routes.firstOrNull { it.selected }
    val color: Int get() = preferences.color ?: CONNECT_BLUE
    fun name(route: AudioRoute): String = preferences.devices.find { it.id == route.id }?.alias ?: route.name
    fun icon(route: AudioRoute): DeviceIcon = preferences.devices.find { it.id == route.id }?.icon ?: route.icon
}

internal class ConnectDeviceStore(private val prefs: SharedPreferences) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    private fun read(): ConnectPreferences = try {
        val json = JSONObject(prefs.getString("devices", "{}").orEmpty())
        val devices = json.optJSONArray("history") ?: JSONArray()
        ConnectPreferences((0 until minOf(devices.length(), 100)).mapNotNull { index ->
            val item = devices.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").takeIf { it.isNotBlank() && it.length <= 512 } ?: return@mapNotNull null
            SavedDevice(id, item.optString("name").take(100), item.optString("alias").trim().take(80).ifBlank { null },
                DeviceIcon.entries.find { it.name == item.optString("icon") })
        }.distinctBy { it.id }, if (json.has("color")) json.getInt("color") or 0xFF000000.toInt() else null)
    } catch (_: Exception) { ConnectPreferences() }

    suspend fun remember(route: AudioRoute) = update { state ->
        if (route.id == "phone") state else {
            val old = state.devices.find { it.id == route.id }
            if (old?.name == route.name) state else state.copy(devices =
                (listOf((old ?: SavedDevice(route.id, route.name, icon = route.icon)).copy(name = route.name.take(100))) + state.devices.filter { it.id != route.id }).take(100))
        }
    }
    suspend fun edit(route: AudioRoute, alias: String, icon: DeviceIcon?) = update { state ->
        val replacement = SavedDevice(route.id, route.name.take(100), alias.trim().take(80).ifBlank { null }, icon ?: route.icon)
        state.copy(devices = (listOf(replacement) + state.devices.filter { it.id != route.id }).take(100))
    }
    suspend fun color(color: Int?) = update { it.copy(color = color?.or(0xFF000000.toInt())) }
    private suspend fun update(change: (ConnectPreferences) -> ConnectPreferences) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = change(mutable.value)
            if (next == mutable.value) return@withLock
            val history = JSONArray()
            next.devices.forEach { device -> history.put(JSONObject().put("id", device.id).put("name", device.name)
                .put("alias", device.alias).put("icon", device.icon?.name)) }
            val json = JSONObject().put("history", history).put("color", next.color)
            check(prefs.edit().putString("devices", json.toString()).commit())
            mutable.value = next
        }
    }
}

internal interface AudioRoutes {
    fun start(changed: (List<AudioRoute>) -> Unit, failed: () -> Unit)
    fun select(id: String): Boolean
    fun close()
}

internal class ConnectController(
    context: Context, private val scope: CoroutineScope,
    private val backend: AudioRoutes = androidAudioRoutes(context),
    internal val store: ConnectDeviceStore = ConnectDeviceStore(context.getSharedPreferences("sourcify_connect", Context.MODE_PRIVATE)),
) {
    private val routes = MutableStateFlow(emptyList<AudioRoute>())
    private val mutableFailure = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val failures = mutableFailure.asSharedFlow()
    val state = combine(routes, store.state, ::ConnectState).stateIn(scope, SharingStarted.Eagerly, ConnectState())
    private var playing = false
    private var closed = false
    init { refresh() }
    fun refresh() {
        if (closed) return
        runCatching { backend.close() }
        try { backend.start({ outputs -> routes.value = outputs; rememberActive() }, { mutableFailure.tryEmit(Unit) }) }
        catch (_: Exception) { routes.value = emptyList() }
    }
    fun setPlaying(value: Boolean) { playing = value; rememberActive() }
    private fun rememberActive() {
        if (!playing || closed) return
        val active = routes.value.firstOrNull { it.selected } ?: return
        scope.launch { try { store.remember(active) } catch (_: java.io.IOException) { mutableFailure.emit(Unit) }
            catch (_: IllegalStateException) { mutableFailure.emit(Unit) } }
    }
    fun select(id: String): Boolean = !closed && try { backend.select(id) } catch (_: Exception) { false }
    fun close() { closed = true; runCatching { backend.close() } }
}
