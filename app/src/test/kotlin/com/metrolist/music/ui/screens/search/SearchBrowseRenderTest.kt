package com.metrolist.music.ui.screens.search

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.theme.LocalSongColor
import com.metrolist.music.ui.theme.MetrolistTheme
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
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w412dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SearchBrowseRenderTest {
    @Test
    fun searchAndCategoryActionsKeepTheirRoutesAcrossThemes() {
        for (dark in listOf(true, false)) {
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            try {
                val activity = controller.get()
                activity.setTheme(R.style.Theme_Metrolist)
                var searches = 0
                var retries = 0
                var selectedRoute: String? = null
                activity.setContent {
                    MetrolistTheme(darkTheme = dark) {
                        CompositionLocalProvider(
                            LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 80),
                            LocalSongColor provides Color(0xFF8ED8FF),
                        ) {
                            SearchBrowseContent(
                                categories = listOf(
                                    BrowseCategory("Music", Color(0xFFB60D82), R.drawable.music_note, "music-route"),
                                    BrowseCategory("Podcasts", Color(0xFF213967), R.drawable.radio, "podcast-route"),
                                ),
                                onSearch = { searches++ },
                                onCategory = { selectedRoute = it },
                                onRetry = { retries++ },
                            )
                        }
                    }
                }
                val decor = activity.window.decorView
                repeat(2) {
                    shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
                    decor.measure(View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
                    decor.layout(0, 0, 412, 900)
                }
                shadowOf(Looper.getMainLooper()).idleFor(32, TimeUnit.MILLISECONDS)
                val nodes = allNodes(requireNotNull(owner(decor)).unmergedRootSemanticsNode)
                fun click(label: String) {
                    val clickable = nodes.single { node ->
                        node.config.getOrNull(SemanticsActions.OnClick) != null &&
                            allNodes(node).any { child -> child.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true }
                    }
                    requireNotNull(clickable.config[SemanticsActions.OnClick].action).invoke()
                }
                click(activity.getString(R.string.search_listen_prompt))
                click("Music")
                assertEquals("music-route", selectedRoute)
                click("Podcasts")
                assertEquals("podcast-route", selectedRoute)
                click(activity.getString(R.string.s6_retry_playlists))
                assertEquals(1, searches)
                assertEquals(1, retries)
                val bitmap = Bitmap.createBitmap(412, 900, Bitmap.Config.ARGB_8888)
                try {
                    decor.draw(Canvas(bitmap))
                    val pixel = bitmap.getPixel(2, 150)
                    assertTrue("The song color must tint the search backdrop", android.graphics.Color.blue(pixel) > android.graphics.Color.red(pixel))
                    val output = File("build/reports/sourcify-ui/search-${if (dark) "dark" else "light"}.png")
                    output.parentFile?.mkdirs()
                    output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally { bitmap.recycle() }
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
