package com.metrolist.music.ui.screens.settings.integrations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.utils.*
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LosslessProviderScreen(provider: LosslessProvider, navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val connection = LocalPlayerConnection.current
    val repository = remember(context) { LosslessRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var endpoint by rememberSaveable(provider) { mutableStateOf(repository.endpoint(provider)) }
    var query by rememberSaveable(provider) { mutableStateOf("") }
    var best by rememberSaveable(provider) { mutableStateOf(false) }
    var results by remember(provider) { mutableStateOf<List<LosslessTrack>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var status by remember(provider) { mutableStateOf<String?>(null) }
    var bytes by remember { mutableLongStateOf(0) }
    var total by remember { mutableLongStateOf(-1) }
    val errorText = stringResource(R.string.lossless_error)
    fun runWork(work: suspend () -> Unit) {
        if (busy) return
        busy = true; status = null; bytes = 0; total = -1
        job = scope.launch {
            try { work() }
            catch (error: CancellationException) { status = context.getString(R.string.lossless_cancelled); throw error }
            catch (_: Exception) { status = errorText }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
        TopAppBar(title = { Text(provider.label) }, navigationIcon = {
            IconButton(onClick = { navController.navigateUp() }) { Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.cd_back)) }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(if (provider == LosslessProvider.DEEZER) R.string.lossless_deezer_setup else R.string.lossless_api_setup), style = MaterialTheme.typography.bodyMedium)
            }
            item {
                OutlinedTextField(endpoint, { endpoint = it; status = null; results = emptyList() }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.lossless_api_address)) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy, onClick = {
                        try { repository.saveEndpoint(provider, endpoint); status = context.getString(R.string.lossless_saved) }
                        catch (_: Exception) { status = context.getString(R.string.lossless_invalid_url) }
                    }) { Text(stringResource(R.string.lossless_save)) }
                    TextButton(enabled = !busy && endpoint.isNotBlank(), onClick = { runWork {
                        repository.test(provider, endpoint)
                        repository.saveEndpoint(provider, endpoint)
                        status = context.getString(R.string.lossless_verified)
                    } }) { Text(stringResource(R.string.lossless_test)) }
                    TextButton(enabled = !busy, onClick = {
                        endpoint = provider.defaultEndpoint; repository.saveEndpoint(provider, endpoint); results = emptyList(); status = null
                    }) { Text(stringResource(R.string.lossless_reset)) }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.lossless_best), Modifier.weight(1f))
                    Switch(best, { best = it }, enabled = !busy)
                }
                Text(stringResource(R.string.lossless_download_description), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { navController.navigate("local_audio") }) { Text(stringResource(R.string.local_audio_title)) }
            }
            item {
                OutlinedTextField(query, { query = it }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.lossless_search)) })
                Button(enabled = !busy && query.isNotBlank() && endpoint.isNotBlank(), onClick = { runWork {
                    results = emptyList()
                    results = repository.search(provider, endpoint, query)
                    status = context.getString(if (results.isEmpty()) R.string.lossless_no_results else R.string.lossless_results)
                } }) { Text(stringResource(R.string.search)) }
            }
            if (busy) item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (bytes > 0) Text(if (total > 0) "${bytes / 1048576} / ${total / 1048576} MiB" else "${bytes / 1048576} MiB")
                TextButton(onClick = { job?.cancel() }) { Text(stringResource(R.string.cancel)) }
            }
            status?.let { text -> item { Text(text, style = MaterialTheme.typography.bodySmall) } }
            items(results, key = { it.id }) { track ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !busy && connection != null) { runWork {
                    val local = repository.acquire(track, endpoint, best) { read, length -> bytes = read; total = length }
                    withContext(Dispatchers.IO) { database.insert(local.mediaMetadata) }
                    connection?.playQueue(ListQueue(provider.label, listOf(local.mediaMetadata.toMediaItem()), 0))
                    status = listOfNotNull(local.codec, local.bitDepth?.let { "$it Bit" }, local.sampleRate?.let { "${it / 1000.0} kHz" }).joinToString(" · ")
                } }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(track.artwork, null, Modifier.size(48.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    Icon(painterResource(R.drawable.download), stringResource(R.string.lossless_download))
                }
            }
        }
    }
}
