package com.metrolist.music.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.db.entities.SongEntity
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class LocalAudioRetentionTest {
    @Test fun accountCleanupRetainsLocalImportsAndDownloadsButRemovesRemoteOnlySongs() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = database.dao
            dao.insert(SongEntity("local:9fd1d6e2-dd38-4919-8c1b-ac9474d89dca", "FLAC", isLocal = true, liked = true))
            dao.insert(SongEntity("saved", "Saved song", isDownloaded = true))
            dao.insert(SongEntity("remote", "Streamed song"))
            dao.deleteSongsNotDownloaded()
            assertTrue(dao.songEntity("local:9fd1d6e2-dd38-4919-8c1b-ac9474d89dca")?.liked == true)
            assertNotNull(dao.songEntity("saved"))
            assertNull(dao.songEntity("remote"))
        } finally { database.close() }
    }
}
