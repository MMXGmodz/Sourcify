package com.metrolist.music.ui.menu

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.spotify.SPOTIFY_REDIRECT_URI
import com.metrolist.music.spotify.SpotifyAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SpotifyImportSetupDialog(account: SpotifyAccount, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val connected by account.connected.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var clientId by rememberSaveable { mutableStateOf(account.clientId) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.spotify_import_title)) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (connected) {
                    Text(stringResource(R.string.spotify_oauth_connected))
                    TextButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try { account.disconnect() }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { failed = true }
                            finally { busy = false }
                        }
                    }) { Text(stringResource(R.string.spotify_disconnect)) }
                } else {
                    Text(stringResource(R.string.spotify_setup_description), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { try { uriHandler.openUri("https://developer.spotify.com/dashboard") } catch (_: Exception) { failed = true } }) {
                        Text(stringResource(R.string.spotify_dashboard))
                    }
                    OutlinedTextField(clientId, { clientId = it.trim(); failed = false }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.spotify_client_id)) })
                    Text(stringResource(R.string.spotify_redirect_description), style = MaterialTheme.typography.bodySmall)
                    Text(SPOTIFY_REDIRECT_URI, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("Spotify redirect", SPOTIFY_REDIRECT_URI))
                        copied = true
                    }) {
                        Icon(painterResource(if (copied) R.drawable.check else R.drawable.content_copy), null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.spotify_copy_redirect))
                    }
                    Button(enabled = !busy && Regex("[a-fA-F0-9]{32}").matches(clientId), onClick = {
                        busy = true
                        failed = false
                        scope.launch {
                            try {
                                val url = account.beginAuthorization(clientId)
                                CustomTabsIntent.Builder().build().launchUrl(context, url)
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { failed = true }
                            finally { busy = false }
                        }
                    }) { Text(stringResource(R.string.spotify_connect)) }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text(stringResource(R.string.spotify_oauth_failed), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.spotify_access_description), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.spotify_setup_done)) } },
    )
}
