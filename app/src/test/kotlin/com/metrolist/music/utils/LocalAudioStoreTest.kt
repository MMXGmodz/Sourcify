package com.metrolist.music.utils

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class LocalAudioStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val id = "local:9fd1d6e2-dd38-4919-8c1b-ac9474d89dca"

    @Test fun localIdsCannotEscapeTheirOwnedDirectory() {
        assertTrue(LocalAudioStore.isLocalId(id))
        listOf("local:../../file", "file:///a.flac", "local:1-1-1-1-1", "local:", "remote").forEach {
            assertFalse(LocalAudioStore.isLocalId(it))
        }
    }

    @Test fun copiesPreserveOriginalBytesAndBoundTheOutput() = runBlocking {
        val bytes = ByteArray(24000) { (it % 127).toByte() }
        val output = ByteArrayOutputStream()
        assertEquals(bytes.size.toLong(), copyLocalAudio(ByteArrayInputStream(bytes), output, bytes.size.toLong()))
        assertArrayEquals(bytes, output.toByteArray())
        try {
            copyLocalAudio(ByteArrayInputStream(bytes), ByteArrayOutputStream(), 100)
            fail("Oversized input must be rejected")
        } catch (_: IOException) { }
        try {
            copyLocalAudio(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream(), 100)
            fail("Empty files must be rejected")
        } catch (_: IOException) { }
    }

    @Test fun cancelledImportStopsCopying() = runBlocking {
        val output = ByteArrayOutputStream()
        try {
            copyLocalAudio(ByteArrayInputStream(ByteArray(200000)), output, 300000) {
                if (output.size() > 0) throw CancellationException("cancelled import")
            }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertTrue(output.size() < 200000)
    }

    @Test fun committedLocalCopySurvivesRestartRenameAndRemoval() = runBlocking {
        val directory = temporary.newFolder()
        val folder = File(directory, id.removePrefix("local:")).apply { mkdir() }
        val audio = File(folder, "audio").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val track = LocalAudioTrack(id, "Title", "Artist", "Album", 1000, "audio/flac", size = audio.length())
        File(folder, "metadata.json").writeText(track.toJson().toString())
        val store = LocalAudioStore(directory)
        store.refresh()
        assertEquals(track.title, store.tracks.value.single().title)
        store.rename(id, "New title")
        val restarted = LocalAudioStore(directory)
        restarted.refresh()
        assertEquals("New title", restarted.trackForId(id)?.title)
        assertArrayEquals(byteArrayOf(1, 2, 3), restarted.fileForId(id)?.readBytes())
        restarted.remove(id)
        assertNull(restarted.fileForId(id))
        assertTrue(restarted.tracks.value.isEmpty())
    }
}
