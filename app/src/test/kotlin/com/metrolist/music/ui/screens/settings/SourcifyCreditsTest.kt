package com.metrolist.music.ui.screens.settings

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
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.theme.MetrolistTheme
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
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SourcifyCreditsTest {
    @Test
    fun creditsRenderWithOwnNamesAndRetainBundledLicense() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity = controller.get()
            activity.setTheme(R.style.Theme_Metrolist)
            activity.setContent {
                CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0)) {
                    MetrolistTheme(darkTheme = true) {
                        Surface { AboutScreen(NavController(activity)) }
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
            fun collect(node: SemanticsNode) {
                node.config.getOrNull(SemanticsProperties.Text)?.forEach { text += it.text }
                node.children.forEach(::collect)
            }
            collect(requireNotNull(owner(decor)).unmergedRootSemanticsNode)
            assertTrue(text.contains("MMXG"))
            assertTrue(text.contains("Vibecoded"))
            assertFalse(text.any { it.contains("Metrolist", ignoreCase = true) })
            val license = activity.assets.open("sourcify_licenses.txt").bufferedReader().use { it.readText() }
            assertTrue(license.contains("GNU GENERAL PUBLIC LICENSE"))
            assertTrue(license.contains("Metrolist Project (C) 2026"))
            val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bitmap))
            val output = File("build/reports/sourcify-ui/credits.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
