package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.constants.*
import com.metrolist.music.ui.component.DynamicSlider
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import kotlin.math.roundToInt

@Composable
internal fun DynamicProgressDialog(onDismiss: () -> Unit) {
    val (music, setMusic) = rememberPreference(DynamicProgressMusicKey, true)
    val (intensity, setIntensity) = rememberPreference(DynamicProgressIntensityKey, 100)
    val (_, setSquiggly) = rememberPreference(SquigglySliderKey, false)
    val (style, setStyle) = rememberEnumPreference(SliderStyleKey, SliderStyle.DEFAULT)
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.s13_dynamic)) },
        text = {
            DynamicProgressOptions(music, style == SliderStyle.DYNAMIC, setMusic, {
                setStyle(SliderStyle.DYNAMIC)
                setSquiggly(false)
            }, intensity, setIntensity)
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DynamicProgressOptions(
    music: Boolean,
    selected: Boolean,
    onMusic: (Boolean) -> Unit,
    onUse: () -> Unit,
    intensity: Int = 100,
    onIntensity: (Int) -> Unit = {},
) {
    var previewIntensity by remember(intensity) { mutableFloatStateOf(intensity.coerceIn(0, 100).toFloat()) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DynamicSlider(state = remember { SliderState(value = .55f, trackRange = 0f..1f) },
            amplitude = if (music) .8f * previewIntensity / 100f else 0f, onValueChange = {}, onValueChangeFinished = {}, enabled = false)
        Text(stringResource(R.string.s14_cover_color))
        Column {
            Text(stringResource(R.string.s1382_dynamic_intensity, previewIntensity.roundToInt()))
            Slider(
                value = previewIntensity,
                onValueChange = { previewIntensity = it.coerceIn(0f, 100f) },
                onValueChangeFinished = { onIntensity(previewIntensity.roundToInt().coerceIn(0, 100)) },
                valueRange = 0f..100f,
            )
            Text(stringResource(R.string.s1382_dynamic_intensity_desc), style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.s13_music))
                Text(stringResource(R.string.s13_music_desc), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = music, onCheckedChange = onMusic)
        }
        Button(onClick = onUse, enabled = !selected, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (selected) R.string.s14_dynamic_selected else R.string.s14_use_dynamic))
        }
    }
}
