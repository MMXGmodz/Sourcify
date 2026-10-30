package com.metrolist.music.playback

import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.*
import java.io.IOException

/** Original files in the chosen folder bypass the network resolver, including for seeks. */
internal class SavedDownloadDataSourceFactory(
    private val streamFactory: DataSource.Factory,
    private val documentForId: (String) -> Uri?,
    private val documentFactory: DataSource.Factory,
    private val completeFallbackAvailable: (String) -> Boolean,
    private val beforeDocumentOpen: (String) -> Unit = {},
    private val unavailableMessage: String = "Saved audio file is unavailable",
) : DataSource.Factory {
    override fun createDataSource(): DataSource = object : DataSource {
        private val listeners = mutableListOf<TransferListener>()
        private var source: DataSource? = null
        override fun addTransferListener(listener: TransferListener) { listeners += listener; source?.addTransferListener(listener) }
        override fun open(dataSpec: DataSpec): Long {
            check(source == null)
            val id = dataSpec.key ?: dataSpec.uri.toString()
            val document = documentForId(id)
            if (document != null) {
                try {
                    beforeDocumentOpen(id)
                    return openWith(documentFactory, dataSpec.withUri(document))
                } catch (error: Exception) {
                    if (!completeFallbackAvailable(id)) throw DataSourceException(
                        IOException(unavailableMessage, error),
                        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
                }
            }
            return openWith(streamFactory, dataSpec)
        }
        private fun openWith(factory: DataSource.Factory, spec: DataSpec): Long {
            val delegate = factory.createDataSource()
            listeners.forEach(delegate::addTransferListener)
            source = delegate
            return try { delegate.open(spec) } catch (error: Throwable) {
                try { delegate.close() } finally { source = null }
                throw error
            }
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = checkNotNull(source).read(buffer, offset, length)
        override fun getUri(): Uri? = source?.uri
        override fun getResponseHeaders(): Map<String, List<String>> = source?.responseHeaders ?: emptyMap()
        override fun close() { try { source?.close() } finally { source = null } }
    }
}
