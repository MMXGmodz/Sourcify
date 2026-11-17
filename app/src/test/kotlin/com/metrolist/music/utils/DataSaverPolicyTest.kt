package com.metrolist.music.utils

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.metrolist.music.constants.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DataSaverPolicyTest {
    @Test fun defaultSaverForcesMobileLowEvenWhenLegacyQualityWasHigh() {
        val prefs = mutablePreferencesOf(AudioQualityKey to "HIGH", StreamingMobileQualityKey to "HIGH")
        assertTrue(dataSaverEnabled(prefs))
        assertEquals(AudioQuality.HIGH, streamingAudioQuality(prefs, false))
        assertEquals(AudioQuality.LOW, streamingAudioQuality(prefs, true))
        prefs[DataSaverKey] = false
        assertEquals(AudioQuality.HIGH, streamingAudioQuality(prefs, true))
    }

    @Test fun mobileDownloadsRequireBothOptInAndDisabledSaver() {
        val prefs = mutablePreferencesOf()
        assertFalse(mobileDownloadsAllowed(prefs))
        assertFalse(backgroundDownloadsAllowed(prefs))
        prefs[DownloadMobileAllowedKey] = true
        assertFalse(mobileDownloadsAllowed(prefs))
        prefs[DataSaverKey] = false
        assertTrue(mobileDownloadsAllowed(prefs))
    }

    @Test fun migrationMakesOldPermissiveSettingsSafeOnceAndKeepsOtherPreferences() = runBlocking {
        val old = mutablePreferencesOf(CanvasWifiOnlyKey to false, DownloadMobileAllowedKey to true,
            DownloadBackgroundAllowedKey to true, AudioQualityKey to "HIGH")
        assertTrue(StrictDataDefaultsMigration.shouldMigrate(old))
        val migrated = StrictDataDefaultsMigration.migrate(old)
        assertTrue(dataSaverEnabled(migrated))
        assertEquals(true, migrated[CanvasWifiOnlyKey])
        assertFalse(mobileDownloadsAllowed(migrated))
        assertFalse(backgroundDownloadsAllowed(migrated))
        assertEquals("HIGH", migrated[AudioQualityKey])
        assertFalse(StrictDataDefaultsMigration.shouldMigrate(migrated))
    }
}
