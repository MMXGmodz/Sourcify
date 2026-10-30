package com.metrolist.music.playback

import android.net.Uri
import androidx.media3.datasource.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SavedDownloadDataSourceTest {
    @Test fun savedFolderAudioAndSeekingBypassTheInternetResolver() {
        val bytes = ByteArray(1200) { (it % 123).toByte() }
        val factory = SavedDownloadDataSourceFactory(
            { error("Saved audio used the Internet resolver") },
            { Uri.parse("content://test.documents/music.m4a") },
            { ByteArrayDataSource(bytes) }, { false },
        )
        val spec = DataSpec.Builder().setUri("saved-song").setKey("saved-song").setPosition(100).setLength(37).build()
        DataSourceInputStream(factory.createDataSource(), spec).use {
            assertArrayEquals(bytes.copyOfRange(100, 137), it.readBytes())
        }
    }

    @Test fun revokedFolderFallsBackOnlyWhenACompleteInternalCopyExists() {
        var fallbackOpens = 0
        val factory = SavedDownloadDataSourceFactory(
            { fallbackOpens++; ByteArrayDataSource(byteArrayOf(5, 6, 7)) },
            { Uri.parse("content://test.documents/revoked.m4a") },
            { object : DataSource by PlaceholderDataSource.FACTORY.createDataSource() {
                override fun open(dataSpec: DataSpec): Long = throw IOException("Permission revoked")
            } }, { true },
        )
        DataSourceInputStream(factory.createDataSource(), DataSpec.Builder().setUri("saved").setKey("saved").build()).use {
            assertArrayEquals(byteArrayOf(5, 6, 7), it.readBytes())
        }
        assertEquals(1, fallbackOpens)
    }
}
