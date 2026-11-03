package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun PlaylistArtworkHeader(title: String, content: @Composable BoxScope.() -> Unit) {
    val configuration = LocalConfiguration.current
    val height = configuration.screenWidthDp.coerceAtMost(540).dp
    Box(Modifier.fillMaxWidth().height(height).clipToBounds()) {
        // Fade the artwork and its title scrim together so neither paints a band over the page.
        Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                fun smooth(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
                val edges = Array(41) { index ->
                    val position = index / 40f
                    val alpha = when {
                        position < .28f -> smooth(position / .28f)
                        position > .58f -> 1f - smooth((position - .58f) / .42f)
                        else -> 1f
                    }
                    position to Color.White.copy(alpha = alpha)
                }
                val mask = Brush.verticalGradient(*edges)
                val scrim = Brush.verticalGradient(
                    0f to Color.Transparent,
                    .5f to Color.Transparent,
                    .8f to Color.Black.copy(alpha = .72f),
                    1f to Color.Black.copy(alpha = .72f),
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(scrim)
                    drawRect(mask, blendMode = BlendMode.DstIn)
                }
            }) { content() }
        Text(title, style = MaterialTheme.typography.headlineLarge.copy(shadow = Shadow(
                color = MaterialTheme.colorScheme.background.copy(alpha = .8f), offset = Offset(0f, 1f), blurRadius = 8f)), fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 28.dp, vertical = 36.dp).playlistTitleAnchor())
    }
}
