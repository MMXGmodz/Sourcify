package com.metrolist.music.lyrics

import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity
import kotlinx.coroutines.flow.first

/** A late automatic fetch must not overwrite a newer successful fetch or the user's edit. */
internal suspend fun MusicDatabase.saveFetchedLyricsIfMissing(value: LyricsEntity) {
    withTransaction {
        val latest = lyrics(value.id).first()
        if (latest == null || latest.lyrics == LyricsEntity.LYRICS_NOT_FOUND) upsert(value)
    }
}
