/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

val DefaultThemeColor = Color(0xFFED5564)
val SourcifyThemeColor = Color(0xFF8ED8FF)
private val LegacySourcifyThemeColor = Color(0xFF1ED760)
private val SourcifyTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp),
    )
}

@Composable
fun MetrolistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = SourcifyThemeColor,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Determine if system dynamic colors should be used (Android S+ and default theme color)
    val useSystemDynamicColor = (themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

    // Select the appropriate color scheme generation method
    val baseColorScheme = if (useSystemDynamicColor) {
        // Use standard Material 3 dynamic color functions for system wallpaper colors
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        // Use materialKolor only when a specific seed color is provided
        rememberDynamicColorScheme(
            seedColor = if (themeColor == LegacySourcifyThemeColor) SourcifyThemeColor else themeColor,
            isDark = darkTheme,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
            style = PaletteStyle.TonalSpot // Keep existing style
        )
    }

    // Apply pureBlack modification if needed, similar to original logic
    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme, themeColor) {
        val scheme = if (darkTheme && (themeColor == SourcifyThemeColor || themeColor == LegacySourcifyThemeColor)) {
            baseColorScheme.copy(
                primary = SourcifyThemeColor,
                secondary = SourcifyThemeColor,
                secondaryContainer = Color(0xFF242424),
                onSecondaryContainer = Color.White,
                onPrimary = Color.Black,
                primaryContainer = SourcifyThemeColor,
                onPrimaryContainer = Color.Black,
                background = Color(0xFF000000),
                onBackground = Color(0xFFFFFFFF),
                surface = Color(0xFF000000),
                onSurface = Color(0xFFFFFFFF),
                surfaceDim = Color(0xFF000000),
                surfaceBright = Color(0xFF3A3A3A),
                surfaceContainerLowest = Color(0xFF000000),
                surfaceContainerLow = Color(0xFF121212),
                surfaceContainer = Color(0xFF181818),
                surfaceContainerHigh = Color(0xFF242424),
                surfaceContainerHighest = Color(0xFF2A2A2A),
                onSurfaceVariant = Color(0xFFB3B3B3),
                outline = Color(0xFF727272),
                outlineVariant = Color(0xFF333333),
            )
        } else {
            val accent = artworkAccent(themeColor, darkTheme)
            val container = androidx.compose.ui.graphics.lerp(themeColor, if (darkTheme) Color.Black else Color.White, 0.7f)
            baseColorScheme.copy(
                primary = accent, secondary = accent, tertiary = accent,
                onPrimary = if (androidx.core.graphics.ColorUtils.calculateLuminance(accent.toArgb()) > 0.35) Color.Black else Color.White,
                primaryContainer = container, secondaryContainer = container,
                onPrimaryContainer = if (darkTheme) Color.White else Color.Black,
                onSecondaryContainer = if (darkTheme) Color.White else Color.Black,
            )
        }
        scheme.pureBlack(darkTheme && pureBlack)
    }

    // Use standard MaterialTheme instead of MaterialExpressiveTheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SourcifyTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(20.dp),
        ),
        content = content,
    )
}

fun Bitmap.extractThemeColor(): Color = Color(
    Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .rankedColors(1, DefaultThemeColor.toArgb())
        .first()
)

internal fun Palette.rankedColors(
    desiredColorCount: Int,
    fallbackColor: Int,
): List<Int> = Score.score(
    swatches.associate { it.rgb to it.population },
    desiredColorCount,
    fallbackColor,
    true,
)

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
