package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.ui.theme.LocalSongColor
import kotlin.math.roundToInt

private val LocalPlaylistCollapse = staticCompositionLocalOf<PlaylistCollapseState?> { null }

private class PlaylistCollapseState {
    var header: LayoutCoordinates? = null
    var title: LayoutCoordinates? = null
    var play: LayoutCoordinates? = null
    var titleOffset by mutableStateOf<Float?>(null)
    var playOffset by mutableStateOf<Float?>(null)
    var frame: State<PlaylistCollapseFrame> = mutableStateOf(PlaylistCollapseFrame(0f, false, 0f))

    fun measure() {
        val h = header?.takeIf { it.isAttached } ?: return
        title?.takeIf { it.isAttached }?.let { titleOffset = h.localPositionOf(it, Offset.Zero).y }
        play?.takeIf { it.isAttached }?.let { playOffset = h.localPositionOf(it, Offset.Zero).y }
    }
}

@Composable
internal fun Modifier.playlistHeaderAnchor(): Modifier {
    val state = LocalPlaylistCollapse.current ?: return this
    return onGloballyPositioned { state.header = it; state.measure() }
}

@Composable
internal fun Modifier.playlistTitleAnchor(): Modifier {
    val state = LocalPlaylistCollapse.current ?: return this
    return onGloballyPositioned { state.title = it; state.measure() }
        .graphicsLayer { alpha = 1f - state.frame.value.titleAlpha }
}

@Composable
internal fun Modifier.playlistPlayAnchor(): Modifier {
    val state = LocalPlaylistCollapse.current ?: return this
    val hidden = state.frame.value.docked
    return onGloballyPositioned { state.play = it; state.measure() }
        .graphicsLayer { alpha = if (hidden) 0f else 1f }
        .then(if (hidden) Modifier.clearAndSetSemantics {} else Modifier)
}

@Composable
internal fun PlaylistCollapsingContainer(
    listState: LazyListState,
    title: String,
    enabled: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onEditCover: (() -> Unit)? = null,
    onBackLongClick: () -> Unit = onBack,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    playEnabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = remember(listState) { PlaylistCollapseState() }
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.getTop(density).toFloat()
    val barBottom = topInset + with(density) { 64.dp.toPx() }
    val playSize = with(density) { 58.dp.toPx() }
    val transition = with(density) { 36.dp.toPx() }
    val frameState = remember(listState, enabled, barBottom, playSize, transition) {
        derivedStateOf {
            if (!enabled) PlaylistCollapseFrame(0f, false, 0f)
            else {
                val info = listState.layoutInfo
                val header = info.visibleItemsInfo.firstOrNull { it.key == "playlist_header" }
                // Lazy-list offsets exclude the leading content inset. Local anchors work for every header height.
                val top = header?.let { it.offset + info.beforeContentPadding.toFloat() }
                    ?: if (listState.firstVisibleItemIndex > 0) -100000f else null
                playlistCollapseFrame(
                    top?.let { y -> state.titleOffset?.let { y + it } },
                    top?.let { y -> state.playOffset?.let { y + it } },
                    barBottom, transition, playSize,
                )
            }
        }
    }
    state.frame = frameState
    val frame by frameState
    val backdrop = MaterialTheme.colorScheme.background
    val coverColor = LocalSongColor.current
    val barColor = lerp(backdrop, coverColor, 0.52f)
    val ink = MaterialTheme.colorScheme.onBackground
    CompositionLocalProvider(LocalPlaylistCollapse provides state) {
        Box(modifier.fillMaxSize().drawBehind {
            val info = listState.layoutInfo
            val header = info.visibleItemsInfo.firstOrNull { it.key == "playlist_header" }
            val travel = if (!enabled) 0f else header?.offset?.let { (-it).coerceAtLeast(0).toFloat() }
                ?: if (listState.firstVisibleItemIndex > 0) size.height else 0f
            val extent = with(density) { 680.dp.toPx() }
            drawRect(Brush.verticalGradient(listOf(lerp(backdrop, coverColor, 0.62f), backdrop),
                startY = -travel, endY = extent - travel))
        }) {
            content()
            if (enabled) {
                Box(Modifier.fillMaxWidth().height(with(density) { barBottom.toDp() })
                    .graphicsLayer { alpha = frame.titleAlpha }
                    .background(barColor))
                Row(Modifier.fillMaxWidth().padding(top = with(density) { topInset.toDp() }).height(64.dp)
                    .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, onLongClick = onBackLongClick, modifier = Modifier.size(48.dp)
                        .background(ink.copy(alpha = 0.12f * (1f - frame.titleAlpha)), CircleShape)) {
                        Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back), tint = ink)
                    }
                    Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        if (frame.titleAlpha > 0f) {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ink,
                                modifier = Modifier.graphicsLayer {
                                    alpha = frame.titleAlpha
                                    translationY = (1f - frame.titleAlpha) * with(density) { 10.dp.toPx() }
                                })
                        }
                    }
                    if (onEditCover != null) IconButton(onClick = onEditCover, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(R.drawable.edit), stringResource(R.string.edit_playlist_cover), tint = ink)
                    }
                    if (!frame.docked) {
                        IconButton(onClick = onSearch, modifier = Modifier.size(48.dp)) {
                            Icon(painterResource(R.drawable.search), stringResource(R.string.search), tint = ink)
                        }
                    } else Spacer(Modifier.size(48.dp))
                }
                if (frame.docked) {
                    Surface(onClick = onPlay, enabled = playEnabled, shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 20.dp)
                            .offset { IntOffset(0, frame.playTop.roundToInt()) }.size(58.dp)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.play), stringResource(R.string.play), tint = Color.Black, modifier = Modifier.size(29.dp))
                        }
                    }
                }
            }
        }
    }
}
