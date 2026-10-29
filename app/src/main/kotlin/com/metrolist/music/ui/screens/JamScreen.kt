package com.metrolist.music.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.R
import com.metrolist.music.jam.JamServer
import com.metrolist.music.jam.AndroidJam
import kotlinx.coroutines.delay
import org.json.JSONObject
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JamScreen(navController: NavController, showTopBar: Boolean = false,
    jam: AndroidJam? = LocalPlayerConnection.current?.service?.jam) {
    val manager = LocalListenTogetherManager.current
    val room = manager?.roomState?.collectAsStateWithLifecycle()?.value
    var localActive by remember { mutableStateOf(jam?.server?.active == true) }
    var online by rememberSaveable { mutableStateOf(!localActive) }
    LaunchedEffect(jam) { while (jam != null) { localActive = jam.server.active; delay(1000) } }
    LaunchedEffect(room, localActive) {
        if (room != null) online = true else if (localActive) online = false
    }
    val insets = LocalPlayerAwareWindowInsets.current
    Column(Modifier.fillMaxSize()) {
        if (showTopBar) TopAppBar(title = { Text(stringResource(R.string.sourcify_jam_hub_title)) }, navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back))
            }
        })
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(
            top = if (showTopBar) 8.dp else insets.asPaddingValues().calculateTopPadding() + 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = online, onClick = { online = true }, enabled = !localActive,
                label = { Text(stringResource(R.string.sourcify_jam_online)) }, modifier = Modifier.weight(1f))
            FilterChip(selected = !online, onClick = { online = false }, enabled = room == null,
                label = { Text(stringResource(R.string.sourcify_jam_wifi)) }, modifier = Modifier.weight(1f))
        }
        if (online) {
            Text(stringResource(R.string.sourcify_jam_online_description), Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { navController.navigate("settings/integrations/listen_together") }, Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.sourcify_jam_server_settings))
            }
        }
        Box(Modifier.weight(1f)) {
            if (online) ListenTogetherScreen(navController, embedded = true)
            else LocalJamScreen(jam, embedded = true)
        }
    }
}

@Composable
private fun LocalJamScreen(jam: AndroidJam?, embedded: Boolean = false) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("Sourcify") }
    var invitation by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var snapshot by remember(jam) { mutableStateOf(jam?.server?.state() ?: JSONObject().put("active", false)) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(jam, refresh) {
        while (jam != null) {
            snapshot = jam.server.state()
            delay(1000)
        }
    }
    val active = snapshot.optBoolean("active")
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val invalidInvite = stringResource(R.string.sourcify_jam_invalid_invite)
    val linkLabel = stringResource(R.string.sourcify_jam_invite)
    val unavailable = stringResource(R.string.sourcify_jam_unavailable)
    fun perform(action: () -> Unit) {
        error = ""
        try { action(); refresh++ } catch (e: Exception) { error = e.message ?: unavailable }
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = if (embedded) 12.dp else windowInsets.asPaddingValues().calculateTopPadding() + 12.dp,
                bottom = windowInsets.asPaddingValues().calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(stringResource(R.string.sourcify_jam_heading), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.sourcify_jam_description), style = MaterialTheme.typography.bodyLarge)
            }
            if (error.isNotEmpty()) item { Text(error, color = MaterialTheme.colorScheme.error) }
            if (!active) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(name, { name = it.take(32) }, label = { Text(stringResource(R.string.sourcify_jam_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Button(onClick = { perform { jam!!.start(name) } }, enabled = jam != null && name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.sourcify_jam_start))
                            }
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.sourcify_jam_join), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.sourcify_jam_join_description))
                            OutlinedTextField(invitation, { invitation = it }, label = { Text(linkLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedButton(onClick = {
                                if (!JamServer.validInvite(invitation)) error = invalidInvite
                                else perform { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(invitation.trim()))) }
                            }, enabled = invitation.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sourcify_jam_join)) }
                        }
                    }
                }
            } else {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.sourcify_jam_code, snapshot.optString("code")), style = MaterialTheme.typography.headlineSmall)
                            val link = snapshot.getJSONArray("links").optString(0)
                            Text(link, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(linkLabel, link))
                                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.sourcify_jam_copy)) }
                                OutlinedButton(onClick = {
                                    perform { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, link)
                                    }, linkLabel)) }
                                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.sourcify_jam_share)) }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.sourcify_jam_lock), modifier = Modifier.weight(1f))
                                Switch(snapshot.optBoolean("locked"), { jam!!.server.locked = it; refresh++ })
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.sourcify_jam_controls), modifier = Modifier.weight(1f))
                                Switch(snapshot.optBoolean("controlAllowed"), { jam!!.server.controlAllowed = it; refresh++ })
                            }
                            OutlinedButton(onClick = { perform { jam!!.stop() } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sourcify_jam_stop)) }
                        }
                    }
                }
                item { Text(stringResource(R.string.sourcify_jam_participants), style = MaterialTheme.typography.titleLarge) }
                val participants = snapshot.getJSONArray("participants")
                items((0 until participants.length()).map { participants.getJSONObject(it) }, key = { it.getString("id") }) { member ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(member.getString("name"), modifier = Modifier.weight(1f))
                        if (!member.optBoolean("host")) TextButton(onClick = { perform { jam!!.server.kick(member.getString("id")) } }) { Text(stringResource(R.string.sourcify_jam_kick)) }
                    }
                }
                item {
                    Text(stringResource(R.string.sourcify_jam_queue), style = MaterialTheme.typography.titleLarge)
                    val paused = snapshot.getJSONObject("playback").optBoolean("paused")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { perform { jam!!.control("previous") } }) { Text(stringResource(R.string.sourcify_jam_previous)) }
                        Button(onClick = { perform { jam!!.control(if (paused) "play" else "pause") } }) { Text(stringResource(if (paused) R.string.sourcify_jam_play else R.string.sourcify_jam_pause)) }
                        TextButton(onClick = { perform { jam!!.control("next") } }) { Text(stringResource(R.string.sourcify_jam_next)) }
                    }
                }
                val queue = snapshot.getJSONArray("queue")
                if (queue.length() == 0) item { Text(stringResource(R.string.sourcify_jam_empty)) }
                items((0 until queue.length()).toList(), key = { queue.getJSONObject(it).getString("entryId") }) { index ->
                    val entry = queue.getJSONObject(index)
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor =
                        if (index == snapshot.optInt("queueIndex")) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(entry.getJSONObject("track").optString("title"), fontWeight = FontWeight.Bold)
                            Text(entry.getJSONObject("track").optString("artist"), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.sourcify_jam_added_by, entry.optString("addedByName")), style = MaterialTheme.typography.labelSmall)
                            Row {
                                TextButton(onClick = { perform { jam!!.move(entry.getString("entryId"), -1) } }, enabled = index > 0 && jam?.shuffleEnabled == false) { Text(stringResource(R.string.sourcify_jam_up)) }
                                TextButton(onClick = { perform { jam!!.move(entry.getString("entryId"), 1) } }, enabled = index < queue.length() - 1 && jam?.shuffleEnabled == false) { Text(stringResource(R.string.sourcify_jam_down)) }
                                TextButton(onClick = { perform { jam!!.remove(entry.getString("entryId")) } }) { Text(stringResource(R.string.sourcify_jam_remove)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
