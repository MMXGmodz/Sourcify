package com.metrolist.music.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp

internal val PlayerVideoMorphSpec = tween<Dp>(520, easing = FastOutSlowInEasing)

@Composable
internal fun PlayerMediaSlot(
    videoRequested: Boolean,
    videoAspectRatio: Float,
    artworkHeight: Dp,
    modifier: Modifier = Modifier,
    video: @Composable () -> Unit,
    artwork: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val ratio = videoAspectRatio.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
        val targetHeight = if (videoRequested) maxWidth / ratio else artworkHeight
        val height by animateDpAsState(targetHeight, PlayerVideoMorphSpec, label = "playerVideoHeight")
        Box(Modifier.fillMaxWidth().height(height).clipToBounds(), contentAlignment = Alignment.Center) {
            PlayerMediaContent(videoRequested, video, artwork)
        }
    }
}

@Composable
internal fun PlayerMediaContent(videoRequested: Boolean, video: @Composable () -> Unit, artwork: @Composable () -> Unit) {
    Crossfade(targetState = videoRequested, modifier = Modifier.fillMaxSize(),
        animationSpec = tween(320, easing = FastOutSlowInEasing), label = "playerVideoArtwork") { showVideo ->
        if (showVideo) video() else artwork()
    }
}
