package com.metrolist.music.utils

import android.Manifest
import android.app.Application
import android.app.DownloadManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.net.Uri
import android.os.Build
import com.metrolist.music.UpdateActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class UpdateInstallTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val recordFile get() = File(context.noBackupFilesDir, "sourcify-update.json")

    @Before fun clearRecord() {
        recordFile.delete()
    }

    private fun release(): ReleaseInfo {
        val tv = com.metrolist.music.BuildConfig.TV_INTERFACE
        val tag = if (tv) "android-tv-v99.0.0-preview.1" else "v99.0.0-preview.1"
        val name = if (tv) "Sourcify-TV.apk" else "Sourcify.apk"
        return ReleaseInfo(tag, "99.0.0", "", "", listOf(ReleaseAsset(name,
            "https://github.com/MMXGmodz/Sourcify/releases/download/$tag/$name", 100, "universal", "foss")))
    }

    @Suppress("DEPRECATION")
    private fun apk(name: String = context.packageName, versionCode: Long = 2,
        version: String = "99.0.0", signer: String? = "1234"): PackageInfo = PackageInfo().apply {
        packageName = name
        versionName = version
        if (Build.VERSION.SDK_INT >= 28) {
            longVersionCode = versionCode
            signingInfo = SigningInfo().also { info ->
                shadowOf(info).setSignatures(signer?.let { arrayOf(Signature(it)) } ?: emptyArray())
            }
        } else {
            this.versionCode = versionCode.toInt()
            signatures = signer?.let { arrayOf(Signature(it)) }
        }
    }

    @Test fun ownedDownloadMustMatchBothSavedIdAndOriginalSource() {
        val id = UpdateDownload.enqueue(context, release())
        shadowOf(shadowOf(manager).getRequest(id)).status = DownloadManager.STATUS_RUNNING
        assertEquals(id, UpdateDownload.query(context, id).download.id)
        assertThrows(IllegalArgumentException::class.java) { UpdateDownload.query(context, id + 1) }

        val foreignId = manager.enqueue(DownloadManager.Request(Uri.parse("https://evil.example/other.apk")))
        shadowOf(shadowOf(manager).getRequest(foreignId)).status = DownloadManager.STATUS_SUCCESSFUL
        val record = JSONObject(recordFile.readText()).put("id", foreignId)
        recordFile.writeText(record.toString())
        assertThrows(IllegalArgumentException::class.java) { UpdateDownload.query(context, foreignId) }
    }

    @Test fun forgedPersistedReleaseIsNotTreatedAsOwned() {
        UpdateDownload.enqueue(context, release())
        recordFile.writeText(JSONObject(recordFile.readText()).put("url", "https://evil.example/Sourcify.apk").toString())
        assertNull(UpdateDownload.ownedDownload(context))
    }

    @Test fun tagAndVersionMustDescribeTheSameRelease() {
        assertThrows(IllegalArgumentException::class.java) {
            UpdateDownload.enqueue(context, release().copy(versionName = "100.0.0"))
        }
    }

    @Test fun onlyNewerSamePackageSameSignerCanReachInstaller() {
        val installed = apk(versionCode = 1, version = "13.8.0")
        UpdateInstaller.validatePackage(installed, apk(), "99.0.0")
        assertThrows(IllegalArgumentException::class.java) {
            UpdateInstaller.validatePackage(installed, apk(name = "com.other.app"), "99.0.0")
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpdateInstaller.validatePackage(installed, apk(versionCode = 1), "99.0.0")
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpdateInstaller.validatePackage(installed, apk(version = "13.7.0"), "99.0.0")
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpdateInstaller.validatePackage(installed, apk(signer = "5678"), "99.0.0")
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpdateInstaller.validatePackage(installed, apk(signer = null), "99.0.0")
        }
    }

    @Test @Config(sdk = [27]) fun androidEightAlsoRequiresMatchingSignatures() {
        val installed = apk(versionCode = 1, version = "13.8.0")
        UpdateInstaller.validatePackage(installed, apk(), "99.0.0")
        assertThrows(IllegalArgumentException::class.java) {
            UpdateInstaller.validatePackage(installed, apk(signer = "5678"), "99.0.0")
        }
    }

    @Test fun installerReceivesOnlyPrivateContentUriAndTemporaryReadGrant() {
        val uri = Uri.parse("content://${context.packageName}.FileProvider/cache/apk-updates/verified.apk")
        val intent = UpdateInstaller.installIntent(context, uri)
        assertEquals(Intent.ACTION_INSTALL_PACKAGE, intent.action)
        assertEquals(uri, intent.data)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags)
        assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        assertThrows(IllegalArgumentException::class.java) {
            UpdateInstaller.installIntent(context, Uri.parse("file:///sdcard/Download/other.apk"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpdateInstaller.installIntent(context, Uri.parse("content://other.app/other.apk"))
        }
    }

    @Test fun backgroundCompletionOnlyPostsNotificationOpeningUpdateScreen() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val id = UpdateDownload.enqueue(context, release())
        shadowOf(shadowOf(manager).getRequest(id)).status = DownloadManager.STATUS_SUCCESSFUL
        UpdateNotifications.showDownloadResult(context, id)
        val notification = shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .getNotification(UpdateNotifications.DOWNLOAD_NOTIFICATION_ID)
        assertNotNull(notification)
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(UpdateActivity::class.java.name, intent.component!!.className)
        assertNull(intent.data)
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test fun unownedCompletionCannotNotifyOrLaunchAnything() {
        UpdateDownloadReceiver().onReceive(context, Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            .putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 999L))
        assertEquals(0, shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).size())
        assertNull(shadowOf(context).nextStartedActivity)
    }
}
