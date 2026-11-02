package com.metrolist.music.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

data class SongEditCanvas(val uri: Uri, val isImage: Boolean)

/** Per-song display edits never change the shared artist records or provider IDs. */
class SongEditStore internal constructor(context: Context) {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("sourcify_song_edits", Context.MODE_PRIVATE)
    private val directory = File(this.context.filesDir, "song_edits")
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()

    private fun record(id: String): JSONObject? = preferences.getString(id, null)?.let {
        runCatching { JSONObject(it) }.getOrNull()
    }

    fun hasEdits(id: String): Boolean = record(id) != null

    fun resolve(metadata: MediaMetadata): MediaMetadata {
        val edit = record(metadata.id) ?: return metadata
        val names = edit.optJSONArray("artists") ?: JSONArray()
        return metadata.copy(
            title = edit.optString("title", metadata.title),
            artists = (0 until names.length()).map { index ->
                MediaMetadata.Artist(metadata.artists.getOrNull(index)?.id, names.getString(index))
            },
            thumbnailUrl = ownedUri(edit.optString("cover"))?.toString() ?: metadata.thumbnailUrl,
        )
    }

    fun canvas(id: String): SongEditCanvas? {
        val edit = record(id) ?: return null
        return ownedUri(edit.optString("canvas"))?.let { SongEditCanvas(it, edit.optBoolean("canvasImage")) }
    }

    fun cover(id: String): Uri? = record(id)?.let { ownedUri(it.optString("cover")) }

    fun original(metadata: MediaMetadata): MediaMetadata {
        val original = record(metadata.id)?.optJSONObject("original") ?: return metadata
        return metadata.copy(
            title = original.getString("title"),
            thumbnailUrl = original.optString("cover").takeIf { it.isNotEmpty() },
            artists = original.getJSONArray("artists").let { artists ->
                (0 until artists.length()).map { index -> artists.getJSONObject(index).let {
                    MediaMetadata.Artist(it.optString("id").takeIf(String::isNotEmpty), it.getString("name"))
                } }
            },
        )
    }

    fun save(metadata: MediaMetadata, title: String, artistNames: List<String>, cover: Uri?, canvas: SongEditCanvas?, cleanUp: Boolean = true) {
        require(title.trim().isNotEmpty() && title.trim().length <= 300)
        require(artistNames.size <= 30 && artistNames.all { it.isNotBlank() && it.length <= 300 })
        require(cover == null || ownedUri(cover.toString()) != null)
        require(canvas == null || ownedUri(canvas.uri.toString()) != null)
        val previous = record(metadata.id)
        val original = previous?.optJSONObject("original") ?: JSONObject().apply {
            put("title", metadata.title)
            put("cover", metadata.thumbnailUrl.orEmpty())
            put("artists", JSONArray().apply { metadata.artists.forEach { artist ->
                put(JSONObject().put("id", artist.id.orEmpty()).put("name", artist.name))
            } })
        }
        val edit = JSONObject().put("original", original).put("title", title.trim())
            .put("artists", JSONArray(artistNames)).put("cover", cover?.toString().orEmpty())
            .put("canvas", canvas?.uri?.toString().orEmpty()).put("canvasImage", canvas?.isImage ?: false)
        if (!preferences.edit().putString(metadata.id, edit.toString()).commit()) throw IOException("Could not save song edits")
        mutableRevision.value++
        if (cleanUp) cleanPrevious(previous)
    }

    fun clear(id: String, cleanUp: Boolean = true) {
        val previous = record(id)
        if (!preferences.edit().remove(id).commit()) throw IOException("Could not reset song edits")
        mutableRevision.value++
        if (cleanUp) cleanPrevious(previous)
    }

    suspend fun updateSong(database: MusicDatabase, metadata: MediaMetadata, title: String, artistNames: List<String>,
        cover: Uri?, canvas: SongEditCanvas?, reset: Boolean = false): Song = withContext(NonCancellable + Dispatchers.IO) {
        val previous = preferences.getString(metadata.id, null)
        val original = original(metadata)
        try {
            database.withTransaction {
                if (database.song(metadata.id).first() == null) database.insert(original)
                val song = requireNotNull(database.song(metadata.id).first())
                database.update(song.song.copy(
                    title = if (reset) original.title else title.trim(),
                    thumbnailUrl = if (reset) original.thumbnailUrl else cover?.toString() ?: original.thumbnailUrl,
                ))
                // A preference write failure must roll back the database edit too.
                if (reset) clear(metadata.id, cleanUp = false)
                else save(metadata, title, artistNames, cover, canvas, cleanUp = false)
            }
        } catch (failure: Throwable) {
            val rollback = preferences.edit()
            if (previous == null) rollback.remove(metadata.id) else rollback.putString(metadata.id, previous)
            if (!rollback.commit()) failure.addSuppressed(IOException("Could not restore previous song edits"))
            mutableRevision.value++
            throw failure
        }
        cleanPrevious(previous?.let(::JSONObject))
        requireNotNull(database.song(metadata.id).first())
    }

    private fun cleanPrevious(previous: JSONObject?) {
        listOf("cover", "canvas").forEach { key -> previous?.optString(key)?.let(::deleteIfUnused) }
    }

    /** Copy picker bytes into durable private storage; the provider URI is never retained. */
    suspend fun importMedia(uri: Uri, imageOnly: Boolean): SongEditCanvas = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Choose a file using the media picker" }
        val mime = context.contentResolver.getType(uri).orEmpty()
        val image = mime.startsWith("image/")
        require(image || (!imageOnly && mime.startsWith("video/"))) { "Choose an image or video" }
        check(directory.mkdirs() || directory.isDirectory)
        val target = File(directory, UUID.randomUUID().toString())
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    copyLocalAudio(input, output, if (image) 20L * 1024 * 1024 else 200L * 1024 * 1024) {
                        currentCoroutineContext().ensureActive()
                        if (directory.usableSpace < 16L * 1024 * 1024) throw IOException("Not enough free storage")
                    }
                    output.fd.sync()
                }
            } ?: throw IOException("The selected file cannot be opened")
            if (image) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(target.absolutePath, bounds)
                require(bounds.outWidth in 1..20000 && bounds.outHeight in 1..20000) { "Invalid or oversized image" }
            } else {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(target.absolutePath)
                    require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") { "Choose a playable video" }
                    require((retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) > 0)
                } finally { retriever.release() }
            }
            SongEditCanvas(Uri.fromFile(target), image)
        } catch (failure: Throwable) {
            target.delete()
            throw failure
        }
    }

    fun discard(uri: Uri) = deleteIfUnused(uri.toString())

    private fun ownedUri(value: String): Uri? = runCatching {
        val uri = Uri.parse(value)
        if (uri.scheme != "file") return@runCatching null
        val file = File(requireNotNull(uri.path))
        if (file.canonicalFile.parentFile != directory.canonicalFile || !file.isFile) return@runCatching null
        uri
    }.getOrNull()

    private fun deleteIfUnused(value: String) {
        val uri = ownedUri(value) ?: return
        if (preferences.all.values.none { raw ->
            runCatching { JSONObject(raw as String).let { it.optString("cover") == value || it.optString("canvas") == value } }.getOrDefault(false)
        }) File(requireNotNull(uri.path)).delete()
    }

    companion object {
        @Volatile private var instance: SongEditStore? = null
        fun get(context: Context): SongEditStore = instance ?: synchronized(this) {
            instance ?: SongEditStore(context).also { instance = it }
        }
        fun apply(metadata: MediaMetadata): MediaMetadata = instance?.resolve(metadata) ?: metadata
    }
}

@Composable
fun rememberSongEditCanvas(songId: String?): SongEditCanvas? {
    val context = LocalContext.current
    val store = remember(context) { SongEditStore.get(context) }
    val revision by store.revision.collectAsState()
    return remember(store, songId, revision) { songId?.let(store::canvas) }
}

internal fun songEditArtistNames(text: String): List<String> = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
