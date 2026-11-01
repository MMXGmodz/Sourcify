package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdaterTest {
    @Test fun sourcifyPreviewTagIsNewerThanInstalledVersion() {
        org.junit.Assert.assertTrue(Updater.isUpdateAvailable("13.7.9", "v13.7.10-preview.1"))
        org.junit.Assert.assertFalse(Updater.isUpdateAvailable("13.7.10", "v13.7.10-preview.1"))
        org.junit.Assert.assertFalse(Updater.isUpdateAvailable("13.7.10", "v13.7.9-preview.1"))
    }

    private fun release(tag: String, apk: String = "Sourcify.apk", draft: Boolean = false,
        url: String = "https://github.com/MMXGmodz/Sourcify/releases/download/$tag/$apk") =
        """{"tag_name":"$tag","name":"Fancy title, not a version","draft":$draft,"prerelease":true,
        "published_at":"2026-11-01T12:00:00Z","body":null,
        "assets":[{"name":"$apk","browser_download_url":"$url","size":100}]}"""

    @Test fun selectsNewestCompatibleAndroidReleaseIncludingPreviews() {
        val releases = Updater.parseSourcifyReleases("[" + listOf(
            release("v13.7.9-preview.1"), release("v13.7.10-preview.1"),
            release("v99.0.0", draft = true), release("windows-v99.0.0", "Sourcify.exe"),
            release("android-tv-v99.0.0", "Sourcify-TV.apk")
        ).joinToString(",") + "]", tv = false)
        assertEquals(listOf("13.7.10", "13.7.9"), releases.map { it.versionName })
        assertEquals("", releases.first().description)
        assertEquals("Sourcify.apk", releases.first().assets.single().name)
    }

    @Test fun rejectsForeignAssetsMalformedVersionsAndWrongApp() {
        val releases = Updater.parseSourcifyReleases("[" + listOf(
            release("v13.7.10", url = "https://evil.example/Sourcify.apk"),
            release("v13.7.11", url = "https://github.com/Other/Repo/releases/download/v13.7.11/Sourcify.apk"),
            release("v13.7.12", "Metrolist.apk"), release("vbanana"),
            release("v13.7.10", url = "http://github.com/MMXGmodz/Sourcify/releases/download/v13.7.10/Sourcify.apk"),
            release("v13.7.10", url = "https://github.com/MMXGmodz/Sourcify/releases/download/v13.7.9/Sourcify.apk")
        ).joinToString(",") + "]", tv = false)
        org.junit.Assert.assertTrue(releases.isEmpty())
    }

    @Test fun tvSelectsOnlyTvApk() {
        val response = "[" + release("v13.7.10") + "," + release("android-tv-v13.7.9-preview.1", "Sourcify-TV.apk") + "]"
        val releases = Updater.parseSourcifyReleases(response, tv = true)
        assertEquals("13.7.9", releases.single().versionName)
        assertEquals("Sourcify-TV.apk", releases.single().assets.single().name)
    }

    @Test
    fun parsesKmpReleaseArtifact() {
        val response =
            """
            {
              "tag_name": "v1.2.3",
              "body": null,
              "published_at": "2026-09-05T12:00:00Z",
              "assets": [{
                "name": "Metrolist.apk",
                "browser_download_url": "https://example.com/Metrolist.apk",
                "size": 42
              }]
            }
            """.trimIndent()
        val release = checkNotNull(Updater.parseKmpRelease(response))

        assertEquals("1.2.3", release.versionName)
        assertEquals("", release.description)
        assertEquals("https://example.com/Metrolist.apk", release.assets.single().downloadUrl)
        assertNull(Updater.parseKmpRelease(response.replace("Metrolist.apk", "Metrolist-with-Google-Cast.apk")))
    }
}
