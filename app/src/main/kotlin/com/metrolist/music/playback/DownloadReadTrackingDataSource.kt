package com.metrolist.music.playback

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.metrolist.music.utils.DownloadStorage

/** A download may move after its old cached spans have already been opened by a player. */
internal class DownloadReadTrackingDataSourceFactory(
    private val factory: DataSource.Factory,
    private val storage: DownloadStorage,
    private val afterClose: (String) -> Unit,
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        val delegate = factory.createDataSource()
        return object : DataSource by delegate {
            private var lease: String? = null
            override fun open(dataSpec: DataSpec): Long {
                check(lease == null)
                val id = dataSpec.key ?: dataSpec.uri.toString()
                storage.acquireRead(id)
                lease = id
                return try { delegate.open(dataSpec) } catch (error: Throwable) {
                    try { close() } catch (_: Exception) { }
                    throw error
                }
            }
            override fun close() {
                try { delegate.close() } finally {
                    lease?.let { id ->
                        lease = null
                        storage.releaseRead(id)
                        try { afterClose(id) } catch (_: Exception) { /* Cache cleanup must not break a successful read. */ }
                    }
                }
            }
        }
    }
}
