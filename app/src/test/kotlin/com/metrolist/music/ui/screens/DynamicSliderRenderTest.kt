package com.metrolist.music.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.component.DynamicSlider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w360dp-h300dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DynamicSliderRenderTest {
    @Test fun maximumIntensityHasTallWavesAndZeroKeepsTheGlowingThumb() {
        listOf(1f, 0f).forEach { amplitude ->
            val bitmap = render(amplitude)
            try {
                val grayRows = (0 until bitmap.height).filter { y -> android.graphics.Color.green(bitmap.getPixel(326, y)) > 40 }
                assertTrue("Progress track must be visible", grayRows.isNotEmpty())
                val baseline = (grayRows.first() + grayRows.last()) / 2
                val waveTop = (0 until baseline - 3).firstOrNull { y ->
                    (45..190).any { x -> android.graphics.Color.green(bitmap.getPixel(x, y)) > 40 }
                }
                if (amplitude > 0) assertTrue("100% waves must rise at least 20dp", waveTop != null && baseline - waveTop >= 20)
                else assertTrue("0% must keep the played range flat", waveTop == null)
                val white = buildList {
                    for (y in baseline - 14..baseline + 14) for (x in 205..285) {
                        val pixel = bitmap.getPixel(x, y)
                        if (android.graphics.Color.red(pixel) > 230 && android.graphics.Color.green(pixel) > 230 && android.graphics.Color.blue(pixel) > 230) add(x to y)
                    }
                }
                assertTrue("Thumb ring must remain visible", white.isNotEmpty())
                val cx = (white.minOf { it.first } + white.maxOf { it.first }) / 2
                val cy = (white.minOf { it.second } + white.maxOf { it.second }) / 2
                val halo = bitmap.getPixel(cx + 15, cy)
                assertTrue("Thumb must keep a strong cover-colored halo even at 0%", android.graphics.Color.green(halo) > 60 && android.graphics.Color.green(halo) - android.graphics.Color.blue(halo) > 25)
            } finally { bitmap.recycle() }
        }
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    private fun render(amplitude: Float): Bitmap {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            val activity = controller.get()
            activity.setContent {
                MaterialTheme {
                    Box(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                        DynamicSlider(remember { SliderState(value = .72f, trackRange = 0f..1f) }, amplitude, {}, {}, color = Color(0xFF00E575))
                    }
                }
            }
            val root = activity.window.decorView
            fun layout() {
                root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 360, 300)
            }
            layout()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            layout()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            return Bitmap.createBitmap(360, 300, Bitmap.Config.ARGB_8888).also { bitmap ->
                root.draw(Canvas(bitmap))
                val output = File("build/ui-previews/dynamic-intensity-$amplitude.png").apply { parentFile.mkdirs() }
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally { controller.pause().stop().destroy() }
    }
}
