package com.metrolist.music.connect

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ConnectDevicesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("connect_test", Context.MODE_PRIVATE)
    private val buds = AudioRoute("bluetooth:one", "AirPods", DeviceIcon.HEADPHONES, true)
    private val phone = AudioRoute("phone", "Phone", DeviceIcon.PHONE, true)
    @Before fun clean() { prefs.edit().clear().commit() }

    @Test fun namesIconsAndColorSurviveRestartAndAutomaticResetsToSourcifyBlue() = runBlocking {
        val store = ConnectDeviceStore(prefs)
        store.remember(buds)
        store.edit(buds, "  Meine AirPods  ", DeviceIcon.EARBUDS)
        store.color(0xFFAB4433.toInt())
        val restarted = ConnectDeviceStore(prefs)
        val state = ConnectState(listOf(buds), restarted.state.value)
        assertEquals("Meine AirPods", state.name(buds))
        assertEquals(DeviceIcon.EARBUDS, state.icon(buds))
        assertEquals(0xFFAB4433.toInt(), state.color)
        restarted.remember(buds.copy(name = "Android renamed device"))
        assertEquals("Meine AirPods", restarted.state.value.devices.single().alias)
        restarted.edit(buds, "", null)
        restarted.color(null)
        val reset = ConnectState(listOf(buds), ConnectDeviceStore(prefs).state.value)
        assertEquals("AirPods", reset.name(buds))
        assertEquals(DeviceIcon.HEADPHONES, reset.icon(buds))
        assertEquals(CONNECT_BLUE, reset.color)
    }

    @Test fun historyTracksOnlyActualPlaybackAndTransferWaitsForAndroidConfirmation() = runBlocking {
        val backend = FakeRoutes(listOf(phone, buds.copy(selected = false)))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = ConnectDeviceStore(prefs)
        val controller = ConnectController(context, scope, backend, store)
        try {
            controller.setPlaying(false)
            backend.emit(listOf(buds))
            assertTrue(store.state.value.devices.isEmpty())
            controller.setPlaying(true)
            withTimeout(5000) { store.state.first { it.devices.any { device -> device.id == buds.id } } }
            controller.setPlaying(false)
            val second = buds.copy(id = "bluetooth:two", name = "Speaker")
            backend.emit(listOf(phone, second.copy(selected = false)))
            assertTrue(controller.select(second.id))
            assertEquals("phone", controller.state.value.active?.id)
            backend.emit(listOf(second))
            assertEquals(second.id, controller.state.value.active?.id)
            assertEquals(listOf(buds.id), store.state.value.devices.map { it.id })
            assertFalse(controller.select("offline"))
            controller.close()
            assertFalse(backend.started)
            controller.refresh()
            assertFalse(backend.started)
        } finally { controller.close(); scope.cancel() }
    }

    @Test fun deviceIdentityDoesNotDependOnAliasAndNewSystemNameKeepsCustomization() = runBlocking {
        val store = ConnectDeviceStore(prefs)
        store.remember(buds)
        store.edit(buds, "Custom", DeviceIcon.EARBUDS)
        store.remember(buds.copy(name = "New Android name"))
        store.remember(buds.copy(id = "different", selected = false))
        assertEquals(2, store.state.value.devices.size)
        val remembered = store.state.value.devices.single { it.id == buds.id }
        assertEquals("New Android name", remembered.name)
        assertEquals("Custom", remembered.alias)
        assertEquals(DeviceIcon.EARBUDS, remembered.icon)
        store.remember(phone)
        assertEquals(2, store.state.value.devices.size)
    }

    @Test fun androidSystemRoutingRequiresBothNearbyDeviceGrants() {
        assertTrue(connectBluetoothPermissions(30).isEmpty())
        assertEquals(setOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_SCAN),
            connectBluetoothPermissions(31).toSet())
        assertEquals(connectBluetoothPermissions(31).toList(), connectBluetoothPermissions(36).toList())
    }

    @Test fun unreadablePreferencesFallBackWithoutCrashing() {
        prefs.edit().putString("devices", "not JSON").commit()
        val state = ConnectDeviceStore(prefs).state.value
        assertTrue(state.devices.isEmpty())
        assertEquals(CONNECT_BLUE, ConnectState(preferences = state).color)
    }

    private class FakeRoutes(private var routes: List<AudioRoute>) : AudioRoutes {
        var started = false
        private var changed: ((List<AudioRoute>) -> Unit)? = null
        override fun start(changed: (List<AudioRoute>) -> Unit, failed: () -> Unit) { started = true; this.changed = changed; changed(routes) }
        fun emit(routes: List<AudioRoute>) { this.routes = routes; changed?.invoke(routes) }
        override fun select(id: String) = routes.any { it.id == id }
        override fun close() { started = false; changed = null }
    }
}
