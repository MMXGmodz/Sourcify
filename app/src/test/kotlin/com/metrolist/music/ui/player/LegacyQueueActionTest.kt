package com.metrolist.music.ui.player

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w892dp-h412dp-land-xhdpi")
class LegacyQueueActionTest {
    @Test fun legacyLandscapePlayerCanStillOpenTheQueue() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        var opens = 0
        try {
            activity.setContent { MaterialTheme {
                PlayerTransportActions(false, { opens++ }) { Box(Modifier.height(80.dp)) }
            } }
            val root = activity.window.decorView
            repeat(2) {
                root.measure(View.MeasureSpec.makeMeasureSpec(1784, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 1784, 824)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            }
            val time = SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                MotionEvent.obtain(time, time + if (action == MotionEvent.ACTION_UP) 50 else 0, action, 100f, 200f, 0).also { root.dispatchTouchEvent(it); it.recycle() }
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            assertEquals("Legacy landscape must retain a queue action after removing the footer", 1, opens)
        } finally { controller.pause().stop().destroy() }
    }
}
