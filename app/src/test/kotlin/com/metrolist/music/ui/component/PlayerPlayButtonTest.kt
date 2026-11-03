package com.metrolist.music.ui.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.metrolist.music.R
import com.metrolist.music.constants.PlayButtonStyle
import com.metrolist.music.constants.PlayButtonStyleKey
import com.metrolist.music.constants.PlayerButtonsStyleKey
import com.metrolist.music.ui.screens.settings.PlayButtonStyleOptions
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.rememberEnumPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
class PlayerPlayButtonTest {
    @Test fun metalHasAGreenRingSilverBevelDarkCenterAndDistinctPlayPauseGlyphs() = withComposition {
        var paused by mutableStateOf(true)
        var captureBounds = Rect.Zero
        content {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PlayerPlayButton(Modifier.size(112.dp).onGloballyPositioned { captureBounds = it.boundsInWindow() }, {}, iconRes = if (paused) R.drawable.play else R.drawable.pause,
                    contentDescription = if (paused) "Play" else "Pause", style = PlayButtonStyle.METAL_RING)
            }
        }
        frames()
        fun capture(name: String): Bitmap {
            val full = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(full))
            val result = Bitmap.createBitmap(full, captureBounds.left.toInt(), captureBounds.top.toInt(), captureBounds.width.toInt(), captureBounds.height.toInt())
            full.recycle()
            File("build/ui-previews/$name.png").apply { parentFile?.mkdirs() }.outputStream().use {
                result.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            return result
        }
        val play = capture("player-play-metal")
        var green = 0
        val rimValues = mutableSetOf<Int>()
        var darkCenter = 0
        for (y in 0 until play.height) for (x in 0 until play.width) {
            val color = play.getPixel(x, y)
            val red = android.graphics.Color.red(color)
            val g = android.graphics.Color.green(color)
            val blue = android.graphics.Color.blue(color)
            val radius = kotlin.math.hypot(x - play.width / 2f, y - play.height / 2f) / play.width
            if (radius in .39f.. .49f && g > 170 && g > red * 1.5 && g > blue * 1.2) green++
            if (radius in .34f.. .40f && kotlin.math.abs(red - g) < 20 && kotlin.math.abs(g - blue) < 20) rimValues += red / 16
            if (radius < .27f && red < 110 && g < 110 && blue < 110) darkCenter++
        }
        assertTrue("The native painted outer ring must be vivid green", green > 250)
        assertTrue("The silver bevel must contain directional metal highlights", rimValues.size >= 5)
        assertTrue("The brushed metal face must retain contrast behind the white glyph", darkCenter > 800)
        paused = false
        frames()
        assertEquals(Role.Button, button("Pause").config[SemanticsProperties.Role])
        val pause = capture("player-pause-metal")
        var changed = 0
        for (y in 0 until play.height) for (x in 0 until play.width) if (play.getPixel(x, y) != pause.getPixel(x, y)) changed++
        assertTrue("Play and pause must paint distinct glyphs", changed > 100)
        play.recycle()
        pause.recycle()
    }

    @Test fun buttonHasOneAccessibleActionMinimumTargetAndBlocksDisabledClicks() = withComposition {
        var enabled by mutableStateOf(true)
        var clicks = 0
        content { PlayerPlayButton(onClick = { clicks++ }, enabled = enabled, iconRes = R.drawable.play,
            contentDescription = "Play track", style = PlayButtonStyle.METAL_RING) }
        frames()
        val node = button("Play track")
        assertEquals(Role.Button, node.config[SemanticsProperties.Role])
        assertEquals(listOf("Play track"), node.config[SemanticsProperties.ContentDescription])
        assertTrue(node.boundsInRoot.width >= 48f && node.boundsInRoot.height >= 48f)
        assertFalse(node.config.contains(SemanticsProperties.Disabled))
        assertEquals("Enabled buttons must participate in keyboard focus", false, node.config.getOrNull(SemanticsProperties.Focused))
        tap(node.boundsInRoot.center.x, node.boundsInRoot.center.y)
        frames()
        assertEquals("A touch must invoke transport exactly once", 1, clicks)
        enabled = false
        frames()
        val disabled = button("Play track")
        assertTrue(disabled.config.contains(SemanticsProperties.Disabled))
        tap(disabled.boundsInRoot.center.x, disabled.boundsInRoot.center.y)
        disabled.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
        frames()
        assertEquals("Disabled touch and accessibility actions must not invoke transport", 1, clicks)
    }

    @Test fun styleCardsPersistIndependentlyAndReadBackInANewComposition() = withComposition {
        val store = activity.dataStore
        val before = runBlocking { store.data.first() }
        try {
            runBlocking { store.edit { it.remove(PlayButtonStyleKey); it[PlayerButtonsStyleKey] = "TERTIARY" } }
            var observed = PlayButtonStyle.STANDARD
            content {
                val (style, select) = rememberEnumPreference(PlayButtonStyleKey, PlayButtonStyle.STANDARD)
                observed = style
                PlayButtonStyleOptions(style, select)
            }
            frames()
            assertEquals(PlayButtonStyle.STANDARD, observed)
            val metal = nodes().single { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton &&
                it.config.getOrNull(SemanticsProperties.Selected) == false }
            assertTrue(requireNotNull(metal.config[SemanticsActions.OnClick].action).invoke())
            frames()
            runBlocking { withTimeout(5_000) { store.data.first { it[PlayButtonStyleKey] == PlayButtonStyle.METAL_RING.name } } }
            assertEquals("TERTIARY", runBlocking { store.data.first()[PlayerButtonsStyleKey] })
            content {
                observed = rememberEnumPreference(PlayButtonStyleKey, PlayButtonStyle.STANDARD).value
            }
            frames(20)
            assertEquals("A recreated reader must load the saved metal selection", PlayButtonStyle.METAL_RING, observed)
        } finally {
            runBlocking { store.edit {
                before[PlayButtonStyleKey]?.let { value -> it[PlayButtonStyleKey] = value } ?: it.remove(PlayButtonStyleKey)
                before[PlayerButtonsStyleKey]?.let { value -> it[PlayerButtonsStyleKey] = value } ?: it.remove(PlayerButtonsStyleKey)
            } }
        }
    }

    private fun withComposition(test: Harness.() -> Unit) {
        val harness = Harness()
        try { harness.test() } finally { harness.close() }
    }

    private class Harness {
        private val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        val root: View = activity.window.decorView
        private val clock = BroadcastFrameClock()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clock)
        private val recomposer = Recomposer(scope.coroutineContext)
        private var time = 0L
        init { scope.launch { recomposer.runRecomposeAndApplyChanges() } }
        fun content(block: @Composable () -> Unit) { activity.setContent(parent = recomposer) { MaterialTheme(content = block) } }
        fun frames(count: Int = 8) {
            repeat(count) {
                root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 360, 800)
                Snapshot.sendApplyNotifications()
                time += 16_666_667
                clock.sendFrame(time)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            }
        }
        fun tap(x: Float, y: Float) {
            val now = SystemClock.uptimeMillis()
            val offset = IntArray(2)
            requireNotNull(composeView(root)).getLocationInWindow(offset)
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(now, now + if (action == MotionEvent.ACTION_UP) 50 else 0, action, x + offset[0], y + offset[1], 0)
                root.dispatchTouchEvent(event)
                event.recycle()
            }
        }
        private fun composeView(view: View): View? {
            if (view.javaClass.simpleName == "AndroidComposeView") return view
            if (view is ViewGroup) for (index in 0 until view.childCount) composeView(view.getChildAt(index))?.let { return it }
            return null
        }
        fun nodes(): List<SemanticsNode> {
            fun descend(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descend)
            val view = requireNotNull(composeView(root))
            val owner = view.javaClass.getMethod("getSemanticsOwner").invoke(view) as SemanticsOwner
            return descend(owner.rootSemanticsNode)
        }
        fun button(description: String) = nodes().single { it.config.getOrNull(SemanticsProperties.ContentDescription) == listOf(description) }
        fun close() { controller.pause().stop().destroy(); recomposer.cancel(); scope.cancel() }
    }
}
