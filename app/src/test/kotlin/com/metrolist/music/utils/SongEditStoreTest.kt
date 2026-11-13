package com.metrolist.music.utils

import android.content.Context
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.Artist
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SongEditStoreTest {
    private lateinit var context: Context
    private lateinit var store: SongEditStore
    private val metadata = MediaMetadata("song", "Original", listOf(
        MediaMetadata.Artist("provider-first", "First"),
        MediaMetadata.Artist("provider-second", "Second"),
    ), 120, thumbnailUrl = "https://example.com/original.jpg")

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("sourcify_song_edits", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "song_edits").deleteRecursively()
        store = SongEditStore(context)
    }

    @Test fun songColorPersistsIndependentlyAndCanReturnToAutomaticWithoutLosingOtherEdits() {
        val chosen = 0xFF8E44AD.toInt()
        store.save(metadata, "Edited", listOf("Artist"), null, null, accentColor = chosen)
        val reopened = SongEditStore(context)
        assertEquals(chosen, reopened.color(metadata.id))
        assertNull(reopened.color("other"))
        store.save(metadata, "Renamed", listOf("Artist"), null, null)
        assertEquals("Other edits must preserve the selected color", chosen, reopened.color(metadata.id))
        store.save(metadata, "Renamed", listOf("Artist"), null, null, accentColor = null)
        assertNull(reopened.color(metadata.id))
        assertEquals("Renamed", reopened.resolve(metadata).title)
    }

    @Test fun failedEditRestoresTheSavedColorAndResetAllClearsIt() = runBlocking {
        val database = MusicDatabase(Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).allowMainThreadQueries().build())
        try {
            store.updateSong(database, metadata, "Saved", listOf("Artist"), null, null, accentColor = 0xFF4169E1.toInt())
            assertEquals(0xFF4169E1.toInt(), store.color(metadata.id))
            try {
                store.updateSong(database, metadata, "", emptyList(), null, null, accentColor = 0xFFFF0000.toInt())
                fail("Invalid edit must fail")
            } catch (_: IllegalArgumentException) { }
            assertEquals(0xFF4169E1.toInt(), store.color(metadata.id))
            store.updateSong(database, metadata, "", emptyList(), null, null, reset = true)
            assertNull(store.color(metadata.id))
        } finally { database.close() }
    }

    @Test fun translucentAccentIsRejectedBeforeOverwritingThePreviousEdit() {
        store.save(metadata, "Saved", emptyList(), null, null, accentColor = 0xFF0000FF.toInt())
        assertThrows(IllegalArgumentException::class.java) {
            store.save(metadata, "Invalid", emptyList(), null, null, accentColor = 0x440000FF)
        }
        assertEquals("Saved", store.resolve(metadata).title)
        assertEquals(0xFF0000FF.toInt(), store.color(metadata.id))
    }

    @Test fun persistentPerSongNamesKeepProviderIdsAndDoNotChangeOtherSongs() {
        store.save(metadata, " Edited ", listOf("Custom first", "Custom second", "Guest"), null, null)
        val reopened = SongEditStore(context)
        val edited = reopened.resolve(metadata)
        assertEquals("Edited", edited.title)
        assertEquals(listOf("provider-first", "provider-second", null), edited.artists.map { it.id })
        assertEquals(listOf("Custom first", "Custom second", "Guest"), edited.artists.map { it.name })
        assertEquals(metadata.copy(id = "another-song"), reopened.resolve(metadata.copy(id = "another-song")))
        assertEquals(edited, reopened.resolve(edited))
    }

    @Test fun landscapeBackgroundPersistsSeparatelySurvivesOtherEditsAndCleansUnusedMedia() {
        val directory = File(context.filesDir, "song_edits").apply { mkdirs() }
        val file = File(directory, "landscape").apply { writeBytes(byteArrayOf(1)) }
        val background = SongEditCanvas(Uri.fromFile(file), true)
        store.save(metadata, metadata.title, metadata.artists.map { it.name }, null, null, landscapeBackground = background)
        val reopened = SongEditStore(context)
        assertEquals(background, reopened.landscapeBackground(metadata.id))
        assertNull(reopened.canvas(metadata.id))
        assertNull(reopened.cover(metadata.id))
        assertEquals(metadata, reopened.resolve(metadata))
        store.save(metadata, "Edited", listOf("Artist"), null, null)
        assertEquals(background, reopened.landscapeBackground(metadata.id))
        assertTrue(file.exists())
        store.save(metadata.copy(id = "other"), "Other", emptyList(), null, background)
        store.save(metadata, "Edited", listOf("Artist"), null, null, landscapeBackground = null)
        assertNull(reopened.landscapeBackground(metadata.id))
        assertTrue("Media referenced by another song must survive", file.exists())
        store.clear("other")
        assertFalse(file.exists())
    }

    @Test fun resetRestoresOriginalAfterRepeatedEditsAndDeletesOnlyUnusedOwnedFiles() {
        val directory = File(context.filesDir, "song_edits").apply { mkdirs() }
        val cover = File(directory, "cover").apply { writeBytes(byteArrayOf(1)) }
        val video = File(directory, "video").apply { writeBytes(byteArrayOf(2)) }
        val canvas = SongEditCanvas(Uri.fromFile(video), false)
        store.save(metadata, "First edit", listOf("Artist"), Uri.fromFile(cover), canvas)
        store.save(store.resolve(metadata), "Second edit", listOf("Artist"), Uri.fromFile(cover), canvas)
        assertEquals(metadata, store.original(store.resolve(metadata)))
        assertEquals(canvas, SongEditStore(context).canvas(metadata.id))
        store.save(metadata.copy(id = "other"), "Other", emptyList(), Uri.fromFile(cover), null)
        store.clear(metadata.id)
        assertTrue(cover.exists())
        assertFalse(video.exists())
        assertEquals(metadata, store.resolve(metadata))
        store.clear("other")
        assertFalse(cover.exists())
    }

    @Test fun rejectsUnownedCoverAndNeverDeletesTheOriginal() {
        val original = File(context.filesDir, "user-original.jpg").apply { writeBytes(byteArrayOf(1)) }
        try {
            store.save(metadata, "Edited", emptyList(), Uri.fromFile(original), null)
            fail("Files outside song_edits must be rejected")
        } catch (_: IllegalArgumentException) { }
        store.discard(Uri.fromFile(original))
        store.clear(metadata.id)
        assertTrue(original.exists())
        assertFalse(store.hasEdits(metadata.id))
    }

    @Test fun pickerImageIsCopiedAndSurvivesLossOfTheProviderFile() = runBlocking {
        val source = File(context.cacheDir, "picker-original.png").apply {
            val bitmap = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
            outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        val bytes = source.readBytes()
        val authority = "song.edit.test.picker"
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "image/png"
            override fun openAssetFile(uri: Uri, mode: String) = AssetFileDescriptor(
                ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY), 0, source.length())
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        }
        provider.attachInfo(context, ProviderInfo().apply { this.authority = authority })
        ShadowContentResolver.registerProviderInternal(authority, provider)
        val imported = store.importMedia(Uri.parse("content://$authority/image"), imageOnly = true)
        assertEquals("file", imported.uri.scheme)
        assertTrue(imported.isImage)
        assertTrue(source.exists())
        assertArrayEquals(bytes, File(requireNotNull(imported.uri.path)).readBytes())
        store.save(metadata, "Edited", emptyList(), imported.uri, null)
        source.delete()
        assertEquals(imported.uri, SongEditStore(context).cover(metadata.id))
        assertArrayEquals(bytes, File(requireNotNull(imported.uri.path)).readBytes())
    }

    @Test fun blankTitleAndOversizedArtistNamesAreRejected() {
        listOf("", "   ", "a".repeat(301)).forEach { title ->
            try {
                store.save(metadata, title, emptyList(), null, null)
                fail("Invalid titles must be rejected")
            } catch (_: IllegalArgumentException) { }
        }
        assertEquals(listOf("One", "AC, DC", "Three"), songEditArtistNames(" One \n\n AC, DC\r\n Three "))
        try {
            store.save(metadata, "Title", listOf("a".repeat(301)), null, null)
            fail("Invalid artist names must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun providerRefetchAndResetPreserveOriginalArtistRelations() = runBlocking {
        val database = MusicDatabase(Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).allowMainThreadQueries().build())
        try {
            database.insert(metadata)
            val originalRelations = database.songArtistMap(metadata.id)
            val edits = SongEditStore.get(context)
            edits.updateSong(database, metadata, "Custom", listOf("Only one artist"), null, null)
            val provider = SongItem(metadata.id, metadata.title, metadata.artists.map { Artist(it.name, it.id) },
                duration = metadata.duration, thumbnail = requireNotNull(metadata.thumbnailUrl))
            assertEquals(metadata.artists, provider.toMediaMetadata().artists)
            assertEquals(metadata.title, provider.toMediaMetadata().title)
            database.withTransaction {
                update(requireNotNull(song(metadata.id).first()), provider.toMediaMetadata())
            }
            assertEquals(originalRelations, database.songArtistMap(metadata.id))
            assertEquals(listOf("Only one artist"), edits.resolve(metadata).artists.map { it.name })
            edits.updateSong(database, edits.resolve(metadata), "", emptyList(), null, null, reset = true)
            assertEquals(originalRelations, database.songArtistMap(metadata.id))
            assertEquals(metadata.artists, edits.resolve(metadata).artists)
        } finally { database.close() }
    }

    @Test fun failedEditRollsBackDatabaseAndRetainsPreviousMetadataAndFiles() = runBlocking {
        val database = MusicDatabase(Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).allowMainThreadQueries().build())
        val directory = File(context.filesDir, "song_edits").apply { mkdirs() }
        val cover = File(directory, "retained-cover").apply { writeBytes(byteArrayOf(1)) }
        try {
            store.updateSong(database, metadata, "Saved", listOf("Custom"), Uri.fromFile(cover), null)
            try {
                store.updateSong(database, metadata, "", emptyList(), null, null)
                fail("An invalid edit must fail after rolling back the transaction")
            } catch (_: IllegalArgumentException) { }
            assertEquals("Saved", database.song(metadata.id).first()?.song?.title)
            assertEquals("Saved", store.resolve(metadata).title)
            assertEquals(Uri.fromFile(cover), store.cover(metadata.id))
            assertTrue(cover.exists())
        } finally { database.close() }
    }
}
