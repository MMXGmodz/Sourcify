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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.ui.component.PlaylistArtworkHeader
import com.metrolist.music.ui.screens.settings.CanvasSettings
import com.metrolist.music.ui.theme.MetrolistTheme
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
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w412dp-h892dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CanvasUiRenderTest {
    @Test
    fun settingsAndPlaylistHeaderRenderOnPhone() {
        render("canvas-settings") {
            CompositionLocalProvider(LocalPlayerConnection provides null, LocalPlayerAwareWindowInsets provides WindowInsets(top = 64.dp)) {
                CanvasSettings(rememberNavController())
            }
        }
        render("stats-dashboard") {
            com.metrolist.music.ui.screens.StatsDashboard(133, "Artist", null, "Top Song", null, emptyList(), {}, {}, {})
        }
        render("queue-controls") {
            // Place this short footer inside the shared screenshot sampling region.
            androidx.compose.foundation.layout.Row(Modifier.padding(top = 100.dp)) {
                com.metrolist.music.ui.player.QueueControl(com.metrolist.music.R.drawable.shuffle, "Shuffle", false, true, Modifier.weight(1f)) {}
                com.metrolist.music.ui.player.QueueControl(com.metrolist.music.R.drawable.repeat, "Wiederholen", true, true, Modifier.weight(1f)) {}
                com.metrolist.music.ui.player.QueueControl(com.metrolist.music.R.drawable.bedtime, "Timer", false, true, Modifier.weight(1f)) {}
            }
        }
        render("lyrics-preview") {
            com.metrolist.music.ui.player.LyricsPreviewCard(listOf("First line", "Second line", "Third line"), Color(0xFF9D2525), onFullscreen = {})
        }
        render("search-browse") {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(bottom = 96.dp)) {
                val titles = listOf("Music", "Podcasts", "Made for you", "New releases", "Pop", "Hip-Hop", "Rock", "Latin")
                val colors = listOf(Color(0xFFB60D82), Color(0xFF213967), Color(0xFF8865A8), Color(0xFFCA1749))
                com.metrolist.music.ui.screens.search.SearchBrowseContent(
                    titles.mapIndexed { i, title -> com.metrolist.music.ui.screens.search.BrowseCategory(title, colors[i % 4], com.metrolist.music.R.drawable.album, "home") },
                    onSearch = {}, onCategory = {},
                )
            }
        }
        render("artist-context") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                com.metrolist.music.ui.player.ArtistAboutCard("Test Artist", null, "A real biography is loaded from the artist source.", "Monthly audience", true, true, {}, {}, {})
                com.metrolist.music.ui.player.DiscoverArtistCard("Test Artist", null, null, null, true, true, {}, {}, {})
                com.metrolist.music.ui.player.SongCreditsPreview(
                    com.metrolist.music.models.MediaMetadata("track", "Test Song", listOf(com.metrolist.music.models.MediaMetadata.Artist("artist", "Test Artist")), 180),
                    com.metrolist.music.ui.player.parseSongCredits("Composer: Test Composer\nMixing Engineer: Test Engineer"), onAll = {})
            }
        }
        render("credits-detail") {
            com.metrolist.music.ui.player.SongCreditsContent(
                com.metrolist.music.models.MediaMetadata("track", "Test Song", listOf(com.metrolist.music.models.MediaMetadata.Artist("artist", "Test Artist")), 180),
                com.metrolist.music.ui.player.parseSongCredits("Lyricist: Test Artist\nComposer: Test Composer\nMixing Engineer: Test Engineer\n℗ Test Source"),
                true, true, {}, {})
        }
        render("playlist-header") {
            PlaylistArtworkHeader("Late Night Sessions") {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF467DA0), Color(0xFF253955), Color(0xFF83CEEE)))))
            }
        }
        render("canvas-backdrop-top", verify = { bitmap ->
            assertTrue("Video remains visible in the upper player", android.graphics.Color.red(bitmap.getPixel(400, 400)) > 240)
            assertTrue("Video fades toward black at its lower edge", android.graphics.Color.red(bitmap.getPixel(400, 1700)) < 60)
        }) {
            com.metrolist.music.ui.player.CanvasBackdrop(scrollOffset = { 0f }) {
                Box(Modifier.fillMaxSize().background(Color.Red))
            }
        }
        render("canvas-backdrop-scrolled", verify = { bitmap ->
            assertTrue("Details below the canvas must be black, not an extended video", bitmap.getPixel(400, 1500) == android.graphics.Color.BLACK)
        }) {
            com.metrolist.music.ui.player.CanvasBackdrop(scrollOffset = { 1000f }) {
                Box(Modifier.fillMaxSize().background(Color.Red))
            }
        }
        render("canvas-backdrop-hidden", verify = { bitmap ->
            assertTrue("Video must scroll fully out of the detail area", bitmap.getPixel(400, 400) == android.graphics.Color.BLACK)
        }) {
            com.metrolist.music.ui.player.CanvasBackdrop(scrollOffset = { 2000f }) {
                Box(Modifier.fillMaxSize().background(Color.Red))
            }
        }
        com.metrolist.music.ui.component.PlaylistScrollRenderCheck().titleCollapsesAndPlayStaysClickableAfterHeaderLeavesComposition()
    }

    private fun render(name: String, verify: (Bitmap) -> Unit = {}, content: @Composable () -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        try {
            activity.setContent {
                MetrolistTheme(darkTheme = true) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        // Match the NavHost container; a Surface propagates full-screen minimum constraints.
                        Box(Modifier.fillMaxSize()) { content() }
                    }
                }
            }
            val root = activity.window.decorView
            root.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1784, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 824, 1784)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            root.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1784, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 824, 1784)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            val bitmap = Bitmap.createBitmap(824, 1784, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val colors = mutableSetOf<Int>()
            for (y in 150 until 1500 step 10) for (x in 10 until 800 step 10) colors += bitmap.getPixel(x, y)
            val output = File("build/ui-previews/$name.png").apply { parentFile?.mkdirs() }
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue("Expected painted UI, not an empty window", colors.size > 10 || name == "canvas-backdrop-hidden")
            if (name == "canvas-settings") {
                var titlePixels = 0
                for (y in 35 until 125) for (x in 100 until 400) {
                    val pixel = bitmap.getPixel(x, y)
                    if (android.graphics.Color.red(pixel) > 220 && android.graphics.Color.green(pixel) > 220 && android.graphics.Color.blue(pixel) > 220) titlePixels++
                }
                assertTrue("Canvas heading must remain visible above the tabs", titlePixels > 100)
            }
            verify(bitmap)
            bitmap.recycle()
        } finally { controller.pause().stop().destroy() }
    }
}
