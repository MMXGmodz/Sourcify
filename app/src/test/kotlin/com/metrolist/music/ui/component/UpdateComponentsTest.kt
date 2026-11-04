package com.metrolist.music.ui.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.metrolist.music.R
import com.metrolist.music.ui.theme.MetrolistTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
class UpdateComponentsTest {
    @Test fun allStagesRetainHonestStatusAndOnlyReadyOffersInstallation() = withComposition {
        var stage by mutableStateOf(UpdateStage.CHECKING)
        var paused by mutableStateOf(false)
        var progress by mutableStateOf<Float?>(.42f)
        var permissionDenied by mutableStateOf(false)
        var installerOpened by mutableStateOf(false)
        var installs = 0
        content {
            UpdateScreenContent(stage, "13.8.7", progress, paused, "Network unavailable", permissionDenied, installerOpened,
                onInstall = { installs++ }, onRetry = {}, onOpenDownloads = {}, onClose = {})
        }
        val labels = mapOf(
            UpdateStage.CHECKING to R.string.checking_for_updates,
            UpdateStage.DOWNLOADING to R.string.s1387_update_downloading_title,
            UpdateStage.VERIFYING to R.string.s1387_update_verifying_title,
            UpdateStage.READY to R.string.s1387_update_ready_title,
            UpdateStage.UP_TO_DATE to R.string.s10_up_to_date,
            UpdateStage.INSTALLED to R.string.s1380_update_install_success,
            UpdateStage.ERROR to R.string.s1380_update_failed_title,
        )
        for ((value, label) in labels) {
            stage = value
            frames()
            assertTrue("Missing $value status", hasText(activity.getString(label)))
            assertEquals(value == UpdateStage.READY, buttonOrNull(R.string.s1380_update_install) != null)
            if (value == UpdateStage.DOWNLOADING) {
                val indicator = nodes().single { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.current == .42f }
                assertEquals(0f..1f, indicator.config[SemanticsProperties.ProgressBarRangeInfo].range)
                paused = true
                progress = null
                frames()
                assertTrue(hasText(activity.getString(R.string.s1380_update_paused)))
                assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) == ProgressBarRangeInfo.Indeterminate })
            }
        }
        stage = UpdateStage.READY
        permissionDenied = true
        frames()
        assertTrue(hasText(activity.getString(R.string.s1380_update_permission_denied)))
        permissionDenied = false
        installerOpened = true
        frames()
        assertTrue(hasText(activity.getString(R.string.s1380_update_install_cancelled)))
        scrollToBottom()
        click(R.string.s1380_update_install)
        assertEquals(1, installs)
    }

    @Test fun darkAndLightNativePaintingUsesSoftBlueSurfacesAndReadableActions() = withComposition {
        var dark by mutableStateOf(true)
        content {
            MetrolistTheme(darkTheme = dark) {
                UpdateScreenContent(UpdateStage.READY, "13.8.7", null, false, "", false, false, {}, {}, {}, {})
            }
        }
        for (isDark in listOf(true, false)) {
            dark = isDark
            frames()
            val bitmap = capture(if (isDark) "update-ready-dark" else "update-ready-light")
            val hero = bitmap.getPixel(30, 80)
            val blue = android.graphics.Color.blue(hero)
            val red = android.graphics.Color.red(hero)
            assertTrue("Hero must have a visible theme-blue tint", blue > red + 5)
            assertTrue("Hero must retain its soft surface in dark mode", !isDark || blue > 30)
            assertTrue("Gradient must paint multiple tones", hero != bitmap.getPixel(30, 235))
            bitmap.recycle()
            scrollToBottom()
            assertTarget(R.string.s1380_update_install)
            assertTarget(R.string.close)
            scrollToTop()
        }
    }

    @Test fun largeTextAndLongErrorRemainScrollableWithReachableRetryDownloadsAndClose() = withComposition {
        var retries = 0
        var downloads = 0
        var closes = 0
        content(fontScale = 1.8f) {
            UpdateScreenContent(UpdateStage.ERROR, "13.8.7", null, false,
                "The network could not be reached. ".repeat(12), false, false, {}, { retries++ }, { downloads++ }, { closes++ })
        }
        frames()
        capture("update-error-large-text").recycle()
        assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.maxValue?.invoke()?.let { max -> max > 0f } == true })
        scrollToBottom()
        capture("update-error-large-text-actions").recycle()
        for (id in listOf(R.string.s1380_update_retry, R.string.s1380_update_open_downloads, R.string.close)) {
            assertTarget(id)
            click(id)
        }
        assertEquals(listOf(1, 1, 1), listOf(retries, downloads, closes))
    }

    @Test fun availableDialogContentKeepsLongReleaseNotesAndBothActionsAccessible() = withComposition {
        var updates = 0
        var dismissals = 0
        content(fontScale = 1.8f) {
            Surface {
                SourcifyUpdateAvailableContent("13.8.7", "Release notes for your music. ".repeat(30), { updates++ }, { dismissals++ })
            }
        }
        frames()
        assertTrue(hasText(activity.getString(R.string.latest_version_format, "13.8.7")))
        capture("update-available-large-text").recycle()
        scrollToBottom()
        capture("update-available-large-text-actions").recycle()
        assertTarget(R.string.s1380_update_install)
        assertTarget(R.string.s1387_update_later)
        click(R.string.s1380_update_install)
        click(R.string.s1387_update_later)
        assertEquals(1, updates)
        assertEquals(1, dismissals)
    }

    private fun withComposition(test: Harness.() -> Unit) {
        val harness = Harness()
        try { harness.test() } finally { harness.close() }
    }

    private class Harness {
        private val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        private val root: View = activity.window.decorView
        private val clock = BroadcastFrameClock()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clock)
        private val recomposer = Recomposer(scope.coroutineContext)
        private var time = 0L
        init { scope.launch { recomposer.runRecomposeAndApplyChanges() } }
        fun content(fontScale: Float = 1f, block: @Composable () -> Unit) {
            activity.setContent(parent = recomposer) {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                    MetrolistTheme(darkTheme = true, content = block)
                }
            }
        }
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
        private fun composeView(view: View): View? {
            if (view.javaClass.simpleName == "AndroidComposeView") return view
            if (view is ViewGroup) for (index in 0 until view.childCount) composeView(view.getChildAt(index))?.let { return it }
            return null
        }
        fun nodes(): List<SemanticsNode> {
            fun descend(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descend)
            val owner = requireNotNull(composeView(root)).javaClass.getMethod("getSemanticsOwner").invoke(composeView(root)) as SemanticsOwner
            return descend(owner.rootSemanticsNode)
        }
        fun hasText(text: String) = nodes().any { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true }
        fun buttonOrNull(id: Int) = nodes().singleOrNull { node ->
            node.config.getOrNull(SemanticsProperties.Role) == Role.Button &&
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == activity.getString(id) } == true
        }
        fun click(id: Int) {
            assertTrue(requireNotNull(requireNotNull(buttonOrNull(id)).config[SemanticsActions.OnClick].action).invoke())
            frames()
        }
        fun assertTarget(id: Int) {
            val bounds = requireNotNull(buttonOrNull(id)).boundsInRoot
            assertTrue("${activity.getString(id)} must have a 48dp touch target: $bounds", bounds.width >= 48f && bounds.height >= 48f)
            assertTrue("${activity.getString(id)} must be inside the viewport: $bounds", bounds.top >= 0f && bounds.bottom <= 800f && bounds.right <= 360f)
        }
        private fun scroll(delta: Float) {
            val node = nodes().first { it.config.getOrNull(SemanticsActions.ScrollBy)?.action != null }
            assertTrue(requireNotNull(node.config[SemanticsActions.ScrollBy].action).invoke(0f, delta))
            repeat(60) {
                frames(4)
                val range = requireNotNull(nodes().firstNotNullOfOrNull {
                    it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                })
                val target = if (delta > 0) range.maxValue() else 0f
                if (kotlin.math.abs(range.value() - target) < 1f) return
            }
            val range = nodes().firstNotNullOf { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) }
            fail("Animated scroll did not reach its boundary: ${range.value()} / ${range.maxValue()}")
        }
        fun scrollToBottom() = scroll(100_000f)
        fun scrollToTop() = scroll(-100_000f)
        fun capture(name: String): Bitmap {
            val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            File("build/ui-previews/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return bitmap
        }
        fun close() { controller.pause().stop().destroy(); recomposer.cancel(); scope.cancel() }
    }
}
