package com.metrolist.music.ui.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w360dp-h800dp-mdpi")
class ProfileDrawerResizeTest {
    @Test fun rotatingAClosedProfileDoesNotOpenItOrShiftTheCurrentPage() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clock)
        val recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        lateinit var state: DrawerState
        var content: LayoutCoordinates? = null
        var time = 0L
        val root = controller.get().window.decorView
        fun frames(width: Int, height: Int) {
            repeat(40) {
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, width, height)
                Snapshot.sendApplyNotifications()
                clock.sendFrame(time.also { time += 16_666_667 })
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                bitmap.recycle()
            }
        }
        try {
            controller.get().setContent(parent = recomposer) {
                MaterialTheme {
                    state = rememberDrawerState(DrawerValue.Closed)
                    ProfileNavigationDrawer(state, { Text("Profile") }) {
                        Box(Modifier.fillMaxSize().onGloballyPositioned { content = it }) { Text("Library") }
                    }
                }
            }
            frames(360, 800)
            assertTrue(state.isClosed)
            assertEquals(0f, requireNotNull(content).boundsInWindow().left, 1f)
            frames(800, 360)
            assertTrue("Rotation must preserve a closed profile", state.isClosed)
            assertEquals("The current page must stay on screen after rotation", 0f, requireNotNull(content).boundsInWindow().left, 1f)
            scope.launch { state.open() }
            frames(800, 360)
            assertTrue(state.isOpen)
            frames(360, 800)
            assertTrue("An explicitly opened profile must stay open on rotation", state.isOpen)
        } finally {
            controller.pause().stop().destroy()
            recomposer.cancel(); scope.cancel()
        }
    }
}
