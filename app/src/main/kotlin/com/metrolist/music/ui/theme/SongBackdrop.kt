package com.metrolist.music.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

internal fun songBackdropColors(coverColor: Color, background: Color): List<Color> = listOf(
    coverColor.copy(alpha = 0.5f).compositeOver(background),
    coverColor.copy(alpha = 0.16f).compositeOver(background),
    background,
)

val LocalSongColor = androidx.compose.runtime.compositionLocalOf { SourcifyThemeColor }

internal fun artworkAccent(color: Color, dark: Boolean): Color =
    androidx.compose.ui.graphics.lerp(color, if (dark) Color.White else Color.Black, if (dark) 0.35f else 0.2f)

@androidx.compose.runtime.Composable
fun screenHeaderBrush(): androidx.compose.ui.graphics.Brush {
    val height = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp *
        androidx.compose.ui.platform.LocalDensity.current.density
    return androidx.compose.ui.graphics.Brush.verticalGradient(
        songBackdropColors(LocalSongColor.current, androidx.compose.material3.MaterialTheme.colorScheme.background),
        endY = height.coerceAtLeast(1f),
    )
}
