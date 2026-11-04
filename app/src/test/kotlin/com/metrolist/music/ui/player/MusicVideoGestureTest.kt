package com.metrolist.music.ui.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w360dp-h800dp-mdpi")
class MusicVideoGestureTest {
    @Test fun singleTapTogglesControlsAndDoubleTapsSeekOnTheirOwnSide() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + clock)
        val recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        var time = 0L
        var toggles = 0
        val seeks = mutableListOf<Boolean>()
        fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
        try {
            controller.get().setContent(parent = recomposer) { MaterialTheme {
                Box(Modifier.fillMaxSize().musicVideoGestures("Controls", "Backward", "Forward",
                    onToggle = { toggles++ }, onSeek = { seeks += it; true }))
            } }
            val root = controller.get().window.decorView
            repeat(8) {
                root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 360, 800)
                Snapshot.sendApplyNotifications()
                time += 16_666_667
                clock.sendFrame(time)
                idle(16)
                val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                bitmap.recycle()
            }
            fun tap(x: Float) {
                val down = SystemClock.uptimeMillis()
                MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, 400f, 0).also {
                    root.dispatchTouchEvent(it); it.recycle()
                }
                idle(40)
                MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, 400f, 0).also {
                    root.dispatchTouchEvent(it); it.recycle()
                }
                idle(80)
            }
            tap(40f)
            idle(500)
            assertEquals(1, toggles)
            assertEquals(emptyList<Boolean>(), seeks)
            tap(320f); tap(320f)
            idle(500)
            assertEquals(listOf(true), seeks)
            assertEquals("Double-tap must not toggle the controls", 1, toggles)
            tap(40f); tap(40f)
            idle(500)
            assertEquals(listOf(true, false), seeks)
            assertEquals(1, toggles)
        } finally {
            controller.pause().stop().destroy()
            recomposer.cancel()
            scope.cancel()
        }
    }
}
