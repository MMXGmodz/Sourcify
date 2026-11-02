package com.metrolist.music.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicSlider(state: SliderState, amplitude: Float, onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier,
    color: Color = com.metrolist.music.ui.theme.LocalSongColor.current) {
    val fast by animateFloatAsState(amplitude.coerceIn(0f, 1f), tween(100), label = "audioWaveFront")
    val middle by animateFloatAsState(amplitude.coerceIn(0f, 1f), tween(180), label = "audioWaveMiddle")
    val slow by animateFloatAsState(amplitude.coerceIn(0f, 1f), tween(280), label = "audioWaveBack")
    val accent by animateColorAsState(lerp(color, Color.White, if (color.luminance() < .15f) .35f else .08f), tween(400), label = "coverProgressColor")
    Slider(state = state, onValueChange = onValueChange, onValueChangeFinished = onValueChangeFinished,
        enabled = enabled, modifier = modifier.heightIn(min = 48.dp),
        thumb = {
            Canvas(Modifier.size(20.dp)) {
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = .18f), Color.Transparent), center, 15.dp.toPx()), 15.dp.toPx())
                drawCircle(Color.White, 9.dp.toPx())
                drawCircle(lerp(accent, Color.Black, .65f), 7.3.dp.toPx())
            }
        }, track = {
            Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                val fraction = if (state.trackRange.endInclusive > state.trackRange.start)
                    ((state.value - state.trackRange.start) / (state.trackRange.endInclusive - state.trackRange.start)).coerceIn(0f, 1f) else 0f
                val end = size.width * fraction
                val y = center.y
                drawLine(Color.White.copy(alpha = .30f), Offset(0f, y), Offset(size.width, y), 3.dp.toPx(), StrokeCap.Round)
                if (end > 0f) {
                    fun wave(level: Float, phase: Float, height: Float, ink: Color) {
                        if (level <= 0f) return
                        val path = Path().apply {
                            moveTo(0f, y)
                            for (i in 0..90) {
                                val t = i / 90f
                                val envelope = sin(t * PI).toFloat().coerceAtLeast(0f).pow(.85f)
                                val ripple = (.5f + .5f * cos((t * 2.25f + phase) * 2 * PI).toFloat()).pow(1.7f)
                                val rise = envelope * ripple * level * height * min(1f, end / 60.dp.toPx())
                                lineTo(end * t, y - rise)
                            }
                            lineTo(end, y); close()
                        }
                        drawPath(path, ink)
                    }
                    wave(slow, .08f, 8.dp.toPx(), accent.copy(alpha = .28f))
                    wave(middle, .33f, 7.dp.toPx(), lerp(accent, Color.White, .55f).copy(alpha = .42f))
                    wave(fast, -.15f, 5.5.dp.toPx(), Color.White.copy(alpha = .58f))
                    drawLine(accent.copy(alpha = .07f), Offset(0f, y), Offset(end, y), 8.dp.toPx(), StrokeCap.Round)
                    drawLine(accent, Offset(0f, y), Offset(end, y), 3.dp.toPx(), StrokeCap.Round)
                }
            }
        })
}
