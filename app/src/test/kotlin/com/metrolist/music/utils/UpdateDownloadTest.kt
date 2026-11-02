package com.metrolist.music.utils

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class UpdateDownloadTest {
    @Test fun queuesApkInAndroidDownloadsAndReusesPendingDownload() {
        val context = RuntimeEnvironment.getApplication()
        val tv = com.metrolist.music.BuildConfig.TV_INTERFACE
        val tag = if (tv) "android-tv-v13.7.10-preview.1" else "v13.7.10-preview.1"
        val name = if (tv) "Sourcify-TV.apk" else "Sourcify.apk"
        val url = "https://github.com/MMXGmodz/Sourcify/releases/download/$tag/$name"
        val release = ReleaseInfo(tag, "13.7.10", "", "", listOf(ReleaseAsset(name, url, 100, "universal", "foss")))
        val id = UpdateDownload.enqueue(context, release)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = shadowOf(shadowOf(manager).getRequest(id))
        request.status = DownloadManager.STATUS_PENDING
        assertEquals(id, UpdateDownload.enqueue(context, release))
        assertEquals(url, request.uri.toString())
        assertEquals("application/vnd.android.package-archive", request.mimeType)
        assertEquals(DownloadManager.Request.VISIBILITY_VISIBLE, request.notificationVisibility)
        assertTrue(request.destination.toString().contains("/${Environment.DIRECTORY_DOWNLOADS}/Sourcify-13.7.10-"))
    }

    @Test @Config(sdk = [28]) fun oldAndroidUsesAppDownloadsWithoutStoragePermission() {
        val context = RuntimeEnvironment.getApplication()
        val tv = com.metrolist.music.BuildConfig.TV_INTERFACE
        val tag = if (tv) "android-tv-v13.7.10" else "v13.7.10"
        val name = if (tv) "Sourcify-TV.apk" else "Sourcify.apk"
        val release = ReleaseInfo(tag, "13.7.10", "", "", listOf(ReleaseAsset(name,
            "https://github.com/MMXGmodz/Sourcify/releases/download/$tag/$name", 100, "universal", "foss")))
        val id = UpdateDownload.enqueue(context, release)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = shadowOf(shadowOf(manager).getRequest(id))
        assertTrue(request.destination.path!!.startsWith(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!.path + "/"))
        request.status = DownloadManager.STATUS_FAILED
        assertNotEquals(id, UpdateDownload.enqueue(context, release))
    }

    @Test fun refusesToQueueForeignApk() {
        val release = ReleaseInfo("v13.7.10", "13.7.10", "", "", listOf(
            ReleaseAsset("Sourcify.apk", "https://evil.example/Sourcify.apk", 100, "universal", "foss")))
        assertThrows(IllegalArgumentException::class.java) { UpdateDownload.enqueue(RuntimeEnvironment.getApplication(), release) }
    }
}
