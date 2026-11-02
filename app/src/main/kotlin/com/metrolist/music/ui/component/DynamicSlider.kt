package com.metrolist.music.ui.component

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
    onValueChangeFinished: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val level by animateFloatAsState(amplitude.coerceIn(0f, 1f), tween(85), label = "audioWave")
    val green = Color(0xFF00FF88)
    Slider(state = state, onValueChange = onValueChange, onValueChangeFinished = onValueChangeFinished,
        enabled = enabled, modifier = modifier.heightIn(min = 48.dp),
        thumb = {
            Canvas(Modifier.size(24.dp)) {
                drawCircle(Brush.radialGradient(listOf(green.copy(alpha = .65f), Color.Transparent), center, 25.dp.toPx()), 25.dp.toPx())
                drawCircle(Color.White, 12.dp.toPx())
                drawCircle(green, 9.dp.toPx())
            }
        }, track = {
            Canvas(Modifier.fillMaxWidth().height(44.dp)) {
                val fraction = if (state.trackRange.endInclusive > state.trackRange.start)
                    ((state.value - state.trackRange.start) / (state.trackRange.endInclusive - state.trackRange.start)).coerceIn(0f, 1f) else 0f
                val end = size.width * fraction
                val y = center.y
                drawLine(Color.White.copy(alpha = .30f), Offset(0f, y), Offset(size.width, y), 6.dp.toPx(), StrokeCap.Round)
                if (end > 0f) {
                    val width = min(end, 110.dp.toPx())
                    val path = Path().apply {
                        moveTo(end - width, y)
                        for (i in 0..40) {
                            val t = i / 40f
                            val height = sin(t * PI).toFloat().pow(1.6f) * level * 18.dp.toPx()
                            lineTo(end - width + t * width, y - height)
                        }
                        lineTo(end, y); close()
                    }
                    drawPath(path, Brush.verticalGradient(listOf(Color(0xFF62FFD5), green.copy(alpha = .8f))))
                    drawLine(green.copy(alpha = .10f), Offset(0f, y), Offset(end, y), 18.dp.toPx(), StrokeCap.Round)
                    drawLine(green.copy(alpha = .20f), Offset(0f, y), Offset(end, y), 12.dp.toPx(), StrokeCap.Round)
                    drawLine(green, Offset(0f, y), Offset(end, y), 6.dp.toPx(), StrokeCap.Round)
                }
            }
        })
}
