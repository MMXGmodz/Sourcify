package com.metrolist.music.ui.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import org.junit.Assert.assertEquals
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
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CanvasPortraitRenderTest {
    @Test fun portraitImageKeepsAllFourEdgesAndSquarePixelsAtTheDefaultZoom() {
        val source = Bitmap.createBitmap(180, 320, Bitmap.Config.ARGB_8888)
        for (y in 0 until source.height) for (x in 0 until source.width) {
            source.setPixel(x, y, when {
                y < 8 -> Color.RED
                y >= source.height - 8 -> Color.BLUE
                x < 8 -> Color.GREEN
                x >= source.width - 8 -> Color.YELLOW
                (x / 20 + y / 20) % 2 == 0 -> Color.WHITE
                else -> Color.BLACK
            })
        }
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            controller.get().setContent {
                Image(painter = BitmapPainter(source.asImageBitmap()), contentDescription = null,
                    contentScale = CanvasContentScale(), modifier = Modifier.fillMaxSize())
            }
            val root = controller.get().findViewById<View>(android.R.id.content)
            fun layout() {
                root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 360, 640)
            }
            layout()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            layout()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            val output = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(output))
            File("build/ui-previews/canvas-portrait-100-percent.png").apply { parentFile?.mkdirs() }
                .outputStream().use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertEquals(Color.RED, output.getPixel(180, 4))
            assertEquals(Color.BLUE, output.getPixel(180, 635))
            assertEquals(Color.GREEN, output.getPixel(4, 320))
            assertEquals(Color.YELLOW, output.getPixel(355, 320))
            // A 20px source checker becomes 40px in both directions.
            assertEquals(Color.WHITE, output.getPixel(100, 100))
            assertEquals(Color.BLACK, output.getPixel(140, 100))
            assertEquals(Color.BLACK, output.getPixel(100, 140))
            assertEquals(Color.WHITE, output.getPixel(140, 140))
            output.recycle()
        } finally {
            controller.pause().stop().destroy()
            source.recycle()
        }
    }
}
