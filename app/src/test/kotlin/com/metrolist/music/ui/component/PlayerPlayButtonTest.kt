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
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay
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
                    contentDescription = if (paused) "Play" else "Pause", style = PlayButtonStyle.METAL_RING,
                    accentColor = androidx.compose.ui.graphics.Color(0xFF38E578))
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

    @Test fun defaultMetalRingTracksTheCurrentCoverColorAcrossRecomposition() = withComposition {
        var cover by mutableStateOf(androidx.compose.ui.graphics.Color(0xFFFF4444))
        var captureBounds = Rect.Zero
        content {
            androidx.compose.runtime.CompositionLocalProvider(com.metrolist.music.ui.theme.LocalSongColor provides cover) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PlayerPlayButton(Modifier.size(112.dp).onGloballyPositioned { captureBounds = it.boundsInWindow() }, {},
                        iconRes = R.drawable.play, contentDescription = "Play", style = PlayButtonStyle.METAL_RING)
                }
            }
        }
        fun ringColor(): Int {
            frames()
            val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val x = captureBounds.center.x.toInt()
            val y = (captureBounds.center.y - captureBounds.width * .43f).toInt()
            return bitmap.getPixel(x, y).also { bitmap.recycle() }
        }
        val red = ringColor()
        assertTrue(android.graphics.Color.red(red) > android.graphics.Color.blue(red) + 80)
        cover = androidx.compose.ui.graphics.Color(0xFF4488FF)
        val blue = ringColor()
        assertTrue(android.graphics.Color.blue(blue) > android.graphics.Color.red(blue) + 80)
        assertTrue(android.graphics.Color.green(blue) < android.graphics.Color.blue(blue))
    }

    @Test fun videoSettingsPersistQualityAndLargerBrowsingVideo() = withComposition {
        val store = activity.dataStore
        val qualityKey = com.metrolist.music.constants.MusicVideoQualityKey
        val largeKey = com.metrolist.music.constants.MusicVideoLargeMiniPlayerKey
        val pipKey = com.metrolist.music.constants.MusicVideoPipEnabledKey
        val pipFeature = android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE
        val hadPipFeature = activity.packageManager.hasSystemFeature(pipFeature)
        shadowOf(activity.packageManager).setSystemFeature(pipFeature, true)
        val before = runBlocking { store.data.first() }
        try {
            runBlocking { store.edit { it.remove(qualityKey); it.remove(largeKey); it.remove(pipKey) } }
            content { Box(Modifier.fillMaxSize().background(Color(0xFF242628))) { com.metrolist.music.ui.player.MusicVideoSettingsContent(720) } }
            frames(20)
            val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            File("build/ui-previews/music-video-settings.png").apply { parentFile?.mkdirs() }.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            fun awaitSaved(check: (androidx.datastore.preferences.core.Preferences) -> Boolean) {
                val deadline = System.nanoTime() + 5_000_000_000L
                while (System.nanoTime() < deadline) {
                    frames(2)
                    if (check(runBlocking { store.data.first() })) return
                    Thread.sleep(5)
                }
                fail("Setting was not persisted while pumping native UI frames")
            }
            val large = nodes().first { it.config.getOrNull(SemanticsProperties.Role) == Role.Switch }
            assertTrue(requireNotNull(large.config[SemanticsActions.OnClick].action).invoke())
            frames(20)
            awaitSaved { it[largeKey] == true }
            val quality = nodes().first { it.config.getOrNull(SemanticsActions.OnClick)?.action != null &&
                it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == activity.getString(R.string.s86_video_quality) } == true }
            assertTrue(requireNotNull(quality.config[SemanticsActions.OnClick].action).invoke())
            frames(20)
            val fullHd = nodes().single { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton &&
                it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == "1080p" } == true }
            assertTrue(requireNotNull(fullHd.config[SemanticsActions.OnClick].action).invoke())
            frames(20)
            awaitSaved { it[qualityKey] == "P1080" }
            assertTrue(runBlocking { store.data.first()[largeKey] } == true)
            if (!com.metrolist.music.BuildConfig.TV_INTERFACE) {
                val pipToggle = nodes().last { it.config.getOrNull(SemanticsProperties.Role) == Role.Switch }
                assertFalse(pipToggle.config.contains(SemanticsProperties.Disabled))
                assertTrue(requireNotNull(pipToggle.config[SemanticsActions.OnClick].action).invoke())
                awaitSaved { it[pipKey] == false }
            }
        } finally {
            shadowOf(activity.packageManager).setSystemFeature(pipFeature, hadPipFeature)
            runBlocking { store.edit {
                before[qualityKey]?.let { value -> it[qualityKey] = value } ?: it.remove(qualityKey)
                before[largeKey]?.let { value -> it[largeKey] = value } ?: it.remove(largeKey)
                before[pipKey]?.let { value -> it[pipKey] = value } ?: it.remove(pipKey)
            } }
        }
    }

    @Test fun browsingVideoStaysInTheBottomCardWhenPreviewIsUnavailableAndOptionToggles() = withComposition {
        var large by mutableStateOf(false)
        var previewReady by mutableStateOf(false)
        var cardCoordinates: androidx.compose.ui.layout.LayoutCoordinates? = null
        var videoCoordinates: androidx.compose.ui.layout.LayoutCoordinates? = null
        fun cardBounds() = requireNotNull(cardCoordinates).boundsInWindow()
        fun videoBounds() = requireNotNull(videoCoordinates).boundsInWindow()
        content {
            Box(Modifier.fillMaxSize()) {
                val extra = com.metrolist.music.ui.player.videoMiniPlayerExtraHeight(large, true, 1f)
                val state = com.metrolist.music.ui.component.rememberBottomSheetState(
                    dismissedBound = 0.dp,
                    expandedBound = 800.dp, collapsedBound = 104.dp + com.metrolist.music.constants.MiniPlayerHeight + extra,
                    initialAnchor = com.metrolist.music.ui.component.collapsedAnchor)
                com.metrolist.music.ui.component.BottomSheet(state, collapsedContent = {
                    if (large) com.metrolist.music.ui.player.VideoMiniPlayerCard("Escape", "Nemzzz", true, false,
                        modifier = Modifier.onGloballyPositioned { cardCoordinates = it },
                        onExpand = {}, onPlayPause = {}, onLike = {}, video = {
                            Box(Modifier.fillMaxSize().onGloballyPositioned { videoCoordinates = it }
                                .background(if (previewReady) Color(0xFF307DF6) else Color.Black))
                        })
                    else Box(Modifier.size(360.dp, com.metrolist.music.constants.MiniPlayerHeight)
                        .onGloballyPositioned { cardCoordinates = it })
                }, content = {})
            }
        }
        frames(120)
        val baselineBottom = cardBounds().bottom
        large = true
        frames(120)
        assertEquals("The current-track card must stay above navigation, not float upward", baselineBottom, cardBounds().bottom, 1f)
        assertTrue("Collapsed video card bounds: " + cardBounds(), cardBounds().top > 540f)
        assertTrue(videoBounds().left >= 12f && videoBounds().right < 180f)
        assertTrue(videoBounds().height > 70f)
        val withoutSurface = cardBounds()
        previewReady = true
        frames(20)
        assertEquals(withoutSurface, cardBounds())
        val shot = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(shot))
        File("build/ui-previews/video-mini-player.png").apply { parentFile?.mkdirs() }.outputStream().use {
            shot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        shot.recycle()
        large = false
        frames(120)
        assertEquals(baselineBottom, cardBounds().bottom, 1f)
    }

    @Test fun browsingVideoKeepsTouchTargetsVisibleAtLargeFontWithoutActivatingTheCard() = withComposition {
        var expands = 0
        var plays = 0
        var likes = 0
        content {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(density.density, 1.8f)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    com.metrolist.music.ui.player.VideoMiniPlayerCard("A long music video title that must fit", "Artist name", true, false,
                        onExpand = { expands++ }, onPlayPause = { plays++ }, onLike = { likes++ },
                        video = { Box(Modifier.fillMaxSize().background(Color.Blue)) })
                }
            }
        }
        frames(20)
        var play = nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription) == listOf(activity.getString(R.string.player_pause)) && it.config.getOrNull(SemanticsActions.OnClick)?.action != null }
        val originalRight = play.boundsInRoot.right
        setSystemBarRightInset(48)
        frames(20)
        play = nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription) == listOf(activity.getString(R.string.player_pause)) && it.config.getOrNull(SemanticsActions.OnClick)?.action != null }
        assertEquals("Landscape navigation must move the play target out of the system bar", originalRight - 48f, play.boundsInRoot.right, 1f)
        assertTrue(play.boundsInRoot.width >= 48f && play.boundsInRoot.height >= 48f)
        assertTrue(play.boundsInRoot.bottom <= 800f)
        tap(play.boundsInRoot.center.x, play.boundsInRoot.center.y)
        frames()
        assertEquals(1, plays)
        assertEquals(0, expands)
        val like = nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription) == listOf(activity.getString(R.string.s87_toggle_favorite)) && it.config.getOrNull(SemanticsActions.OnClick)?.action != null }
        tap(like.boundsInRoot.center.x, like.boundsInRoot.center.y)
        frames()
        assertEquals(1, likes)
        assertEquals(0, expands)
    }

    @Test fun videoModeMovesControlsThroughIntermediatePositionsBeforeSettling() = withComposition {
        var requested by mutableStateOf(false)
        var slot: androidx.compose.ui.layout.LayoutCoordinates? = null
        var controls: androidx.compose.ui.layout.LayoutCoordinates? = null
        content {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                com.metrolist.music.ui.player.PlayerMediaSlot(requested, 16f / 9f, 360.dp,
                    modifier = Modifier.onGloballyPositioned { slot = it },
                    video = { Box(Modifier.fillMaxSize().background(Color.Blue)) },
                    artwork = { Box(Modifier.fillMaxSize().background(Color.Red)) })
                Box(Modifier.size(360.dp, 48.dp).onGloballyPositioned { controls = it })
            }
        }
        frames(20)
        val before = requireNotNull(slot).boundsInWindow().height
        assertEquals(360f, before, 1f)
        requested = true
        frames(1)
        val initial = requireNotNull(slot).boundsInWindow().height
        assertTrue("Video mode must start from the existing cover height, not jump to the target", initial > 205f)
        frames(10)
        val middle = requireNotNull(slot).boundsInWindow().height
        assertTrue("Intermediate video layout height: " + middle, middle in 205f..359f)
        assertEquals(requireNotNull(slot).boundsInWindow().bottom, requireNotNull(controls).boundsInWindow().top, 1f)
        val image = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(image))
        File("build/ui-previews/video-morph-midpoint.png").apply { parentFile?.mkdirs() }.outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
        frames(60)
        assertEquals(203f, requireNotNull(slot).boundsInWindow().height, 1f)
        assertEquals(requireNotNull(slot).boundsInWindow().bottom, requireNotNull(controls).boundsInWindow().top, 1f)
    }

    @Test fun rapidVideoModeReversalContinuesFromCurrentGeometryAndNeverDuplicatesVideo() = withComposition {
        var requested by mutableStateOf(false)
        var slot: androidx.compose.ui.layout.LayoutCoordinates? = null
        var videoLayers = 0
        var maximumVideoLayers = 0
        content {
            com.metrolist.music.ui.player.PlayerMediaSlot(requested, 16f / 9f, 360.dp,
                modifier = Modifier.onGloballyPositioned { slot = it },
                video = {
                    androidx.compose.runtime.DisposableEffect(Unit) {
                        videoLayers++
                        maximumVideoLayers = maxOf(maximumVideoLayers, videoLayers)
                        onDispose { videoLayers-- }
                    }
                    Box(Modifier.fillMaxSize().background(Color.Blue))
                }, artwork = { Box(Modifier.fillMaxSize().background(Color.Red)) })
        }
        frames(20)
        requested = true
        frames(10)
        val during = requireNotNull(slot).boundsInWindow().height
        requested = false
        flushAtCurrentTime()
        assertEquals("Retargeting must preserve the current geometry", during,
            requireNotNull(slot).boundsInWindow().height, 1f)
        frames(10)
        val returning = requireNotNull(slot).boundsInWindow().height
        assertTrue("Return to artwork must pass through intermediate geometry: " + returning,
            returning > during + 1f && returning < 359f)
        requested = true
        frames(4)
        requested = false
        frames(60)
        assertEquals(360f, requireNotNull(slot).boundsInWindow().height, 1f)
        assertEquals("Crossfade may keep artwork and video, but only one video subtree", 1, maximumVideoLayers)
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
            // DataStore emits on real IO threads; advancing virtual frames alone cannot await the new reader.
            runBlocking {
                withTimeout(5_000) {
                    while (observed != PlayButtonStyle.METAL_RING) {
                        frames(1)
                        delay(1)
                    }
                }
            }
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
        private var insetsView: View? = null
        init { scope.launch { recomposer.runRecomposeAndApplyChanges() } }
        fun content(block: @Composable () -> Unit) { activity.setContent(parent = recomposer) {
            insetsView = androidx.compose.ui.platform.LocalView.current
            MaterialTheme(content = block)
        } }
        fun frames(count: Int = 8) {
            repeat(count) {
                root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 360, 800)
                Snapshot.sendApplyNotifications()
                time += 16_666_667
                clock.sendFrame(time)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            }
            // Current Compose completes pending measure/place work during the native draw pass.
            val frame = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(frame))
            frame.recycle()
        }
        // Retarget/recompose without advancing animation time: real frame motion is not a jump.
        fun flushAtCurrentTime() {
            repeat(3) {
                Snapshot.sendApplyNotifications()
                clock.sendFrame(time)
                shadowOf(Looper.getMainLooper()).idle()
                root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 360, 800)
                val frame = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(frame))
                frame.recycle()
            }
        }
        fun setSystemBarRightInset(pixels: Int) {
            val insets = androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars(), androidx.core.graphics.Insets.of(0, 0, pixels, 0))
                .setVisible(androidx.core.view.WindowInsetsCompat.Type.navigationBars(), true).build()
            assertEquals(pixels, insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).right)
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(requireNotNull(insetsView), insets)
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
