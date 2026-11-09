package com.metrolist.music.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.metrolist.music.R
import com.metrolist.music.ui.theme.MetrolistTheme
import org.junit.Assert.*
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
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeShortcutsTest {
    @Test fun bothShortcutsHaveEqualHeightAndKeepTheirActionsAtLargeFont() {
        for (scale in listOf(1f, 1.6f)) {
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            try {
                val activity = controller.get()
                val clicks = mutableListOf<String>()
                activity.setContent {
                    MetrolistTheme(darkTheme = true) {
                        CompositionLocalProvider(LocalDensity provides Density(1f, scale),
                            LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                            Column(Modifier.fillMaxSize().background(com.metrolist.music.ui.theme.screenHeaderBrush())) {
                                HomeHero {}
                                HomeLibraryShortcuts { clicks += it }
                            }
                        }
                    }
                }
                val root = activity.window.decorView
                repeat(3) {
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
                    root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, 360, 800)
                    val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
                    root.draw(Canvas(bitmap))
                    if (it == 2) File("build/ui-previews/home-$scale.png").apply { parentFile?.mkdirs() }.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG,100,stream) }
                    bitmap.recycle()
                }
                val nodes = descendants(requireNotNull(owner(root)).unmergedRootSemanticsNode)
                val cards = listOf(R.string.liked, R.string.downloaded_songs).map { id ->
                    nodes.single { n -> n.config.getOrNull(SemanticsActions.OnClick) != null && descendants(n).any { child -> child.config.getOrNull(SemanticsProperties.Text)?.any { it.text == activity.getString(id) } == true } }
                }
                assertEquals("Both home shortcuts must share a height", cards[0].boundsInRoot.height, cards[1].boundsInRoot.height, 1f)
                assertTrue(cards.all { it.boundsInRoot.height >= 48f })
                cards.forEach { requireNotNull(it.config[SemanticsActions.OnClick].action).invoke() }
                assertEquals(listOf("auto_playlist/liked","auto_playlist/downloaded"), clicks)
            } finally { controller.pause().stop().destroy() }
        }
    }
    private fun descendants(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::descendants)
    private fun owner(v: View): SemanticsOwner? {
        if (v.javaClass.simpleName == "AndroidComposeView") return v.javaClass.getMethod("getSemanticsOwner").invoke(v) as SemanticsOwner
        if (v is ViewGroup) for(i in 0 until v.childCount) owner(v.getChildAt(i))?.let { return it }
        return null
    }
}
