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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.theme.MetrolistTheme
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.setClassicPlaylistCover
import com.metrolist.music.utils.playlistCoverStyleKey
import com.metrolist.music.utils.rememberClassicPlaylistCover
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.util.UUID
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaylistArtworkTransitionTest {
    @Test fun classicCoverPreferencePersistsAndIsIsolatedPerPlaylist() = runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = "cover-${UUID.randomUUID()}"
        val second = "cover-${UUID.randomUUID()}"
        try {
            assertEquals(null, context.dataStore.data.first()[playlistCoverStyleKey(first)])
            assertTrue(setClassicPlaylistCover(context, first, true))
            assertEquals(true, context.dataStore.data.first()[playlistCoverStyleKey(first)])
            assertEquals(null, context.dataStore.data.first()[playlistCoverStyleKey(second)])
            assertTrue(setClassicPlaylistCover(context, second, false))
            assertEquals(true, context.dataStore.data.first()[playlistCoverStyleKey(first)])
            assertEquals(false, context.dataStore.data.first()[playlistCoverStyleKey(second)])
            assertTrue(setClassicPlaylistCover(context, first, false))
            assertEquals(false, context.dataStore.data.first()[playlistCoverStyleKey(first)])
        } finally {
            context.safeDataStoreEdit { it.remove(playlistCoverStyleKey(first)); it.remove(playlistCoverStyleKey(second)) }
        }
    }

    @Test fun classicCoverKeepsSquareArtworkUnfaded() {
        var bounds = Rect.Zero
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clock)
        val recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        var time = 0L
        val root = activity.window.decorView
        fun frames() {
            repeat(12) {
                root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 360, 800)
                Snapshot.sendApplyNotifications()
                clock.sendFrame(time.also { time += 16_666_667L })
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            }
        }
        try {
            activity.setContent(parent = recomposer) {
                MetrolistTheme(darkTheme = true) {
                    Surface(Modifier.fillMaxSize()) {
                        Column {
                            PlaylistArtworkHeader("Favoriten", classic = true) {
                                Box(Modifier.fillMaxSize().background(Color.White).onGloballyPositioned { bounds = it.boundsInWindow() })
                            }
                        }
                    }
                }
            }
            frames()
            assertEquals("Classic artwork is square", bounds.width, bounds.height, 1f)
            assertEquals("Classic artwork uses the Top 50 cover size", 240f, bounds.width, 1f)
            assertTrue("Classic artwork stays inset", bounds.left >= 24f && bounds.right <= 336f)
            val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
            try {
                root.draw(Canvas(bitmap))
                val x = bounds.center.x.toInt()
                for (y in listOf(bounds.top.toInt() + 8, bounds.bottom.toInt() - 8)) {
                    assertEquals("Classic artwork has no top or bottom fade", Color.White.toArgb(), bitmap.getPixel(x, y))
                }
                val output = File("build/ui-previews/playlist-classic-cover.png").apply { parentFile?.mkdirs() }
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
        } finally {
            controller.pause().stop().destroy()
            recomposer.cancel()
            scope.cancel()
        }
    }

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
