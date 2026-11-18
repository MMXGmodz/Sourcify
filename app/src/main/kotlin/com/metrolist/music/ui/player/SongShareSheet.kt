package com.metrolist.music.ui.player

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.lyrics.saveFetchedLyricsIfMissing
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.component.SongColorOptions
import com.metrolist.music.ui.theme.LocalSongColor
import com.metrolist.music.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongShareSheet(song: MediaMetadata, onDismiss: () -> Unit, startWithLyrics: Boolean = false) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val lyrics by database.lyrics(song.id).collectAsStateWithLifecycle(initialValue = null)
    var loadingLyrics by remember(song.id) { mutableStateOf(true) }
    LaunchedEffect(song.id) {
        try {
            if (shareSongUrl(song.id) != null) withContext(Dispatchers.IO) {
                val saved = database.lyrics(song.id).first()
                if (saved == null || saved.lyrics == LyricsEntity.LYRICS_NOT_FOUND) withTimeoutOrNull(26000) {
                    val helper = dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext,
                        com.metrolist.music.di.LyricsHelperEntryPoint::class.java).lyricsHelper()
                    val result = helper.getLyrics(song)
                    if (result.lyrics.isNotBlank() && result.lyrics != LyricsEntity.LYRICS_NOT_FOUND)
                        database.saveFetchedLyricsIfMissing(LyricsEntity(song.id, result.lyrics, result.provider))
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { }
        finally { loadingLyrics = false }
    }
    val lines = remember(lyrics) {
        val raw = lyrics?.lyrics?.takeIf { it != LyricsEntity.LYRICS_NOT_FOUND }.orEmpty()
        if (com.metrolist.music.lyrics.lyricsTextLooksSynced(raw)) com.metrolist.music.lyrics.LyricsUtils.parseLyrics(raw)
            .flatMap { it.text.lines() }.map(String::trim).filter(String::isNotBlank).take(1000)
        else raw.lines().map(String::trim).filter(String::isNotBlank).take(1000)
    }
    var lyricMode by remember(song.id) { mutableStateOf(startWithLyrics) }
    var selected by remember(song.id, lines) { mutableStateOf(emptySet<Int>()) }
    var picker by remember(song.id) { mutableStateOf(startWithLyrics) }
    var customColor by remember(song.id) { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val artwork by produceState<ShareArtwork?>(null, song.thumbnailUrl) {
        value = try { withContext(Dispatchers.IO) { loadShareArtwork(context, song.thumbnailUrl) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { ShareArtwork(null, shareCoverColor(null)) }
    }
    val backgroundColor = customColor ?: artwork?.color ?: shareCoverColor(null)
    val chosen = if (lyricMode) selectedShareLines(lines, selected) else emptyList()
    val renderKey = Triple(song, chosen, backgroundColor)
    val rendered by produceState<Pair<Triple<MediaMetadata, List<String>, Int>, android.graphics.Bitmap>?>(null, renderKey, artwork, picker) {
        value = null
        val loaded = artwork ?: return@produceState
        if (picker || (lyricMode && chosen.isEmpty())) return@produceState
        try {
            if (customColor != null) delay(120)
            value = renderKey to withContext(Dispatchers.Default) { renderShareCard(context, song, chosen, backgroundColor, loaded.cover) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true }
    }
    val image = rendered?.takeIf { it.first == renderKey }?.second
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (picker) MaterialTheme.colorScheme.surfaceContainerHigh else BottomSheetDefaults.ContainerColor,
    ) {
        if (picker) {
            ShareLyricsPicker(
                lines = lines, selected = selected, loading = loadingLyrics,
                onSelectionChange = { selected = it },
                onBack = { picker = false; if (chosen.isEmpty()) lyricMode = false },
                onContinue = { lyricMode = true; picker = false },
            )
        } else {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.height(300.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    image?.let { Image(it.asImageBitmap(), stringResource(R.string.s6_preview),
                        Modifier.fillMaxHeight().aspectRatio(it.width.toFloat() / it.height)) } ?: CircularProgressIndicator()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(!lyricMode, { lyricMode = false }, label = { Text(stringResource(R.string.s6_song)) })
                    FilterChip(lyricMode, { picker = true }, label = { Text(stringResource(R.string.s6_lyrics)) }, enabled = lines.isNotEmpty())
                }
                if (artwork != null) CompositionLocalProvider(LocalSongColor provides Color(artwork!!.color)) {
                    SongColorOptions(customColor, { customColor = it }, titleRes = R.string.s6_share_color)
                }
                if (lyricMode) TextButton(onClick = { picker = true }) { Text(stringResource(R.string.s6_edit_lyrics)) }
                if (error) Text(stringResource(R.string.s6_share_error), color = MaterialTheme.colorScheme.error)
                val url = shareSongUrl(song.id)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (url != null) OutlinedButton(onClick = {
                        (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                            .setPrimaryClip(android.content.ClipData.newPlainText("Sourcify", url))
                    }) { Text(stringResource(R.string.s6_copy_link)) }
                    Button(enabled = image != null && !busy && (!lyricMode || chosen.isNotEmpty()), onClick = {
                        scope.launch {
                            val card = image ?: return@launch
                            busy = true; error = false
                            try {
                                val uri = withContext(Dispatchers.IO) { saveShareCard(context, card) }
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_TEXT, listOfNotNull(song.title, url).joinToString("\n"))
                                    clipData = android.content.ClipData.newUri(context.contentResolver, "Sourcify", uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, context.getString(R.string.share)))
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { error = true }
                            finally { busy = false }
                        }
                    }) { Text(stringResource(R.string.s6_share_image)) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
internal fun ShareLyricsPicker(
    lines: List<String>, selected: Set<Int>, loading: Boolean,
    onSelectionChange: (Set<Int>) -> Unit, onBack: () -> Unit, onContinue: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back)) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.s6_edit_lyrics), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.s6_lyrics_selection_count, selected.size, MAX_SHARE_LYRIC_LINES),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(stringResource(R.string.s6_select_lyrics), style = MaterialTheme.typography.bodyMedium)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)) {
            if (lines.isEmpty()) item {
                if (loading) CircularProgressIndicator() else Text(stringResource(R.string.lyrics_not_found))
            }
            itemsIndexed(lines) { index, line ->
                val checked = index in selected
                Surface(shape = RoundedCornerShape(14.dp),
                    color = if (checked) Color.Black else Color.Transparent,
                    border = if (checked) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                    contentColor = if (checked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(line, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox,
                            enabled = checked || selected.size < MAX_SHARE_LYRIC_LINES,
                            onValueChange = { value -> onSelectionChange(if (value) selected + index else selected - index) }
                        ).padding(14.dp))
                }
            }
        }
        Button(onClick = onContinue, enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.s6_lyrics_continue))
        }
        Spacer(Modifier.height(12.dp))
    }
}
