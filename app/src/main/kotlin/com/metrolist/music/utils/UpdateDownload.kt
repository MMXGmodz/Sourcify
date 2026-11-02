package com.metrolist.music.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import androidx.core.content.FileProvider
import com.metrolist.music.R
import org.json.JSONObject
import java.io.File

data class OwnedUpdateDownload(
    val id: Long,
    val tag: String,
    val version: String,
    val asset: ReleaseAsset,
)

data class UpdateDownloadStatus(
    val download: OwnedUpdateDownload,
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val reason: Int,
)

object UpdateDownload {
    private const val RECORD_FILE = "sourcify-update.json"
    private const val MAX_APK_BYTES = 512L * 1024 * 1024
    private val verificationLock = Any()

    private fun manager(context: Context) = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private fun record(context: Context) = AtomicFile(File(context.noBackupFilesDir, RECORD_FILE))

    private fun validRelease(tag: String, version: String, asset: ReleaseAsset): Boolean =
        Updater.isSourcifyAsset(tag, asset) && asset.size <= MAX_APK_BYTES &&
            tag.removePrefix("android-tv-v").removePrefix("v").substringBefore("-") == version

    @Synchronized
    fun ownedDownload(context: Context): OwnedUpdateDownload? = runCatching {
        val json = JSONObject(record(context).openRead().bufferedReader().use { it.readText() })
        val asset = ReleaseAsset(json.getString("name"), json.getString("url"), json.getLong("size"), "universal", "foss")
        val download = OwnedUpdateDownload(json.getLong("id"), json.getString("tag"), json.getString("version"), asset)
        download.takeIf { it.id >= 0 && validRelease(it.tag, it.version, it.asset) }
    }.getOrNull()

    @Synchronized
    fun forget(context: Context) {
        record(context).delete()
    }

    @Synchronized
    fun enqueue(context: Context, release: ReleaseInfo): Long {
        val asset = release.assets.firstOrNull { validRelease(release.tagName, release.versionName, it) }
            ?: throw IllegalArgumentException("No compatible Sourcify APK")
        val manager = manager(context)
        ownedDownload(context)?.takeIf { it.asset == asset && it.tag == release.tagName }?.let { previous ->
            val existing = runCatching { query(context, previous.id) }.getOrNull()
            if (existing != null && existing.status in setOf(DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_SUCCESSFUL)) return previous.id
        }
        val filename = "Sourcify-${release.versionName}-${System.currentTimeMillis()}.apk"
        val request = DownloadManager.Request(Uri.parse(asset.downloadUrl))
            .setTitle("Sourcify ${release.versionName}")
            .setDescription(context.getString(R.string.s1380_update_downloading))
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        } else {
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename)
        }
        val id = manager.enqueue(request)
        val json = JSONObject().put("id", id).put("tag", release.tagName).put("version", release.versionName)
            .put("name", asset.name).put("url", asset.downloadUrl).put("size", asset.size)
        val file = record(context)
        val output = file.startWrite()
        try {
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            manager.remove(id)
            throw error
        }
        return id
    }

    @Synchronized
    fun query(context: Context, id: Long): UpdateDownloadStatus {
        val owned = ownedDownload(context)
        require(owned != null && owned.id == id) { "This download does not belong to the updater" }
        manager(context).query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            require(cursor.moveToFirst()) { "The update download is no longer available" }
            require(cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)) == id &&
                cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)) == owned.asset.downloadUrl) {
                "The download does not match the selected Sourcify release"
            }
            return UpdateDownloadStatus(owned,
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)))
        }
        error("The update download is no longer available")
    }

    /** Copy out of shared Downloads before verification, so the installer reads the same private bytes. */
    fun verifiedInstallUri(context: Context, id: Long): Uri = synchronized(verificationLock) {
        val snapshot = query(context, id)
        require(snapshot.status == DownloadManager.STATUS_SUCCESSFUL) { "The APK download has not completed" }
        val directory = File(context.cacheDir, "apk-updates").apply { mkdirs() }
        val file = File(directory, "update-$id.apk")
        directory.listFiles()?.filter { it != file && it.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1000L }
            ?.forEach { it.delete() }
        try {
            if (file.isFile && file.length() == snapshot.download.asset.size) {
                UpdateInstaller.validateArchive(context, file, snapshot.download.version)
                return@synchronized FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
            }
            val descriptor = manager(context).openDownloadedFile(id)
                ?: error("The downloaded APK cannot be opened")
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytes = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        bytes += count
                        require(bytes <= snapshot.download.asset.size) { "The downloaded APK has an unexpected size" }
                        output.write(buffer, 0, count)
                    }
                    require(bytes == snapshot.download.asset.size) { "The downloaded APK is incomplete" }
                }
            }
            UpdateInstaller.validateArchive(context, file, snapshot.download.version)
            FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }
}
