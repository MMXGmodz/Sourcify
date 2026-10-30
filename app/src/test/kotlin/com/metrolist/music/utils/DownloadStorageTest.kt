package com.metrolist.music.utils

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class DownloadStorageTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun locationChangePreservesRecordsForDownloadsInThePreviousFolder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = temporary.newFolder()
        val store = DownloadStorage(context, directory)
        val folder = Uri.parse("content://test.documents/tree/music")
        val audio = Uri.parse("content://test.documents/document/music%2Fsong.m4a")
        store.setFolder(folder)
        store.register("song", audio, folder, 100)
        store.setFolder(null)
        val restarted = DownloadStorage(context, directory)
        assertNull(restarted.folderUri)
        assertEquals(audio, restarted.record("song")?.uri)
        assertEquals(100L, restarted.storedBytes())
    }

    @Test fun originalCopyRejectsTruncationAndExtraBytesWithoutCallingCommit() = runBlocking {
        val original = ByteArray(19000) { (it % 97).toByte() }
        val copied = ByteArrayOutputStream()
        copyDownloadedOriginal(ByteArrayInputStream(original), copied, original.size.toLong())
        assertArrayEquals(original, copied.toByteArray())
        for (expected in listOf(100L, 20000L)) {
            try {
                copyDownloadedOriginal(ByteArrayInputStream(original), ByteArrayOutputStream(), expected)
                fail("An incomplete or incorrectly sized copy must not be committed")
            } catch (_: IOException) { }
        }
    }
}
