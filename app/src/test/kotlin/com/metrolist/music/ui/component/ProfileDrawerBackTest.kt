package com.metrolist.music.ui.component

import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w412dp-h892dp-xhdpi")
class ProfileDrawerBackTest {
    @Test fun androidBackClosesTheProfileInsteadOfLeavingTheUnderlyingScreen() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clock)
        val recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        lateinit var state: DrawerState
        var fallbackCalls = 0
        activity.onBackPressedDispatcher.addCallback(activity, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { fallbackCalls++ }
        })
        try {
            activity.setContent(parent = recomposer) {
                MaterialTheme {
                    state = rememberDrawerState(DrawerValue.Open)
                    ProfileNavigationDrawer(state, { ProfileDrawerContent("User", null, true, false, {}, {}, {}) }) {
                        Text("Home")
                    }
                }
            }
            val root = activity.window.decorView
            var time = 0L
            fun frames(count: Int) {
                repeat(count) {
                    root.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1784, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, 824, 1784)
                    Snapshot.sendApplyNotifications()
                    time += 16_666_667
                    clock.sendFrame(time)
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                }
            }
            frames(8)
            assertTrue(state.isOpen)
            activity.onBackPressedDispatcher.onBackPressed()
            frames(90)
            assertEquals("Drawer must handle Back before the underlying screen", 0, fallbackCalls)
            assertTrue("Profile closes after Back", state.isClosed)
        } finally {
            controller.pause().stop().destroy()
            recomposer.cancel()
            scope.cancel()
        }
    }
}
