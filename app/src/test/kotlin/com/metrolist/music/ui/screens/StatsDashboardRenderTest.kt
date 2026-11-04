package com.metrolist.music.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.metrolist.music.R
import com.metrolist.music.ui.theme.LocalSongColor
import com.metrolist.music.ui.theme.MetrolistTheme
import com.metrolist.music.ui.theme.screenHeaderBrush
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
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w320dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class StatsDashboardRenderTest {
    @Test
    fun largeTextKeepsTheFullMinuteTotalAndEveryExistingActionInBothThemes() {
        for ((dark, width, fontScale) in listOf(Triple(true, 320, 1.6f), Triple(false, 320, 1.6f),
            Triple(true, 412, 1f), Triple(false, 412, 1f))) {
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            try {
                val activity = controller.get()
                activity.setTheme(R.style.Theme_Metrolist)
                var historyClicks = 0
                var artistClicks = 0
                var songClicks = 0
                activity.setContent {
                    MetrolistTheme(darkTheme = dark) {
                        CompositionLocalProvider(
                            LocalDensity provides Density(1f, fontScale),
                            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                            LocalSongColor provides Color(0xFFBD493D),
                        ) {
                            Column(Modifier.fillMaxSize().background(screenHeaderBrush())) {
                                StatsScreenHeader(
                                    title = { Text(activity.getString(R.string.ui_stream_stats), style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onSurface) },
                                    navigationIcon = { IconButton(onClick = {}) { Icon(painterResource(R.drawable.arrow_back), "Back") } },
                                    actions = { IconButton(onClick = {}) { Icon(painterResource(R.drawable.search), "Search") } },
                                )
                                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                    StatsDashboard(123456789, "A long artist name that must remain readable", null,
                                        "A long song title that must remain readable", null, emptyList(),
                                        { artistClicks++ }, { songClicks++ }, { historyClicks++ })
                                }
                            }
                        }
                    }
                }
                val decor = activity.window.decorView
                repeat(2) {
                    shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
                    decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
                    decor.layout(0, 0, width, 900)
                }
                shadowOf(Looper.getMainLooper()).idleFor(32, TimeUnit.MILLISECONDS)
                val bitmap = Bitmap.createBitmap(width, 900, Bitmap.Config.ARGB_8888)
                try {
                    decor.draw(Canvas(bitmap))
                    val output = File("build/reports/sourcify-ui/stats-${width}dp-${fontScale}-${if (dark) "dark" else "light"}.png")
                    output.parentFile?.mkdirs()
                    output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    for (y in listOf(60, 100)) {
                        val pixel = bitmap.getPixel(4, y)
                        assertTrue("The song backdrop must continue through the header, without a black bar", android.graphics.Color.red(pixel) > android.graphics.Color.blue(pixel) + 4)
                    }
                } finally { bitmap.recycle() }
                val nodes = allNodes(requireNotNull(owner(decor)).unmergedRootSemanticsNode)
                val title = nodes.single { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == activity.getString(R.string.ui_stream_stats) } == true }
                val titleLayouts = mutableListOf<TextLayoutResult>()
                requireNotNull(title.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action).invoke(titleLayouts)
                val titleLayout = titleLayouts.single()
                assertFalse("The full title must wrap instead of clipping (width=$width, scale=$fontScale, dark=$dark, size=${titleLayout.size}, constraints=${titleLayout.layoutInput.constraints}, lines=${titleLayout.lineCount}, paragraph=${titleLayout.multiParagraph.width}x${titleLayout.multiParagraph.height}, lastLineRight=${titleLayout.getLineRight(titleLayout.lineCount - 1)}, widthOverflow=${titleLayout.didOverflowWidth}, heightOverflow=${titleLayout.didOverflowHeight})", titleLayout.hasVisualOverflow)
                val minutes = nodes.single { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "123456789" } == true }
                val layouts = mutableListOf<TextLayoutResult>()
                requireNotNull(minutes.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action).invoke(layouts)
                assertFalse("The entire minute total must fit at 160% text size", layouts.single().hasVisualOverflow)
                for (label in listOf(R.string.ui_minutes, R.string.ui_top_artist, R.string.ui_top_song, R.string.ui_top_songs)) {
                    val click = nodes.single { node ->
                        node.config.getOrNull(SemanticsActions.OnClick) != null &&
                            allNodes(node).any { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == activity.getString(label) } == true }
                    }
                    requireNotNull(click.config[SemanticsActions.OnClick].action).invoke()
                }
                assertEquals(1, historyClicks)
                assertEquals(1, artistClicks)
                assertEquals(2, songClicks)

            } finally { controller.pause().stop().destroy() }
        }
    }

    private fun allNodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::allNodes)

    private fun owner(view: View): SemanticsOwner? {
        if (view.javaClass.simpleName == "AndroidComposeView") return view.javaClass.getMethod("getSemanticsOwner").invoke(view) as SemanticsOwner
        if (view is ViewGroup) for (index in 0 until view.childCount) owner(view.getChildAt(index))?.let { return it }
        return null
    }
}
