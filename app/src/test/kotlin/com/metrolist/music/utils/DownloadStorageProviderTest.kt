package com.metrolist.music.utils

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.media3.datasource.*
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.playback.DownloadReadTrackingDataSourceFactory
import com.metrolist.music.playback.SavedDownloadDataSourceFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DownloadStorageProviderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val authority = "sourcify.test.documents"

    private fun setup(): Pair<DownloadStorage, LocalDocuments> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context as Application).grantPermissions(Manifest.permission.MANAGE_DOCUMENTS)
        val provider = LocalDocuments(temporary.newFolder())
        provider.attachInfo(context, ProviderInfo().apply {
            authority = this@DownloadStorageProviderTest.authority
            exported = true; grantUriPermissions = true
            readPermission = Manifest.permission.MANAGE_DOCUMENTS
            writePermission = Manifest.permission.MANAGE_DOCUMENTS
        })
        ShadowContentResolver.registerProviderInternal(authority, provider)
        val store = DownloadStorage(context, temporary.newFolder())
        store.setFolder(DocumentsContract.buildTreeDocumentUri(authority, "root"))
        return store to provider
    }

    @Test fun exportedOriginalIsVerifiedRegisteredAndSeekableWithoutNetwork() = runBlocking {
        val (store, provider) = setup()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = ByteArray(180000) { (it % 121).toByte() }
        store.plan("saved")
        assertTrue(store.export("saved", "Title", "audio/mp4", ByteArrayInputStream(original), original.size.toLong()))
        val record = requireNotNull(store.record("saved"))
        assertTrue(store.isReadable("saved"))
        assertNull(store.targetFolder("saved"))
        val player = SavedDownloadDataSourceFactory({ error("Saved file requested the network") },
            { store.record(it)?.uri }, DefaultDataSource.Factory(context), { false })
        DataSourceInputStream(player.createDataSource(), DataSpec.Builder().setUri("saved").setKey("saved").setPosition(12345).setLength(567).build()).use {
            assertArrayEquals(original.copyOfRange(12345, 12912), it.readBytes())
        }
        store.remove("saved")
        assertNull(store.record("saved"))
        assertEquals(setOf("user-original"), provider.files.keys)
        assertTrue(provider.files.getValue("user-original").exists())
    }

    @Test fun failedAndRemovedExportsDoNotRegisterIncompleteFilesOrDeleteOriginals() = runBlocking {
        val (store, provider) = setup()
        store.plan("broken")
        try {
            store.export("broken", "Broken", "audio/mp4", ByteArrayInputStream(byteArrayOf(1, 2)), 10)
            fail("Incomplete copy must fail")
        } catch (_: IOException) { }
        assertNull(store.record("broken"))
        store.plan("removed")
        try {
            store.export("removed", "Removed", "audio/mp4", ByteArrayInputStream(byteArrayOf(1, 2)), 2) { false }
            fail("Removed download must not be committed")
        } catch (_: IOException) { }
        assertNull(store.record("removed"))
        assertEquals(setOf("user-original"), provider.files.keys)
    }

    @Test fun movingAudioDoesNotEvictSpansThatAnExistingPlayerStillReads() {
        val (store, _) = setup()
        val original = byteArrayOf(10, 20, 30)
        var released = false
        val factory = DownloadReadTrackingDataSourceFactory({ ByteArrayDataSource(original) }, store) { released = !store.hasActiveReads(it) }
        val source = factory.createDataSource()
        DataSourceInputStream(source, DataSpec.Builder().setUri("playing").setKey("playing").build()).use { input ->
            input.open()
            assertTrue(store.hasActiveReads("playing"))
            store.register("playing", android.net.Uri.parse("content://$authority/document/created"), requireNotNull(store.folderUri), original.size.toLong())
            assertTrue(store.hasActiveReads("playing"))
            assertArrayEquals(original, input.readBytes())
        }
        assertFalse(store.hasActiveReads("playing"))
        assertTrue(released)
    }

    @Test fun anInterruptedExportIsRecoveredOnRetryAndRemovalWithoutTouchingUserFiles() = runBlocking {
        val (store, provider) = setup()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val folder = requireNotNull(store.folderUri)
        val parent = DocumentsContract.buildDocumentUriUsingTree(folder, "root")
        store.plan("interrupted")
        val partial = requireNotNull(DocumentsContract.createDocument(context.contentResolver, parent, "audio/mp4", "partial.m4a"))
        context.contentResolver.openOutputStream(partial)!!.use { it.write(byteArrayOf(1)) }
        val pending = temporary.root.walkTopDown().first { it.extension == "pending" }
        writeDownloadJson(pending, JSONObject().put("folder", folder.toString()).put("document", partial.toString()))
        val restarted = DownloadStorage(context, pending.parentFile!!)
        assertTrue(restarted.export("interrupted", "Complete", "audio/mp4", ByteArrayInputStream(byteArrayOf(3, 4)), 2))
        assertFalse("Partial copy must be removed on retry", provider.files.containsKey(DocumentsContract.getDocumentId(partial)))
        restarted.remove("interrupted")

        restarted.plan("remove-pending")
        val orphan = requireNotNull(DocumentsContract.createDocument(context.contentResolver, parent, "audio/mp4", "unfinished.m4a"))
        val nextPending = temporary.root.walkTopDown().first { it.extension == "pending" }
        writeDownloadJson(nextPending, JSONObject().put("folder", folder.toString()).put("document", orphan.toString()))
        DownloadStorage(context, nextPending.parentFile!!).remove("remove-pending")
        assertEquals(setOf("user-original"), provider.files.keys)
    }

    @Test fun createdDocumentIsJournaledBeforeAnyAudioBytesAreWritten() = runBlocking {
        val (store, _) = setup()
        store.plan("journal")
        val input = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val pending = temporary.root.walkTopDown().first { it.extension == "pending" }
                assertTrue("Created URI must survive a process restart", !readDownloadJson(pending)!!.optString("document").isNullOrBlank())
                return super.read(buffer, offset, length)
            }
        }
        assertTrue(store.export("journal", "Journal", "audio/mp4", input, 3))
    }

    class LocalDocuments(private val directory: File) : DocumentsProvider() {
        val files = linkedMapOf("user-original" to File(directory, "User-original.m4a").apply { writeText("keep") })
        private var sequence = 0
        override fun onCreate() = true
        override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_DOCUMENT_ID)).apply { addRow(arrayOf("root", "root")) }
        override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor = MatrixCursor(arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.COLUMN_SIZE)).apply {
            val root = documentId == "root"
            val file = if (root) directory else files[documentId] ?: throw FileNotFoundException(documentId)
            addRow(arrayOf<Any>(documentId, file.name, if (root) DocumentsContract.Document.MIME_TYPE_DIR else "audio/mp4",
                if (root) DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE else DocumentsContract.Document.FLAG_SUPPORTS_WRITE or DocumentsContract.Document.FLAG_SUPPORTS_DELETE,
                file.length()))
        }
        override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor = MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)).apply { files.keys.forEach { addRow(arrayOf(it)) } }
        override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean = parentDocumentId == "root" && documentId in files
        override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
            val id = "created-${++sequence}"
            files[id] = File(directory, displayName).apply { createNewFile() }
            return id
        }
        override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
            ParcelFileDescriptor.open(files[documentId] ?: throw FileNotFoundException(documentId), ParcelFileDescriptor.parseMode(mode))
        override fun deleteDocument(documentId: String) { files.remove(documentId)?.delete() }
    }
}
