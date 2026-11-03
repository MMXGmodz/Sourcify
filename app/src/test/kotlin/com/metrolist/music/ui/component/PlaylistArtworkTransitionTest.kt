package com.metrolist.music.ui.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.theme.MetrolistTheme
import kotlinx.coroutines.*
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
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaylistArtworkTransitionTest {
    @Test fun whiteArtworkMergesIntoTheSameBackdropAtBothEdges() {
        for (dark in listOf(true, false)) {
            val backdrop = if (dark) Color(0xFF355653) else Color(0xFFE5ECE8)
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            val clock = BroadcastFrameClock()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clock)
            val recomposer = Recomposer(scope.coroutineContext)
            scope.launch { recomposer.runRecomposeAndApplyChanges() }
            try {
                val activity = controller.get()
                activity.setContent(parent = recomposer) {
                    MetrolistTheme(darkTheme = dark) {
                        Surface(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxSize().background(backdrop)) {
                                Spacer(Modifier.height(80.dp))
                                PlaylistArtworkHeader("Favoriten") { Box(Modifier.fillMaxSize().background(Color.White)) }
                            }
                        }
                    }
                }
                val root = activity.window.decorView
                repeat(8) { frame ->
                    root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, 360, 800)
                    Snapshot.sendApplyNotifications(); clock.sendFrame((frame + 1) * 16_666_667L)
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                }
                val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                val output = File("build/ui-previews/playlist-white-fade-$dark.png").apply { parentFile?.mkdirs() }
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                try {
                    val expected = backdrop.toArgb()
                    fun difference(pixel: Int): Int = listOf(16, 8, 0).maxOf { shift -> abs(((pixel shr shift) and 255) - ((expected shr shift) and 255)) }
                    assertTrue("Top artwork edge must not introduce a dark horizontal strip in dark=$dark", difference(bitmap.getPixel(4, 80)) <= 2)
                    assertTrue("Bottom artwork edge must merge without a black seam in dark=$dark", difference(bitmap.getPixel(4, 439)) <= 2)
                    assertTrue("White artwork must remain clear in the middle", android.graphics.Color.red(bitmap.getPixel(4, 240)) >= 245)
                } finally { bitmap.recycle() }
            } finally { controller.pause().stop().destroy(); recomposer.cancel(); scope.cancel() }
        }
    }
}
