package com.metrolist.music.utils

import com.metrolist.innertube.models.MediaInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class DownloadCompanionStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun songInfoAndCreditsRemainReadableAfterRestartAndOwnerRemovalCleansThem() {
        val directory = temporary.newFolder()
        val store = DownloadCompanionStore(directory)
        store.retain("saved")
        val info = MediaInfo("saved", "Song", "Artist", description = "Composer: Person\n℗ Label", viewCount = 25)
        store.saveInfo("saved", info)
        val credit = com.metrolist.music.ui.player.MusicBrainzCreditsResult.Found("12345678-1234-1234-1234-123456789abc",
            com.metrolist.music.ui.player.SongCredits(listOf(com.metrolist.music.ui.player.SongCredit("Person", "Komponist*in",
                com.metrolist.music.ui.player.CreditGroup.COMPOSITION)), emptyList()))
        store.saveAdditionalCredits("saved", com.metrolist.music.ui.player.savedMusicBrainzCredits(credit))
        val restarted = DownloadCompanionStore(directory)
        assertEquals(info, restarted.info("saved"))
        assertEquals(credit, com.metrolist.music.ui.player.readSavedMusicBrainzCredits(requireNotNull(restarted.additionalCredits("saved"))))
        restarted.remove("saved")
        assertNull(restarted.info("saved"))
        assertNull(restarted.additionalCredits("saved"))
    }

    @Test fun manualCanvasIdentityDoesNotPickAnOldOrUnrelatedOfflineClip() {
        val store = DownloadCompanionStore(temporary.newFolder())
        store.retain("saved")
        val selected = CanvasSelection(videoId = "abcdefghijk", startMs = 1000, endMs = 9000)
        val file = temporary.newFile().apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        store.saveCanvas("saved", selected, file, 500, 8500)
        assertEquals(500L, store.playbackSelection("saved", selected)?.startMs)
        val changed = selected.copy(videoId = "zyxwvutsrqp")
        assertEquals(changed, store.playbackSelection("saved", changed))
        assertNull(store.playbackSelection("saved", null))
        store.remove("saved")
        assertEquals(selected, store.playbackSelection("saved", selected))
    }

    @Test fun removingDownloadDuringPreparationCannotResurrectItsData() {
        val store = DownloadCompanionStore(temporary.newFolder())
        store.retain("removed")
        store.remove("removed")
        store.saveInfo("removed", MediaInfo("removed", "Late result"))
        assertNull(store.info("removed"))
    }

    @Test fun disablingAutomaticCanvasKeepsManualChoicesButHidesSavedAutomaticClips() {
        val store = DownloadCompanionStore(temporary.newFolder())
        store.retain("automatic")
        val clip = temporary.newFile().apply { writeBytes(byteArrayOf(1, 2, 3)) }
        store.saveCanvas("automatic", null, clip, 0, 1000)
        assertNotNull(store.playbackSelection("automatic", null))
        assertNull(store.playbackSelection("automatic", null, automaticEnabled = false))
        val manual = CanvasSelection(videoId = "abcdefghijk", startMs = 0, endMs = 1000)
        assertEquals(manual, store.playbackSelection("automatic", manual, automaticEnabled = false))
    }

    @Test fun jsonRestoresAnInterruptedAtomicWriteAndRejectsOversizedData() {
        val file = temporary.newFile()
        file.writeText("incomplete")
        java.io.File("${file.path}.bak").writeText("{\"title\":\"committed\"}")
        assertEquals("committed", readDownloadJson(file)?.optString("title"))
        assertNull(readDownloadJson(file, 4))
    }

    @Test fun optionalFailureAllowsOtherExtrasButParentCancellationStopsWork() = runBlocking {
        val visited = mutableListOf<String>()
        runOptionalDownloadTasks(
            { throw IOException("No lyrics available") },
            { visited += "info" },
        )
        assertEquals(listOf("info"), visited)
        try {
            runOptionalDownloadTasks({ throw CancellationException() }, { visited += "must not run" })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(listOf("info"), visited)
    }
}
