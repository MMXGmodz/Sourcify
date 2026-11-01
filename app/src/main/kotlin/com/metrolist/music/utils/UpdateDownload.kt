package com.metrolist.music.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.metrolist.music.R

object UpdateDownload {
    @Synchronized
    fun enqueue(context: Context, release: ReleaseInfo): Long {
        val asset = release.assets.firstOrNull { Updater.isSourcifyAsset(release.tagName, it) }
            ?: throw IllegalArgumentException("No compatible Sourcify APK")
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val preferences = context.getSharedPreferences("apk_updates", Context.MODE_PRIVATE)
        val previous = preferences.getLong("id", -1)
        if (previous >= 0 && preferences.getString("url", null) == asset.downloadUrl) {
            manager.query(DownloadManager.Query().setFilterById(previous))?.use { cursor ->
                if (cursor.moveToFirst()) {
                    when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> return previous
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val uri = manager.getUriForDownloadedFile(previous)
                            val readable = uri != null && runCatching {
                                context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
                            }.getOrDefault(false)
                            if (readable) return previous
                        }
                    }
                }
            }
        }
        val filename = "Sourcify-${release.versionName}-${System.currentTimeMillis()}.apk"
        val request = DownloadManager.Request(Uri.parse(asset.downloadUrl))
            .setTitle("Sourcify ${release.versionName}")
            .setDescription(context.getString(R.string.s10_update_downloading))
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        } else {
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename)
        }
        return manager.enqueue(request).also {
            preferences.edit().putLong("id", it).putString("url", asset.downloadUrl).apply()
        }
    }
}
