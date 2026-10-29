package com.metrolist.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

@Composable
internal fun CanvasBackdrop(scrollOffset: () -> Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().clipToBounds().background(Color.Black)) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = -scrollOffset().coerceAtLeast(0f) }) {
            content()
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                0f to Color.Transparent,
                0.52f to Color.Transparent,
                0.8f to Color.Black.copy(alpha = 0.7f),
                1f to Color.Black,
            )))
        }
    }
}
