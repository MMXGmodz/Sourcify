/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import com.metrolist.music.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val description: String,
    val releaseDate: String,
    val assets: List<ReleaseAsset>
)

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val architecture: String,
    val variant: String // "foss" or "gms"
)

object Updater {
    private val client = HttpClient { expectSuccess = true }
    var lastCheckTime = -1L
        private set
    
    private var cachedReleaseInfo: ReleaseInfo? = null
    private var cachedAllReleases: List<ReleaseInfo> = emptyList()
    
    private const val CHECK_INTERVAL_MILLIS = 2 * 60 * 60 * 1000L // 2 hours
    private const val GITHUB_API_BASE = "https://api.github.com/repos/MMXGmodz/Sourcify"
    private const val KMP_LATEST_RELEASE_URL = "https://api.github.com/repos/MetrolistGroup/Metrolist-KMP/releases/latest"
    private const val KMP_APK_NAME = "Metrolist.apk"

    /**
     * Compares two version strings.
     * Returns: 1 if v1 > v2, -1 if v1 < v2, 0 if equal
     */
    fun compareVersions(v1: String, v2: String): Int {
        val v1Parts = v1.removePrefix("android-tv-v").removePrefix("v").substringBefore("-").substringBefore("+").split(".").map { it.toIntOrNull() ?: 0 }
        val v2Parts = v2.removePrefix("android-tv-v").removePrefix("v").substringBefore("-").substringBefore("+").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLength = maxOf(v1Parts.size, v2Parts.size)
        
        for (i in 0 until maxLength) {
            val part1 = v1Parts.getOrNull(i) ?: 0
            val part2 = v2Parts.getOrNull(i) ?: 0
            when {
                part1 > part2 -> return 1
                part1 < part2 -> return -1
            }
        }
        return 0
    }

    /**
     * Checks if the latest version is newer than the current version.
     * Returns true if an update is available (latestVersion > currentVersion)
     */
    fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean {
        return compareVersions(latestVersion, currentVersion) > 0
    }

    /**
     * Get the current app's architecture and variant
     */
    private fun getCurrentAppVariant(): Pair<String, String> {
        val architecture = BuildConfig.ARCHITECTURE
        val variant = if (BuildConfig.CAST_AVAILABLE) "gms" else "foss"
        return architecture to variant
    }

    /**
     * Parse release assets from GitHub API response
     */
    private fun parseAssets(assetsArray: JSONArray): List<ReleaseAsset> {
        val assets = mutableListOf<ReleaseAsset>()
        
        for (i in 0 until assetsArray.length()) {
            val asset = assetsArray.getJSONObject(i)
            val name = asset.getString("name")
            
            // Skip non-APK files
            if (!name.endsWith(".apk")) continue
            
            val downloadUrl = asset.getString("browser_download_url")
            val size = asset.getLong("size")
            
            // Parse architecture and variant from filename
            val (arch, variant) = when {
                name == "Metrolist.apk" -> "universal" to "foss"
                name == "Metrolist-with-Google-Cast.apk" -> "universal" to "gms"
                name.startsWith("app-") && name.endsWith("-release.apk") -> {
                    val arch = name.removePrefix("app-").removeSuffix("-release.apk")
                    arch to "foss"
                }
                name.startsWith("app-") && name.endsWith("-with-Google-Cast.apk") -> {
                    val arch = name.removePrefix("app-").removeSuffix("-with-Google-Cast.apk")
                    arch to "gms"
                }
                else -> null to null
            }
            
            if (arch != null && variant != null) {
                assets.add(ReleaseAsset(name, downloadUrl, size, arch, variant))
            }
        }
        
        return assets
    }

    /**
     * Fetch latest release from GitHub API
     */
    suspend fun getLatestRelease(forceRefresh: Boolean = false): Result<ReleaseInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Return cached if available and not forcing refresh
                if (cachedReleaseInfo != null && !forceRefresh) {
                    return@runCatching cachedReleaseInfo!!
                }
                
                // /latest excludes prereleases; Sourcify also ships Android preview releases.
                val releaseInfo = getAllReleases(forceRefresh).getOrThrow().firstOrNull()
                    ?: error("No compatible Sourcify release available")

                cachedReleaseInfo = releaseInfo
                lastCheckTime = System.currentTimeMillis()
                releaseInfo
            }.onFailure { if (it is CancellationException) throw it }
        }

    /**
     * Fetch all releases from GitHub API (paginated)
     */
    suspend fun getAllReleases(forceRefresh: Boolean = false): Result<List<ReleaseInfo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (cachedAllReleases.isNotEmpty() && !forceRefresh) {
                    return@runCatching cachedAllReleases
                }
                
                val releases = mutableListOf<ReleaseInfo>()
                var page = 1
                var hasMore = true
                
                while (hasMore && page <= 10) { // Limit to 10 pages
                    val response = client.get("$GITHUB_API_BASE/releases?page=$page&per_page=30")
                        .bodyAsText()
                    val json = JSONArray(response)
                    
                    if (json.length() == 0) {
                        hasMore = false
                        break
                    }
                    
                    releases.addAll(parseSourcifyReleases(response))
                    if (json.length() < 30) break
                    page++
                }
                
                val sorted = releases.sortedWith { a, b -> compareVersions(b.versionName, a.versionName) }
                cachedAllReleases = sorted
                sorted
            }.onFailure { if (it is CancellationException) throw it }
        }

    internal fun isSourcifyAsset(tag: String, asset: ReleaseAsset, tv: Boolean = BuildConfig.TV_INTERFACE): Boolean {
        val prefix = if (tv) "android-tv-v" else "v"
        val name = if (tv) "Sourcify-TV.apk" else "Sourcify.apk"
        return Regex("${prefix}(\\d+\\.\\d+\\.\\d+)(?:-preview\\.[1-9]\\d*)?").matches(tag) &&
            asset.name == name && asset.size > 0 &&
            asset.downloadUrl == "https://github.com/MMXGmodz/Sourcify/releases/download/$tag/$name"
    }

    internal fun parseSourcifyReleases(response: String, tv: Boolean = BuildConfig.TV_INTERFACE): List<ReleaseInfo> {
        val releases = JSONArray(response)
        val prefix = if (tv) "android-tv-v" else "v"
        val pattern = Regex("${prefix}(\\d+\\.\\d+\\.\\d+)(?:-preview\\.[1-9]\\d*)?")
        return buildList {
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                if (release.optBoolean("draft")) continue
                val tag = release.optString("tag_name")
                val version = pattern.matchEntire(tag)?.groupValues?.get(1) ?: continue
                val assets = release.optJSONArray("assets") ?: continue
                val compatible = buildList {
                    for (j in 0 until assets.length()) {
                        val json = assets.getJSONObject(j)
                        val asset = ReleaseAsset(json.optString("name"), json.optString("browser_download_url"),
                            json.optLong("size"), "universal", "foss")
                        if (isSourcifyAsset(tag, asset, tv)) add(asset)
                    }
                }
                if (compatible.isNotEmpty()) add(ReleaseInfo(tag, version,
                    release.optString("body").takeUnless { release.isNull("body") }.orEmpty(),
                    release.optString("published_at"), compatible))
            }
        }.sortedWith { a, b -> compareVersions(b.versionName, a.versionName) }
    }

    internal fun parseKmpRelease(response: String): ReleaseInfo? {
        val release = JSONObject(response)
        val assets = parseAssets(release.getJSONArray("assets")).filter { it.name == KMP_APK_NAME }
        val tagName = release.getString("tag_name")

        return ReleaseInfo(
            tagName = tagName,
            versionName = tagName.removePrefix("v"),
            description = release.optString("body").takeUnless { release.isNull("body") }.orEmpty(),
            releaseDate = release.getString("published_at"),
            assets = assets,
        ).takeIf { assets.isNotEmpty() }
    }

    /**
     * Returns the latest stable KMP release when it includes an Android APK.
     */
    suspend fun getLatestKmpRelease(): Result<ReleaseInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                parseKmpRelease(client.get(KMP_LATEST_RELEASE_URL).bodyAsText())
            }.onFailure { if (it is CancellationException) throw it }
        }

    /**
     * Get the download URL for the correct app variant
     */
    fun getDownloadUrlForCurrentVariant(releaseInfo: ReleaseInfo): String? {
        if (BuildConfig.CAST_AVAILABLE) return null
        return releaseInfo.assets.firstOrNull { isSourcifyAsset(releaseInfo.tagName, it) }?.downloadUrl
    }

    /**
     * Check if update is needed (respects 2-hour cache)
     */
    suspend fun checkForUpdate(forceRefresh: Boolean = false): Result<Pair<ReleaseInfo?, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Check if we should fetch (2 hour interval)
                val shouldFetch = forceRefresh || 
                    (System.currentTimeMillis() - lastCheckTime) > CHECK_INTERVAL_MILLIS
                
                if (!shouldFetch && cachedReleaseInfo != null) {
                    val hasUpdate = isUpdateAvailable(
                        BuildConfig.BASE_VERSION_NAME,
                        cachedReleaseInfo!!.versionName
                    )
                    return@runCatching cachedReleaseInfo!! to hasUpdate
                }
                
                val result = getLatestRelease(forceRefresh = true)
                if (result.isSuccess) {
                    val releaseInfo = result.getOrThrow()
                    val hasUpdate = isUpdateAvailable(
                        BuildConfig.BASE_VERSION_NAME,
                        releaseInfo.versionName
                    )
                    releaseInfo to hasUpdate
                } else {
                    throw result.exceptionOrNull() ?: Exception("Unknown error")
                }
            }.onFailure { if (it is CancellationException) throw it }
        }

    /**
     * Get the latest release info (cached)
     */
    fun getCachedLatestRelease(): ReleaseInfo? = cachedReleaseInfo
}
