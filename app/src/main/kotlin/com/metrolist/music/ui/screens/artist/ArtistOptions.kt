package com.metrolist.music.ui.screens.artist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import com.metrolist.music.utils.ArtistBlocks
import kotlinx.coroutines.launch

@Composable
internal fun ArtistOptions(name: String, artistId: String, followed: Boolean, onFollow: () -> Unit, onConcerts: () -> Unit, onDismiss: () -> Unit, shareLink: String? = null) {
 val context = LocalContext.current
 val blocks by remember { ArtistBlocks.observe(context) }.collectAsState(initial = emptyMap())
 val scope = rememberCoroutineScope()
 var sharing by remember { mutableStateOf(false) }
 val link = shareLink
 AlertDialog(onDismissRequest = onDismiss, title = { Text(if (sharing) stringResource(R.string.share) else name) },
  text = { androidx.compose.foundation.layout.Column {
   if (sharing) {
    TextButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, if (link == null) name else "$name\n$link"), null)) }) { Text(stringResource(R.string.share)) }
    if (link != null) TextButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(name, link)); onDismiss() }) { Text(stringResource(R.string.copy_link)) }
   } else {
    TextButton(onClick = { sharing = true }) { Text(stringResource(R.string.share)) }
    TextButton(onClick = { onFollow(); onDismiss() }) { Text(stringResource(if (followed) R.string.s80_unfollow else R.string.subscribe)) }
    TextButton(onClick = { onDismiss(); onConcerts() }) { Text(stringResource(R.string.s80_artist_concerts)) }
    TextButton(onClick = { scope.launch { ArtistBlocks.set(context, artistId, name, artistId !in blocks); onDismiss() } }) {
     Text(stringResource(if (artistId in blocks) R.string.s80_unblock else R.string.s80_block))
    }
   }
  } }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}
