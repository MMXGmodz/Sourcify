package com.metrolist.music.ui.screens.artist

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.SuccessResult
import com.metrolist.music.R
import com.metrolist.music.ui.theme.MetrolistTheme
import com.metrolist.music.utils.ArtistConcert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "de-w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ConcertComponentsRenderTest {
    private val event = ArtistConcert("11111111-1111-1111-1111-111111111111", "CAPO Live", "2027-01-16", "E-Werk", "Köln", "20:00")

    @Test fun playerCardHasAllEventsAndNoSaveActionOrUnverifiedProximity() {
        val cover = File("build/reports/sourcify-ui/concert-fixture-cover.png")
        cover.parentFile?.mkdirs()
        val fixture = Bitmap.createBitmap(360, 220, Bitmap.Config.ARGB_8888)
        fixture.eraseColor(android.graphics.Color.rgb(72, 52, 101))
        cover.outputStream().use { fixture.compress(Bitmap.CompressFormat.PNG, 100, it) }
        fixture.recycle()
        render("concert-player-de.png", artwork = cover, content = {
            PlayerConcertCardContent("CAPO", event, android.net.Uri.fromFile(cover.absoluteFile).toString(), nearby = false, onAllEvents = {})
        }) { nodes ->
            val text = text(nodes())
            assertTrue(text.contains("Live-Event"))
            assertTrue(text.contains("Alle anzeigen"))
            assertTrue(text.contains("CAPO"))
            val artist = nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == "CAPO" } == true }
            assertTrue("Rich artwork must reserve its 240dp image area above artist details", artist.boundsInRoot.top >= 250f)
            assertTrue(text.any { "20:00" in it })
            assertFalse(text.any { "Nähe" in it })
            assertFalse(nodes().any { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { value -> "merken" in value } == true })
        }
    }

    @Test fun missingPosterKeepsAConcertGraphicAndDateInsteadOfAnArtistPhoto() {
        render("concert-player-fallback-de.png", content = {
            PlayerConcertCardContent("CAPO", event, null, nearby = false, onAllEvents = {})
        }) { nodes ->
            val values = text(nodes())
            assertTrue(values.any { it.startsWith("JAN") })
            assertTrue(values.contains("16"))
            val artist = nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == "CAPO" } == true }
            assertTrue("Missing artwork must keep its image area without a layout jump", artist.boundsInRoot.top >= 250f)
            assertTrue(values.containsAll(listOf("Live-Event", "Alle anzeigen", "CAPO Live")))
        }
    }

    @Test fun eventsRowsPutMonthAboveDayAndSaveToggleChangesItsAccessibleAction() {
        val saved = mutableStateOf(false)
        render("concert-artist-events-de.png", content = {
            Column(Modifier.padding(16.dp)) {
                ArtistContentTabs(eventsSelected = true, onSelectEvents = {})
                ConcertRow(event, saved.value, { saved.value = !saved.value })
                ConcertRow(event.copy(id = "22222222-2222-2222-2222-222222222222", date = "2027-02", city = "", time = ""), false, {})
                OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Alle Events ansehen") }
            }
        }) { nodes ->
            val initial = nodes()
            assertTrue(text(initial).containsAll(listOf("Musik", "Events", "Köln", "Alle Events ansehen")))
            val musicTab = initial.first { it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == "Musik" } == true }
            val eventsTab = initial.first { it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == "Events" } == true }
            assertTrue("Tabs must remain adjacent on the left", eventsTab.boundsInRoot.left - musicTab.boundsInRoot.right < 40f)
            assertTrue("Events must not be centered in the right half", eventsTab.boundsInRoot.right < 220f)
            val month = initial.first { it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text.startsWith("JAN") } == true }
            val day = initial.first { it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == "16" } == true }
            assertTrue("Month must be above day", month.boundsInRoot.top < day.boundsInRoot.top)
            val save = initial.first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Event merken") == true }
            val clickable = initial.filter { node ->
                node.config.getOrNull(SemanticsActions.OnClick) != null && node.boundsInRoot.contains(save.boundsInRoot.center)
            }.minBy { it.boundsInRoot.width * it.boundsInRoot.height }
            assertTrue(requireNotNull(clickable.config.getOrNull(SemanticsActions.OnClick)?.action).invoke())
            Snapshot.sendApplyNotifications()
            assertTrue(saved.value)
            val after = nodes()
            val updated = after.any { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Event nicht mehr merken") == true }
            fun descriptions(values: List<SemanticsNode>) = values.mapNotNull { node ->
                val description = node.config.getOrNull(SemanticsProperties.ContentDescription)
                val toggle = node.config.getOrNull(SemanticsProperties.ToggleableState)
                if (description != null || toggle != null) "${node.id}: description=$description toggle=$toggle" else null
            }
            assertTrue("Saving must expose the remove-saved-event accessibility label after recomposition. " +
                "Before=${descriptions(initial)} After=${descriptions(after)}", updated)
        }
    }

    @Test fun musicConcertCardUsesTheProviderDateAndVenueWithoutInventingArtwork() {
        render("concert-artist-music-de.png", content = {
            ArtistUpcomingConcertCardContent(event, false, {}, {})
        }) { nodes ->
            val values = text(nodes())
            assertTrue(values.containsAll(listOf("CAPO Live", "Köln", "16")))
            assertTrue(values.any { it.startsWith("JAN") })
            assertTrue(values.any { "20:00" in it && "E-Werk" in it })
            assertTrue(values.contains("Alle anzeigen"))
        }
    }

    @Test fun artistHeaderCollapsesOnlyNearItsMeasuredEndAndNeverJumpsAtOneHundredPixels() {
        assertEquals(0f, artistHeaderCollapseProgress(0, 600, 100), 0f)
        assertEquals(0f, artistHeaderCollapseProgress(100, 600, 100), 0f)
        assertEquals(0f, artistHeaderCollapseProgress(500, 600, 100), 0f)
        assertEquals(0.5f, artistHeaderCollapseProgress(550, 600, 100), 0f)
        assertEquals(1f, artistHeaderCollapseProgress(600, 600, 100), 0f)
        assertEquals(1f, artistHeaderCollapseProgress(900, 600, 100), 0f)
        assertEquals(0f, artistHeaderCollapseProgress(100, 0, 100), 0f)
    }

    @Test fun manuallyChosenCityRequiresExactPublishedCityAndPartialDatesKeepNoDay() {
        assertTrue(event.matchesCity(" koln "))
        assertFalse(event.matchesCity(""))
        assertFalse(event.matchesCity("Berlin"))
        assertFalse(event.copy(city = "").matchesCity("Köln"))
        assertEquals("2027-02", concertDateDetails(event.copy(date = "2027-02", time = "")))
    }

    private fun text(nodes: List<SemanticsNode>) = nodes.flatMap { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    }

    private fun render(name: String, artwork: File? = null, content: @Composable () -> Unit, verify: (() -> List<SemanticsNode>) -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clock)
        val recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        var frameTime = 0L
        try {
            activity.setTheme(R.style.Theme_Metrolist)
            if (artwork != null) {
                val result = kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.withTimeout(5000) {
                        SingletonImageLoader.get(activity).execute(ImageRequest.Builder(activity)
                            .data(android.net.Uri.fromFile(artwork.absoluteFile).toString()).size(360, 240).allowHardware(false).build())
                    }
                }
                assertTrue("Local artwork must decode successfully: $result", result is SuccessResult)
            }
            activity.setContent(parent = recomposer) {
                MetrolistTheme(darkTheme = true) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) { content() }
                    }
                }
            }
            val decor = activity.window.decorView
            fun frames(count: Int) {
                repeat(count) {
                    decor.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                    decor.layout(0, 0, 360, 800)
                    Snapshot.sendApplyNotifications()
                    frameTime += 16_666_667L
                    clock.sendFrame(frameTime)
                    shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
                }
            }
            frames(8)
            fun owner(view: View): SemanticsOwner? {
                if (view.javaClass.simpleName == "AndroidComposeView") return view.javaClass.getMethod("getSemanticsOwner").invoke(view) as SemanticsOwner
                if (view is ViewGroup) for (i in 0 until view.childCount) owner(view.getChildAt(i))?.let { return it }
                return null
            }
            fun nodes(): List<SemanticsNode> {
                frames(4)
                return buildList {
                    fun collect(node: SemanticsNode) { add(node); node.children.forEach(::collect) }
                    collect(requireNotNull(owner(decor)).unmergedRootSemanticsNode)
                }
            }
            verify(::nodes)
            val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
            for (attempt in 0 until 80) {
                if (artwork != null) Thread.sleep(25)
                frames(1)
                decor.draw(Canvas(bitmap))
                if (artwork == null || bitmap.getPixel(100, 150) == android.graphics.Color.rgb(72, 52, 101)) break
            }
            if (artwork != null) assertEquals("Decoded local PNG must paint the artwork area",
                android.graphics.Color.rgb(72, 52, 101), bitmap.getPixel(100, 150))
            val output = File("build/reports/sourcify-ui/$name")
            output.parentFile?.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        } finally {
            controller.pause().stop().destroy()
            recomposer.cancel()
            scope.cancel()
        }
    }
}
