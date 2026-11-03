package com.metrolist.music.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.Uri
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.constants.CanvasMobileHeightKey
import com.metrolist.music.constants.CanvasWifiHeightKey
import com.metrolist.music.constants.CanvasWifiOnlyKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkInfo
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class CanvasNetworkProfileTest {
    @Suppress("DEPRECATION")
    @Test fun switchingFromWifiToMobilePublishesTheMobileLimitEvenWhenBothAreAllowed() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val previousPrefs = context.dataStore.data.first()
        try {
            context.dataStore.edit {
                it[CanvasWifiOnlyKey] = false
                it[CanvasWifiHeightKey] = 720
                it[CanvasMobileHeightKey] = 240
            }
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val shadow = shadowOf(manager)
            fun connect(type: Int, transport: Int): Pair<android.net.Network, NetworkCapabilities> {
                shadow.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, type, 0, true, true))
                val network = requireNotNull(manager.activeNetwork)
                val caps = ShadowNetworkCapabilities.newInstance()
                shadowOf(caps).addTransportType(transport)
                shadow.setNetworkCapabilities(network, caps)
                shadow.networkCallbacks.toList().forEach { it.onCapabilitiesChanged(network, caps) }
                return network to caps
            }
            connect(ConnectivityManager.TYPE_WIFI, NetworkCapabilities.TRANSPORT_WIFI)
            val policy = CanvasNetworkPolicy.get(context)
            withTimeout(3_000) {
                policy.profile.first { it == CanvasNetworkProfile(true, 720) }
                while (shadow.networkCallbacks.isEmpty()) delay(10)
            }
            assertEquals("Canvas shares one network observer", 1, shadow.networkCallbacks.size)
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            val clock = BroadcastFrameClock()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clock)
            val recomposer = Recomposer(scope.coroutineContext)
            scope.launch { recomposer.runRecomposeAndApplyChanges() }
            var frameTime = 0L
            try {
                val observed = mutableListOf<CanvasVideoSource?>()
                val selection = CanvasSelection(uri = Uri.parse("content://fixtures/canvas.mp4"), startMs = 0, endMs = 8_000)
                controller.get().setContent(parent = recomposer) {
                    val source = rememberSelectedCanvas(selection, true)
                    SideEffect { observed += source }
                }
                fun composeFrame() {
                    val root = controller.get().window.decorView
                    repeat(5) {
                        root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
                        root.layout(0, 0, 360, 640)
                        Snapshot.sendApplyNotifications()
                        frameTime += 16_666_667L
                        clock.sendFrame(frameTime)
                        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                    }
                }
                composeFrame()
                composeFrame()
                val localSource = requireNotNull(observed.lastOrNull())
                val initialLastIndex = observed.lastIndex
                val beforeMobile = observed.size
                connect(ConnectivityManager.TYPE_MOBILE, NetworkCapabilities.TRANSPORT_CELLULAR)
                assertEquals(CanvasNetworkProfile(true, 240), withTimeout(3_000) {
                    policy.profile.first { it.maxVideoHeight == 240 }
                })
                composeFrame()
                composeFrame()
                assertTrue("Composition must observe the mobile profile", observed.size > beforeMobile)
                assertSame("A local Canvas must keep its source when the network profile changes", localSource, observed.last())
                val beforeWifiRestriction = observed.size
                context.dataStore.edit { it[CanvasWifiOnlyKey] = true }
                assertEquals(CanvasNetworkProfile(false, 240), withTimeout(3_000) {
                    policy.profile.first { !it.remoteAllowed }
                })
                composeFrame()
                composeFrame()
                assertTrue("Composition must observe the Wi-Fi-only restriction", observed.size > beforeWifiRestriction)
                assertSame("Wi-Fi-only restrictions must keep local Canvas available", localSource, observed.last())
                assertTrue("Network changes must never clear a local Canvas", observed.drop(initialLastIndex + 1).none { it == null })
            } finally {
                controller.pause().stop().destroy()
                recomposer.cancel()
                scope.cancel()
            }
        } finally {
            context.dataStore.edit { prefs ->
                fun <T> restore(key: Preferences.Key<T>) {
                    val previous = previousPrefs[key]
                    if (previous == null) prefs.remove(key) else prefs[key] = previous
                }
                restore(CanvasWifiOnlyKey)
                restore(CanvasWifiHeightKey)
                restore(CanvasMobileHeightKey)
            }
            val restoredPolicy = CanvasNetworkPolicy.get(context)
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val expected = CanvasNetworkProfile(restoredPolicy.mayLoadRemote(), canvasVideoHeight(previousPrefs, networkUsesMobileQuality(manager)))
            withTimeout(3_000) { restoredPolicy.profile.first { it == expected } }
        }
    }
}
