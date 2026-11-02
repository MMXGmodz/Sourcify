package com.metrolist.music.utils

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.metrolist.music.R
import com.metrolist.music.UpdateActivity

object UpdateNotifications {
    private const val CHANNEL_ID = "updates"
    private const val AVAILABLE_NOTIFICATION_ID = 1001
    internal const val DOWNLOAD_NOTIFICATION_ID = 1002

    fun showAvailable(context: Context, release: ReleaseInfo) {
        if (Updater.getDownloadUrlForCurrentVariant(release) == null) return
        val preferences = context.getSharedPreferences("sourcify_update_notifications", Context.MODE_PRIVATE)
        if (preferences.getString("notified_tag", null) == release.tagName) return
        if (post(context, AVAILABLE_NOTIFICATION_ID, context.getString(R.string.update_available_title),
                context.getString(R.string.sourcify_update_available_message, release.versionName))) {
            preferences.edit().putString("notified_tag", release.tagName).apply()
        }
    }

    fun showDownloadResult(context: Context, id: Long) {
        val snapshot = runCatching { UpdateDownload.query(context, id) }.getOrNull() ?: return
        when (snapshot.status) {
            DownloadManager.STATUS_SUCCESSFUL -> post(context, DOWNLOAD_NOTIFICATION_ID,
                context.getString(R.string.s1380_update_download_complete),
                context.getString(R.string.s1380_update_tap_install, snapshot.download.version))
            DownloadManager.STATUS_FAILED -> post(context, DOWNLOAD_NOTIFICATION_ID,
                context.getString(R.string.s1380_update_failed_title),
                context.getString(R.string.s1380_update_tap_retry))
        }
    }

    fun dismissDownload(context: Context) {
        NotificationManagerCompat.from(context).cancel(DOWNLOAD_NOTIFICATION_ID)
    }

    private fun post(context: Context, id: Int, title: String, text: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID,
            context.getString(R.string.check_for_updates), NotificationManager.IMPORTANCE_DEFAULT))
        if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val pending = PendingIntent.getActivity(context, id, UpdateActivity.createIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        notifications.notify(id, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.update)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build())
        return true
    }
}
