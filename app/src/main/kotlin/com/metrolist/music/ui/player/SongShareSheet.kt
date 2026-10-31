package com.metrolist.music.ui.player

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import com.metrolist.music.lyrics.saveFetchedLyricsIfMissing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongShareSheet(song: MediaMetadata, onDismiss: () -> Unit, startWithLyrics: Boolean = false) {
    val context=LocalContext.current
    val database=LocalDatabase.current
    val lyrics by database.lyrics(song.id).collectAsStateWithLifecycle(initialValue=null)
    LaunchedEffect(song.id) {
        if (shareSongUrl(song.id) != null) withContext(Dispatchers.IO) {
            try {
                val saved = database.lyrics(song.id).first()
                if(saved == null || saved.lyrics == LyricsEntity.LYRICS_NOT_FOUND) withTimeoutOrNull(26000) {
                    val helper=dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext,
                        com.metrolist.music.di.LyricsHelperEntryPoint::class.java).lyricsHelper()
                    val result=helper.getLyrics(song)
                    if(result.lyrics.isNotBlank() && result.lyrics != LyricsEntity.LYRICS_NOT_FOUND)
                        database.saveFetchedLyricsIfMissing(LyricsEntity(song.id,result.lyrics,result.provider))
                }
            } catch(e:CancellationException){throw e} catch(_:Exception){}
        }
    }
    val lines=remember(lyrics) {
        val raw=lyrics?.lyrics?.takeIf { it!=LyricsEntity.LYRICS_NOT_FOUND }.orEmpty()
        if(com.metrolist.music.lyrics.lyricsTextLooksSynced(raw)) com.metrolist.music.lyrics.LyricsUtils.parseLyrics(raw).flatMap { it.text.lines() }.map(String::trim).filter(String::isNotBlank).take(1000)
        else raw.lines().map(String::trim).filter(String::isNotBlank).take(1000)
    }
    var lyricMode by remember(song.id) { mutableStateOf(startWithLyrics) }
    var selected by remember(song.id) { mutableStateOf(setOf(0)) }
    var picker by remember { mutableStateOf(startWithLyrics) }
    var palette by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    val chosen=if(lyricMode) selectedShareLines(lines,selected) else emptyList()
    val renderKey = Triple(song,chosen,palette)
    val rendered by produceState<Pair<Triple<MediaMetadata,List<String>,Int>,android.graphics.Bitmap>?>(null,renderKey) {
        value=null
        try { value=renderKey to withContext(Dispatchers.IO) { renderShareCard(context,song,chosen,palette) } }
        catch(e:CancellationException){throw e} catch(_:Exception){error=true}
    }
    val image=rendered?.takeIf { it.first == renderKey }?.second
    ModalBottomSheet(onDismissRequest=onDismiss, sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            image?.let { Image(it.asImageBitmap(),stringResource(R.string.s6_preview),Modifier.height(300.dp).aspectRatio(if(lyricMode)1f else 2f/3f)) }
                ?: CircularProgressIndicator()
            Row {
                FilterChip(!lyricMode,{lyricMode=false},label={Text(stringResource(R.string.s6_song))})
                Spacer(Modifier.width(12.dp))
                FilterChip(lyricMode,{lyricMode=true;picker=true},label={Text(stringResource(R.string.s6_lyrics))},enabled=lines.isNotEmpty())
            }
            Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                listOf(androidx.compose.ui.graphics.Color(0xFF515B73),androidx.compose.ui.graphics.Color.LightGray,androidx.compose.ui.graphics.Color(0xFF1C1C1C)).forEachIndexed { index,color ->
                    Surface(onClick={palette=index},shape=CircleShape,color=color,border=BorderStroke(if(palette==index)3.dp else 1.dp,MaterialTheme.colorScheme.onSurface),modifier=Modifier.size(38.dp)){}
                }
            }
            if(lyricMode) TextButton(onClick={picker=true}){Text(stringResource(R.string.s6_edit_lyrics))}
            if(error) Text(stringResource(R.string.s6_share_error))
            val url=shareSongUrl(song.id)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                if(url!=null) OutlinedButton(onClick={
                    (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .setPrimaryClip(android.content.ClipData.newPlainText("Sourcify",url))
                }){Text(stringResource(R.string.s6_copy_link))}
                Button(enabled=image!=null&&!busy&&(!lyricMode||chosen.isNotEmpty()),onClick={scope.launch{
                    val card = image ?: return@launch
                    busy=true;error=false
                    try {
                        val uri=withContext(Dispatchers.IO){saveShareCard(context,card)}
                        val share=Intent(Intent.ACTION_SEND).apply {
                            type="image/png";putExtra(Intent.EXTRA_STREAM,uri)
                            putExtra(Intent.EXTRA_TEXT,listOfNotNull(song.title,url).joinToString("\n"))
                            clipData=android.content.ClipData.newUri(context.contentResolver,"Sourcify",uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share,context.getString(R.string.share)))
                    } catch(e:CancellationException){throw e} catch(_:Exception){error=true}
                    finally{busy=false}
                }}){Text(stringResource(R.string.s6_share_image))}
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if(picker) AlertDialog(onDismissRequest={picker=false},title={Text(stringResource(R.string.s6_select_lyrics))},text={
        LazyColumn(Modifier.heightIn(max=420.dp)) {
            if(lines.isEmpty())item{Text(stringResource(R.string.lyrics_not_found))}
            itemsIndexed(lines) { index,line ->
                Row(Modifier.fillMaxWidth().clickable {
                    selected=if(index in selected) selected-index else if(selected.size<3)selected+index else selected
                }.padding(vertical=8.dp)) {
                    Checkbox(index in selected,{ checked -> selected=if(!checked)selected-index else if(selected.size<3)selected+index else selected })
                    Text(line,Modifier.padding(top=12.dp))
                }
            }
        }
    },confirmButton={TextButton(enabled=chosen.isNotEmpty(),onClick={picker=false}){Text(stringResource(android.R.string.ok))}})
}
