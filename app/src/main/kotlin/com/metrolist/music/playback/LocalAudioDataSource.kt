package com.metrolist.music.playback

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import java.io.File

internal fun isLocalAudioMediaId(mediaId: String?): Boolean = mediaId?.startsWith("local:") == true

/** Local originals bypass both the YouTube resolver and the streaming/download caches. */
internal class LocalAudioDataSourceFactory(
    private val streamFactory: DataSource.Factory,
    private val localFileForId: (String) -> File?,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = object : DataSource {
        private val listeners = mutableListOf<TransferListener>()
        private var source: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            listeners += transferListener
            source?.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            check(source == null) { "Data source is already open" }
            val mediaId = dataSpec.key ?: dataSpec.uri.toString()
            val resolvedSpec: DataSpec
            val delegate: DataSource
            if (isLocalAudioMediaId(mediaId)) {
                val file = localFileForId(mediaId)?.takeIf { it.isFile }
                    ?: throw DataSourceException(
                        "Local audio file is unavailable",
                        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                    )
                delegate = FileDataSource()
                resolvedSpec = dataSpec.withUri(file.toUri())
            } else {
                delegate = streamFactory.createDataSource()
                resolvedSpec = dataSpec
            }
            listeners.forEach(delegate::addTransferListener)
            source = delegate
            return try { delegate.open(resolvedSpec) } catch (error: Throwable) {
                try { delegate.close() } finally { source = null }
                throw error
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            checkNotNull(source) { "Data source is not open" }.read(buffer, offset, length)

        override fun getUri(): Uri? = source?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = source?.responseHeaders ?: emptyMap()

        override fun close() {
            try {
                source?.close()
            } finally {
                source = null
            }
        }
    }
}
