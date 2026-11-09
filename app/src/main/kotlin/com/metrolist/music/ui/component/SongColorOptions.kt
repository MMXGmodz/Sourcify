package com.metrolist.music.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.ui.theme.LocalSongColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongColorOptions(color: Int?, onChange: (Int?) -> Unit, enabled: Boolean = true) {
    val automaticColor = LocalSongColor.current.toArgb()
    val initial = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(color ?: automaticColor, it) } }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var saturation by remember { mutableFloatStateOf(initial[1]) }
    var brightness by remember { mutableFloatStateOf(initial[2]) }
    var emitted by remember { mutableStateOf(color) }
    LaunchedEffect(color) {
        if (color != emitted) {
            val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color ?: automaticColor, it) }
            hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
            emitted = color
        }
    }
    fun emit(value: Int?) { emitted = value; onChange(value) }
    fun change(h: Float = hue, s: Float = saturation, v: Float = brightness) {
        hue = h; saturation = s; brightness = v
        emit(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.s811_song_color), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().toggleable(color == null, enabled = enabled, role = Role.Switch,
            onValueChange = { automatic -> if (automatic) emit(null) else change() }).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.s811_color_automatic), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = color == null, onCheckedChange = null, enabled = enabled)
        }
        AnimatedVisibility(visible = color != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(Modifier.size(36.dp), color = Color(color ?: automaticColor), shape = CircleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {}
                    Text("#" + "%06X".format((color ?: automaticColor) and 0xFFFFFF), style = MaterialTheme.typography.labelLarge)
                }
                val channels = listOf(
                    Triple(R.string.s811_color_hue, hue, 0f..360f),
                    Triple(R.string.s811_color_saturation, saturation, 0f..1f),
                    Triple(R.string.s811_color_brightness, brightness, 0f..1f),
                )
                channels.forEachIndexed { index, (labelId, value, range) ->
                    val label = stringResource(labelId)
                    Text(label, style = MaterialTheme.typography.labelMedium)
                    val state = remember(index) { SliderState(value = value, trackRange = range) }
                    state.value = value
                    Slider(state = state, enabled = enabled, modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                        onValueChange = { selected -> when (index) {
                            0 -> change(h = selected)
                            1 -> change(s = selected)
                            else -> change(v = selected)
                        } })
                }
            }
        }
    }
}
