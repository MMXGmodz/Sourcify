package com.metrolist.music.ui.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w412dp-h892dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerProgressThumbTest {
    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun actualSliderKeepsTheThumbInsideTheLowTrackProfile() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        try {
            activity.setContent { MaterialTheme {
                Column(Modifier.fillMaxSize().padding(top = 80.dp, start = 20.dp, end = 20.dp)) {
                    val state = remember { SliderState(value = .3f) }
                    Slider(state = state, onValueChange = {}, thumb = { PlayerProgressThumb(Color.Red) },
                        track = { PlayerSliderTrack(it, trackHeight = 10.dp) })
                }
            } }
            val root = activity.window.decorView
            repeat(2) {
                root.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1784, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 824, 1784)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            }
            val bitmap = Bitmap.createBitmap(824, 1784, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val rows = mutableSetOf<Int>()
            for (y in 100 until 400) for (x in 50 until 775) {
                val c = bitmap.getPixel(x, y)
                if (android.graphics.Color.red(c) > 220 && android.graphics.Color.green(c) < 60 && android.graphics.Color.blue(c) < 60) rows += y
            }
            assertTrue("Visible progress marker must be painted", rows.isNotEmpty())
            assertTrue("Progress marker must not tower above the track", rows.max() - rows.min() + 1 <= 14)
            bitmap.recycle()
        } finally { controller.pause().stop().destroy() }
    }
}
