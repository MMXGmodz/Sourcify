package com.metrolist.music.ui.menu

import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.R
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.utils.PlaylistArchivePlan
import com.metrolist.music.utils.PlaylistArchiveTrack
import com.metrolist.music.utils.preparePlaylistArchive
import com.metrolist.music.utils.safeArchiveName
import com.metrolist.music.utils.writePlaylistArchive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PlaylistArchiveDialog(
    playlistName: String,
    tracks: suspend () -> List<PlaylistArchiveTrack>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val downloads = LocalDownloadUtil.current
    val scope = rememberCoroutineScope()
    var plan by remember { mutableStateOf<PlaylistArchivePlan?>(null) }
    var failed by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var awaitingDestination by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val dismiss = { job?.cancel(); onDismiss() }
    val destination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        awaitingDestination = false
        val ready = plan
        if (uri != null && ready != null) {
            exporting = true
            job = scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        try {
                            val output = context.contentResolver.openOutputStream(uri, "wt")
                                ?: throw java.io.IOException("Destination is unavailable")
                            output.use {
                                writePlaylistArchive(ready, it) { count, total ->
                                    scope.launch { progress = count to total }
                                }
                            }
                            currentCoroutineContext().ensureActive()
                        } catch (failure: Throwable) {
                            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                            throw failure
                        }
                    }
                    Toast.makeText(context, context.getString(R.string.export_zip_complete, ready.available), Toast.LENGTH_LONG).show()
                    onDismiss()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { exporting = false; failed = true }
            }
        }
    }
    LaunchedEffect(Unit) {
        try {
            plan = withContext(Dispatchers.IO) { preparePlaylistArchive(context, downloads, playlistName, tracks()) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    DefaultDialog(
        onDismiss = dismiss,
        title = { Text(stringResource(R.string.export_zip_title)) },
        buttons = {
            TextButton(onClick = dismiss) { Text(stringResource(android.R.string.cancel)) }
            TextButton(
                enabled = plan?.available?.let { it > 0 } == true && !exporting && !awaitingDestination && !failed,
                onClick = { awaitingDestination = true; destination.launch("${safeArchiveName(playlistName)}.zip") },
            ) { Text(stringResource(R.string.export_zip_save)) }
        },
    ) {
        Column {
            Text(stringResource(R.string.export_zip_description))
            val ready = plan
            when {
                failed -> Text(stringResource(R.string.export_zip_failed))
                exporting -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.export_zip_progress, progress?.first ?: 0, ready?.available ?: 0))
                }
                ready == null -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.export_zip_preparing))
                }
                else -> {
                    Text(stringResource(R.string.export_zip_review, ready.available, ready.entries.size, ready.entries.size - ready.available))
                    if (ready.available == 0) Text(stringResource(R.string.export_zip_empty))
                }
            }
        }
    }
}
