package com.metrolist.music.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.navigation.NavController
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.listentogether.ListenTogetherClient
import com.metrolist.music.listentogether.ListenTogetherManager
import com.metrolist.music.ui.theme.LocalSongColor
import com.metrolist.music.ui.theme.MetrolistTheme
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
import org.robolectric.annotation.LooperMode
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "de-w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class JamHubRenderTest {
    @Test
    fun internetSetupUsesOneBackdropAndShowsBothRoomActions() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val manager = ListenTogetherManager(ListenTogetherClient(activity), activity)
        try {
            activity.setTheme(R.style.Theme_Metrolist)
            activity.setContent {
                CompositionLocalProvider(
                    LocalListenTogetherManager provides manager,
                    LocalPlayerAwareWindowInsets provides WindowInsets(0),
                    LocalSongColor provides Color(0xFFBD493D),
                ) {
                    MetrolistTheme(darkTheme = true) {
                        Surface { JamScreen(NavController(activity), showTopBar = true, jam = null) }
                    }
                }
            }
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            val decor = activity.window.decorView
            decor.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, 360, 800)
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            fun owner(view: View): SemanticsOwner? {
                if (view.javaClass.simpleName == "AndroidComposeView") {
                    return view.javaClass.getMethod("getSemanticsOwner").invoke(view) as SemanticsOwner
                }
                if (view is ViewGroup) for (i in 0 until view.childCount) owner(view.getChildAt(i))?.let { return it }
                return null
            }
            val text = mutableListOf<String>()
            val segmentBounds = mutableListOf<Rect>()
            fun collect(node: SemanticsNode) {
                node.config.getOrNull(SemanticsProperties.Text)?.forEach { text += it.text }
                if (node.config.getOrNull(SemanticsProperties.Selected) != null) segmentBounds += node.boundsInRoot
                node.children.forEach(::collect)
            }
            collect(requireNotNull(owner(decor)).unmergedRootSemanticsNode)
            assertEquals("Both connection modes must be selectable", 2, segmentBounds.size)
            assertEquals("Segment outlines must align at the top", segmentBounds[0].top, segmentBounds[1].top, 0.5f)
            assertEquals("Segment outlines must align at the bottom", segmentBounds[0].bottom, segmentBounds[1].bottom, 0.5f)
            assertTrue(text.toString(), text.contains(activity.getString(R.string.create_room)))
            assertTrue(text.toString(), text.contains(activity.getString(R.string.join_room)))
            assertFalse("Embedded screen must not duplicate the large introduction", text.contains(activity.getString(R.string.listen_together_description)))
            val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bitmap))
            for (y in listOf(70, 180, 300, 450)) {
                val pixel = bitmap.getPixel(4, y)
                assertTrue("Backdrop must remain visible beside embedded content at y=$y", android.graphics.Color.red(pixel) > android.graphics.Color.blue(pixel) + 4)
            }
            val output = File("build/reports/sourcify-ui/jam-hub-internet-de.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        } finally {
            controller.pause().stop().destroy()
            manager.disconnect()
        }
    }
}
