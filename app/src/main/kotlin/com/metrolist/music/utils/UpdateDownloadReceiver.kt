package com.metrolist.music.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.metrolist.music.constants.UpdateNotificationsEnabledKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (id < 0 || UpdateDownload.ownedDownload(context)?.id != id) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (context.dataStore.data.first()[UpdateNotificationsEnabledKey] != false) {
                    UpdateNotifications.showDownloadResult(context, id)
                }
            } catch (_: Exception) {
                // The foreground update screen can recover even if notifications or storage are unavailable.
            } finally {
                pending.finish()
            }
        }
    }
}
