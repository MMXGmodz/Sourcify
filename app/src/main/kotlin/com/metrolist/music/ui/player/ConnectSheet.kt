package com.metrolist.music.ui.player

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.connect.*
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.ExpandableBottomSheet
import com.metrolist.music.ui.component.SongColorOptions
import com.metrolist.music.ui.theme.LocalSongColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun DeviceIcon.drawable(): Int = when (this) {
    DeviceIcon.PHONE -> R.drawable.device_phone
    DeviceIcon.HEADPHONES -> R.drawable.connect_headphones
    DeviceIcon.EARBUDS -> R.drawable.connect_earbuds
    DeviceIcon.SPEAKER -> R.drawable.connect_speaker
    DeviceIcon.CAR -> R.drawable.connect_car
    DeviceIcon.TV -> R.drawable.cast
    DeviceIcon.COMPUTER -> R.drawable.connect_computer
}
internal fun DeviceIcon.label(): Int = when (this) {
    DeviceIcon.PHONE -> R.string.connect_phone
    DeviceIcon.HEADPHONES -> R.string.connect_headphones
    DeviceIcon.EARBUDS -> R.string.connect_earbuds
    DeviceIcon.SPEAKER -> R.string.connect_speaker
    DeviceIcon.CAR -> R.string.connect_car
    DeviceIcon.TV -> R.string.connect_tv
    DeviceIcon.COMPUTER -> R.string.connect_computer
}

@Composable
internal fun ConnectButton(state: ConnectState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val active = state.active?.takeUnless { it.id == "phone" }
    val color = if (active != null) Color(state.color) else Color.White
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(active?.let { state.icon(it).drawable() } ?: R.drawable.connect_devices), null, tint = color)
            Text(active?.let(state::name) ?: stringResource(R.string.connect_title), color = color,
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun openBluetoothSettings(context: Context) {
    try { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
    catch (_: Exception) { Toast.makeText(context, R.string.connect_settings_unavailable, Toast.LENGTH_LONG).show() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectSheet(controller: ConnectController, song: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by controller.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val permissions = remember { connectBluetoothPermissions(Build.VERSION.SDK_INT) }
    fun permitted() = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    var permission by remember { mutableStateOf(permitted()) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permission = permitted(); controller.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permission = permitted(); controller.refresh() }
    LaunchedEffect(controller) { controller.failures.collect { Toast.makeText(context, R.string.connect_failed, Toast.LENGTH_LONG).show() } }
    var editing by remember { mutableStateOf<AudioRoute?>(null) }
    var colorEditor by remember { mutableStateOf(false) }
    var draftColor by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    fun save(action: suspend () -> Unit) {
        saving = true
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { Toast.makeText(context, R.string.connect_save_failed, Toast.LENGTH_LONG).show() }
            finally { saving = false }
        }
    }
    ExpandableBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        ConnectContent(state, song, permission,
            onPermission = { if (Build.VERSION.SDK_INT >= 31) permissionRequest.launch(permissions) },
            onSelect = { route -> if (!controller.select(route.id)) openBluetoothSettings(context) },
            onEdit = { editing = it }, onFind = { openBluetoothSettings(context) },
            onColor = { draftColor = state.preferences.color; colorEditor = true })
    }
    editing?.let { route -> key(route.id) {
        val old = state.preferences.devices.find { it.id == route.id }
        var name by remember { mutableStateOf(old?.alias.orEmpty()) }
        var icon by remember { mutableStateOf(old?.icon ?: route.icon) }
        DefaultDialog(onDismiss = { if (!saving) editing = null }, title = { Text(stringResource(R.string.connect_edit)) }, buttons = {
            TextButton(enabled = !saving, onClick = { editing = null }) { Text(stringResource(android.R.string.cancel)) }
            TextButton(enabled = !saving, onClick = { save { controller.store.edit(route, name, icon); editing = null } }) { Text(stringResource(R.string.sourcify_edit_save)) }
        }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(80) }, enabled = !saving, singleLine = true,
                    label = { Text(stringResource(R.string.connect_name)) }, placeholder = { Text(route.name) }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.connect_name_description), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    DeviceIcon.entries.forEach { choice ->
                        OutlinedIconButton(onClick = { icon = choice }, enabled = !saving, modifier = Modifier.semantics { selected = choice == icon },
                            border = BorderStroke(if (choice == icon) 2.dp else 1.dp, if (choice == icon) Color(state.color) else MaterialTheme.colorScheme.outlineVariant)) {
                            Icon(painterResource(choice.drawable()), stringResource(choice.label()))
                        }
                    }
                }
            }
        }
    } }
    if (colorEditor) DefaultDialog(onDismiss = { if (!saving) colorEditor = false }, title = { Text(stringResource(R.string.connect_color)) }, buttons = {
        TextButton(enabled = !saving, onClick = { colorEditor = false }) { Text(stringResource(android.R.string.cancel)) }
        TextButton(enabled = !saving, onClick = { save { controller.store.color(draftColor); colorEditor = false } }) { Text(stringResource(R.string.sourcify_edit_save)) }
    }) {
        CompositionLocalProvider(LocalSongColor provides Color(CONNECT_BLUE)) {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                SongColorOptions(draftColor, { draftColor = it }, enabled = !saving,
                    titleRes = R.string.connect_color, automaticLabelRes = R.string.connect_default_color)
            }
        }
    }
}

@Composable
internal fun ConnectContent(state: ConnectState, song: String, permission: Boolean,
    onPermission: () -> Unit, onSelect: (AudioRoute) -> Unit, onEdit: (AudioRoute) -> Unit, onFind: () -> Unit, onColor: () -> Unit,
) {
    val active = state.active
    val remembered = state.preferences.devices.filter { saved -> saved.id != "phone" && saved.id != active?.id && state.routes.none { it.id == saved.id } }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.connect_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onColor) { Icon(painterResource(R.drawable.palette), stringResource(R.string.connect_color), tint = Color(state.color)) }
        }
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(active?.let { if (it.id == "phone" && state.name(it) == it.name) stringResource(R.string.connect_phone) else state.name(it) }
                        ?: stringResource(R.string.connect_unknown), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.connect_current), color = Color(state.color), style = MaterialTheme.typography.labelMedium)
                    if (song.isNotBlank()) Text(song, color = Color(state.color), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(painterResource(active?.let { state.icon(it).drawable() } ?: R.drawable.connect_devices), null,
                    tint = Color(state.color), modifier = Modifier.size(36.dp))
                if (active != null) IconButton(onClick = { onEdit(active) }) { Icon(painterResource(R.drawable.more_horiz), stringResource(R.string.connect_edit)) }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!permission) item { TextButton(onClick = onPermission) { Text(stringResource(R.string.connect_permission)) } }
            items(state.routes.filter { !it.selected }, key = { "route:${it.id}" }) { route ->
                ConnectDeviceRow(if (route.id == "phone" && state.name(route) == route.name) stringResource(R.string.connect_phone) else state.name(route), state.icon(route), stringResource(R.string.connect_switch), { onSelect(route) }, { onEdit(route) })
            }
            if (remembered.isNotEmpty()) item { Text(stringResource(R.string.connect_previous), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) }
            items(remembered, key = { "history:${it.id}" }) { device ->
                val route = AudioRoute(device.id, device.name, device.icon ?: DeviceIcon.SPEAKER)
                ConnectDeviceRow(device.alias ?: device.name, route.icon, stringResource(R.string.connect_disconnected), { onSelect(route) }, { onEdit(route) })
            }
        }
        FilledTonalButton(onClick = onFind, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(painterResource(R.drawable.bluetooth), null)
            Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.connect_find))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ConnectDeviceRow(name: String, icon: DeviceIcon, status: String, onSelect: () -> Unit, onEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon.drawable()), null, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onEdit) { Icon(painterResource(R.drawable.more_horiz), stringResource(R.string.connect_edit)) }
    }
}
