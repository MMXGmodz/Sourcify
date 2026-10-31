package com.metrolist.music.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/** Original bytes are kept outside the evictable playback cache. No transcoding is performed. */
class LocalAudioStore internal constructor(private val directory: File) {
    private val mutex = Mutex()
    private val mutableTracks = MutableStateFlow<List<LocalAudioTrack>>(emptyList())
    val tracks: StateFlow<List<LocalAudioTrack>> = mutableTracks.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { refresh() }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        mutex.withLock { refreshLocked() }
    }

    fun fileForId(id: String): File? = uuidForId(id)?.let { uuid ->
        File(directory, "$uuid/audio").takeIf { it.isFile && it.length() in 1..MAX_AUDIO_BYTES }
    }

    fun trackForId(id: String): LocalAudioTrack? =
        tracks.value.firstOrNull { it.id == id } ?: uuidForId(id)?.let { readTrack(File(directory, it)) }

    suspend fun importAudio(context: Context, uri: Uri, sourceTitle: String? = null, sourceArtist: String? = null): LocalAudioTrack = withContext(Dispatchers.IO) {
        require(uri.scheme == "content" || uri.scheme == "file") { "Select a local audio file" }
        mutex.withLock {
            check(directory.mkdirs() || directory.isDirectory) { "Audio storage is unavailable" }
            val uuid = UUID.randomUUID().toString()
            val staging = File(directory, ".$uuid.tmp")
            val target = File(directory, uuid)
            check(staging.mkdir()) { "Audio storage is unavailable" }
            try {
                val displayName = runCatching {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    }
                }.getOrNull()?.take(500) ?: uri.lastPathSegment.orEmpty().take(500)
                val audio = File(staging, "audio")
                context.contentResolver.openInputStream(uri)?.use { source ->
                    FileOutputStream(audio).use { destination ->
                        copyLocalAudio(source, destination, MAX_AUDIO_BYTES) {
                            currentCoroutineContext().ensureActive()
                            if (staging.usableSpace < MIN_FREE_BYTES) throw IOException("Not enough free storage")
                        }
                        destination.fd.sync()
                    }
                } ?: throw IOException("The selected file cannot be opened")
                currentCoroutineContext().ensureActive()
                val track = inspectAudio(audio, displayName, "local:$uuid").let { original ->
                    original.copy(title = sourceTitle?.take(300)?.takeIf(String::isNotBlank) ?: original.title,
                        artist = sourceArtist?.take(300)?.takeIf(String::isNotBlank) ?: original.artist)
                }
                saveArtwork(audio, File(staging, "cover.jpg"))
                FileOutputStream(File(staging, "metadata.json")).use {
                    it.write(track.toJson().toString().toByteArray(Charsets.UTF_8))
                    it.fd.sync()
                }
                currentCoroutineContext().ensureActive()
                if (!staging.renameTo(target)) throw IOException("Could not save the audio file")
                refreshLocked()
                trackForId(track.id) ?: throw IOException("Could not read the imported audio")
            } catch (failure: Throwable) {
                staging.deleteRecursively()
                throw failure
            }
        }
    }

    suspend fun rename(id: String, title: String) = withContext(Dispatchers.IO) {
        require(title.isNotBlank() && title.length <= 300)
        mutex.withLock {
            val uuid = requireNotNull(uuidForId(id))
            val track = requireNotNull(trackForId(id))
            val file = AtomicFile(File(directory, "$uuid/metadata.json"))
            val stream = file.startWrite()
            try {
                stream.write(track.copy(title = title.trim()).toJson().toString().toByteArray(Charsets.UTF_8))
                file.finishWrite(stream)
            } catch (error: Throwable) { file.failWrite(stream); throw error }
            refreshLocked()
        }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val uuid = uuidForId(id) ?: return@withLock
            val source = File(directory, uuid)
            if (source.exists() && !source.deleteRecursively()) throw IOException("Could not remove the local copy")
            refreshLocked()
        }
    }

    private fun refreshLocked() {
        mutableTracks.value = directory.listFiles().orEmpty()
            .asSequence().filter { it.isDirectory && uuidForId("local:${it.name}") != null }
            .mapNotNull(::readTrack).sortedBy { it.title.lowercase(java.util.Locale.ROOT) }.toList()
    }

    private fun readTrack(folder: File): LocalAudioTrack? = runCatching {
        val metadata = File(folder, "metadata.json")
        require(metadata.length() in 1..16_384)
        val json = JSONObject(metadata.readText())
        val id = json.getString("id")
        require(uuidForId(id) == folder.name)
        val audio = fileForId(id) ?: return null
        LocalAudioTrack(
            id = id,
            title = json.getString("title").take(500),
            artist = json.optString("artist").take(500),
            albumTitle = json.optString("album").take(500),
            durationMs = json.getLong("durationMs").also { require(it > 0) },
            mimeType = json.getString("mimeType").also { require(it in SUPPORTED_MIMES) },
            sampleRate = json.optInt("sampleRate").takeIf { it > 0 },
            channelCount = json.optInt("channelCount").takeIf { it in 1..32 },
            bitDepth = json.optInt("bitDepth").takeIf { it in 1..64 },
            size = audio.length(),
            artworkUri = File(folder, "cover.jpg").takeIf { it.isFile && it.length() <= MAX_ARTWORK_BYTES }?.let { Uri.fromFile(it).toString() },
        )
    }.getOrNull()

    companion object {
        const val MAX_AUDIO_BYTES = 1_073_741_824L
        private const val MIN_FREE_BYTES = 16 * 1024 * 1024L
        private const val MAX_ARTWORK_BYTES = 8 * 1024 * 1024L
        private val SUPPORTED_MIMES = setOf("audio/flac", "audio/raw", "audio/mpeg", "audio/mp4a-latm", "audio/opus", "audio/vorbis")
        @Volatile private var instance: LocalAudioStore? = null

        fun get(context: Context): LocalAudioStore = instance ?: synchronized(this) {
            instance ?: LocalAudioStore(File(context.applicationContext.filesDir, "local_audio")).also { instance = it }
        }

        fun isLocalId(id: String): Boolean = uuidForId(id) != null

        internal fun uuidForId(id: String): String? {
            if (!id.startsWith("local:")) return null
            val value = id.removePrefix("local:")
            return runCatching { UUID.fromString(value).toString().takeIf { it == value } }.getOrNull()
        }

        private fun inspectAudio(file: File, displayName: String, id: String): LocalAudioTrack {
            val extractor = MediaExtractor()
            val format: MediaFormat
            try {
                extractor.setDataSource(file.absolutePath)
                val formats = (0 until extractor.trackCount).map(extractor::getTrackFormat)
                require(formats.none { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }) { "Choose an audio file, not a video" }
                format = formats.firstOrNull { it.getString(MediaFormat.KEY_MIME) in SUPPORTED_MIMES }
                    ?: throw IOException("Unsupported audio format. Choose FLAC, PCM WAV, MP3, AAC, Ogg or Opus")
                val mime = format.getString(MediaFormat.KEY_MIME)!!
                if (mime != "audio/raw" && MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format) == null) {
                    throw IOException("This device cannot decode the selected audio format")
                }
            } finally {
                extractor.release()
            }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    ?: format.longOrNull(MediaFormat.KEY_DURATION)?.div(1000)
                    ?: throw IOException("Audio duration could not be read")
                require(duration > 0) { "The audio file is empty or damaged" }
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.takeIf(String::isNotEmpty)
                    ?: displayName.substringBeforeLast('.', displayName).ifBlank { "Audio" }
                val mime = format.getString(MediaFormat.KEY_MIME)!!
                val pcmBits = when (format.intOrNull(MediaFormat.KEY_PCM_ENCODING)) {
                    AudioFormat.ENCODING_PCM_8BIT -> 8
                    AudioFormat.ENCODING_PCM_16BIT -> 16
                    AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                    AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
                    else -> null
                }
                return LocalAudioTrack(
                    id = id, title = title.take(500),
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty().take(500),
                    albumTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty().take(500),
                    durationMs = duration, mimeType = mime,
                    sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE),
                    channelCount = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT),
                    bitDepth = if (mime == "audio/raw") pcmBits else if (mime == "audio/flac") runCatching {
                        val header = ByteArray(42)
                        file.inputStream().use { require(it.read(header) == header.size) }
                        inspectFlacHeader(header, 0).bitDepth
                    }.getOrNull() else null,
                    size = file.length(),
                )
            } finally {
                retriever.release()
            }
        }

        private fun saveArtwork(audio: File, destination: File) {
            // Artwork is optional; a damaged or huge embedded image must not reject playable audio.
            runCatching {
                val retriever = MediaMetadataRetriever()
                val bytes = try {
                    retriever.setDataSource(audio.absolutePath)
                    retriever.embeddedPicture
                } finally { retriever.release() } ?: return
                if (bytes.size > MAX_ARTWORK_BYTES) return
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                if (options.outWidth <= 0 || options.outHeight <= 0) return
                options.inJustDecodeBounds = false
                while (options.outWidth / options.inSampleSize.coerceAtLeast(1) > 1024 || options.outHeight / options.inSampleSize.coerceAtLeast(1) > 1024) {
                    options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
                }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return
                try { FileOutputStream(destination).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) } }
                finally { bitmap.recycle() }
            }.onFailure { destination.delete() }
        }
    }
}

data class LocalAudioTrack(
    val id: String,
    val title: String,
    val artist: String,
    val albumTitle: String,
    val durationMs: Long,
    val mimeType: String,
    val sampleRate: Int? = null,
    val channelCount: Int? = null,
    val bitDepth: Int? = null,
    val size: Long,
    val artworkUri: String? = null,
) {
    val isLossless: Boolean get() = mimeType == "audio/flac" || mimeType == "audio/raw"
    val codec: String get() = when (mimeType) {
        "audio/flac" -> "FLAC"
        "audio/raw" -> "PCM WAV"
        "audio/mpeg" -> "MP3"
        "audio/mp4a-latm" -> "AAC"
        "audio/opus" -> "Opus"
        "audio/vorbis" -> "Vorbis"
        else -> mimeType
    }
    val mediaMetadata: MediaMetadata get() = MediaMetadata(
        id = id, title = title,
        artists = artist.takeIf(String::isNotBlank)?.let { listOf(MediaMetadata.Artist(null, it)) }.orEmpty(),
        duration = (durationMs / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        thumbnailUrl = artworkUri,
    )

    internal fun toJson() = JSONObject().apply {
        put("id", id); put("title", title); put("artist", artist); put("album", albumTitle)
        put("durationMs", durationMs); put("mimeType", mimeType)
        sampleRate?.let { put("sampleRate", it) }
        channelCount?.let { put("channelCount", it) }
        bitDepth?.let { put("bitDepth", it) }
    }
}

internal suspend fun copyLocalAudio(input: InputStream, output: java.io.OutputStream, maxBytes: Long, check: suspend () -> Unit = {}): Long {
    var total = 0L
    val buffer = ByteArray(64 * 1024)
    while (true) {
        check()
        val count = input.read(buffer)
        if (count == -1) break
        if (count == 0) continue
        if (total > maxBytes - count) throw IOException("Audio files larger than 1 GB are not supported")
        output.write(buffer, 0, count)
        total += count
    }
    if (total == 0L) throw IOException("The selected file is empty")
    return total
}

private fun MediaFormat.intOrNull(key: String): Int? = if (containsKey(key)) getInteger(key) else null
private fun MediaFormat.longOrNull(key: String): Long? = if (containsKey(key)) getLong(key) else null
