package com.metrolist.music.ui.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.metrolist.music.R
import com.metrolist.music.connect.*
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
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConnectContentTest {
    @Test fun currentDeviceHistoryAndSettingsActionsAreReadableAndIndependent() {
        for ((width, scale) in listOf(360 to 1f, 360 to 1.6f, 640 to 1f)) {
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            try {
                val activity = controller.get()
                activity.setTheme(R.style.Theme_Metrolist)
                val phone = AudioRoute("phone", "Phone", DeviceIcon.PHONE)
                val buds = AudioRoute("buds", "AirPods", DeviceIcon.HEADPHONES, true)
                val state = ConnectState(listOf(phone, buds), ConnectPreferences(listOf(
                    SavedDevice("buds", "AirPods", "Meine AirPods Pro", DeviceIcon.EARBUDS),
                    SavedDevice("old", "Old speaker", "Wohnzimmer", DeviceIcon.SPEAKER))))
                var select = ""; var edit = ""; var find = 0; var color = 0; var permission = 0
                activity.setContent {
                    MetrolistTheme(darkTheme = true) {
                        CompositionLocalProvider(LocalDensity provides Density(1f, scale), LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
                                ConnectContent(state, "EGOIST — KIDD KAWAKI", false, { permission++ },
                                    { select = it.id }, { edit = it.id }, { find++ }, { color++ })
                            }
                        }
                    }
                }
                val decor = activity.window.decorView
                repeat(3) {
                    shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
                    decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
                    decor.layout(0, 0, width, 1000)
                }
                val nodes = allNodes(requireNotNull(owner(decor)).unmergedRootSemanticsNode)
                fun clickLabel(text: String) {
                    val node = nodes.first { it.config.getOrNull(SemanticsActions.OnClick) != null && allNodes(it).any { child ->
                        child.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == text } == true ||
                            child.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(text) == true } }
                    requireNotNull(node.config[SemanticsActions.OnClick].action).invoke()
                }
                assertTrue(nodes.any { it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == "Meine AirPods Pro" } == true })
                clickLabel(activity.getString(R.string.connect_find)); assertEquals(1, find)
                clickLabel(activity.getString(R.string.connect_color)); assertEquals(1, color)
                clickLabel(activity.getString(R.string.connect_permission)); assertEquals(1, permission)
                clickLabel(activity.getString(R.string.connect_phone)); assertEquals("phone", select)
                clickLabel("Wohnzimmer"); assertEquals("old", select)
                clickLabel(activity.getString(R.string.connect_edit)); assertEquals("buds", edit)
                val bitmap = Bitmap.createBitmap(width, 1000, Bitmap.Config.ARGB_8888)
                decor.draw(Canvas(bitmap))
                File("build/ui-previews/connect-$width-$scale.png").apply { parentFile!!.mkdirs() }.outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
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
