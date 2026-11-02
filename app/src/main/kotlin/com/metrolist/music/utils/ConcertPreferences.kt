package com.metrolist.music.utils

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

internal object ConcertPreferences {
    val cityKey = stringPreferencesKey("concertManualCity")
    val savedKey = stringSetPreferencesKey("savedConcertEventIds")

    suspend fun setSaved(context: Context, id: String, saved: Boolean) {
        if (!id.matches(Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))) return
        context.safeDataStoreEdit { prefs ->
            val ids = prefs[savedKey].orEmpty()
            prefs[savedKey] = if (saved) ids + id else ids - id
        }
    }
}
