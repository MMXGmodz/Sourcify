package com.metrolist.music.utils

import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Song
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

suspend fun MusicDatabase.renameSong(metadata: MediaMetadata, title: String): Song = withContext(Dispatchers.IO) {
    val name = title.trim()
    require(name.isNotEmpty() && name.length <= 300)
    withTransaction {
        insert(metadata)
        val existing = requireNotNull(song(metadata.id).first())
        update(existing.song.copy(title = name))
    }
    requireNotNull(song(metadata.id).first())
}
