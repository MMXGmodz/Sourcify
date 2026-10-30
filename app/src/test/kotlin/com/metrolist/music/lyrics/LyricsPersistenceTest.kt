package com.metrolist.music.lyrics

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.db.entities.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LyricsPersistenceTest {
    @Test fun lateAutomaticResultsCannotReplaceUserEditsOrSavedSuccessfulLyrics() = runBlocking {
        val room = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), InternalDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val database = MusicDatabase(room)
            database.insert(SongEntity("song", "Song"))
            database.saveFetchedLyricsIfMissing(LyricsEntity("song", LyricsEntity.LYRICS_NOT_FOUND, "source"))
            database.saveFetchedLyricsIfMissing(LyricsEntity("song", "Saved offline", "source"))
            database.saveFetchedLyricsIfMissing(LyricsEntity("song", LyricsEntity.LYRICS_NOT_FOUND, "late failure"))
            assertEquals("Saved offline", database.lyrics("song").first()?.lyrics)
            database.upsert(LyricsEntity("song", "My edit", "manual"))
            database.saveFetchedLyricsIfMissing(LyricsEntity("song", "Late successful fetch", "source"))
            assertEquals("My edit", database.lyrics("song").first()?.lyrics)
        } finally { room.close() }
    }
}
