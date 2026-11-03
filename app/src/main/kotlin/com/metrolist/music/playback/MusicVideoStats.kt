package com.metrolist.music.playback

/** Joins finalized Media3 source sessions only when our in-place switch links them. */
internal class MusicVideoStats(private val emit: (String, Long) -> Unit) {
    internal class Occurrence(val songId: String) {
        val sources = mutableSetOf<Any>()
        val pending = mutableSetOf<Any>()
        var playTimeMs = 0L
        var closed = false
        var emitted = false
    }
    private var active: Occurrence? = null
    private val pendingBySource = mutableMapOf<Any, ArrayDeque<Occurrence?>>()
    private val owners = mutableMapOf<Any, Any>()

    fun beginReplacement(sourceUid: Any, songId: String, owner: Any = this): Occurrence {
        if (active?.songId != songId) finishOccurrence()
        val occurrence = active ?: Occurrence(songId).also { active = it }
        attachReplacement(occurrence, sourceUid, owner)
        return occurrence
    }

    fun attachReplacement(occurrence: Occurrence, sourceUid: Any, owner: Any = this) {
        owners[sourceUid] = owner
        if (occurrence.sources.add(sourceUid)) {
            occurrence.pending.add(sourceUid)
            pendingBySource.getOrPut(sourceUid) { ArrayDeque() }.addLast(occurrence)
        }
    }

    fun finishOccurrence(previousSourceUid: Any? = null, owner: Any = this) {
        val occurrence = active
        if (occurrence == null) {
            if (previousSourceUid != null) {
                owners[previousSourceUid] = owner
                pendingBySource.getOrPut(previousSourceUid) { ArrayDeque() }.addLast(null)
            }
            return
        }
        active = null
        occurrence.closed = true
        emitIfComplete(occurrence)
    }

    /** False leaves ordinary, unrelated Media3 sessions on the existing statistics path. */
    fun onSegment(sourceUid: Any, playTimeMs: Long): Boolean {
        val waiting = pendingBySource[sourceUid] ?: return false
        val occurrence = waiting.removeFirst()
        if (waiting.isEmpty()) { pendingBySource.remove(sourceUid); owners.remove(sourceUid) }
        if (occurrence == null) return false
        occurrence.pending.remove(sourceUid)
        occurrence.playTimeMs += playTimeMs.coerceAtLeast(0)
        emitIfComplete(occurrence)
        return true
    }

    // Native PlaybackStatsListener finalizes removed sessions synchronously in onEvents.
    // A replaced UID still unreported after that batch never acquired a stats session.
    fun onAnalyticsBatchComplete(owner: Any, currentUid: Any?, transitioned: Boolean = false) {
        pendingBySource.keys.toList().forEach { uid ->
            if (owners[uid] !== owner) return@forEach
            val waiting = pendingBySource.getValue(uid)
            val removed = waiting.filter { it == null || uid != currentUid || (it.closed && transitioned) }
            waiting.removeAll(removed.toSet())
            removed.filterNotNull().forEach { occurrence ->
                occurrence.pending.remove(uid)
                emitIfComplete(occurrence)
            }
            if (waiting.isEmpty()) { pendingBySource.remove(uid); owners.remove(uid) }
        }
    }

    private fun emitIfComplete(occurrence: Occurrence) {
        if (occurrence.closed && occurrence.pending.isEmpty() && !occurrence.emitted) {
            occurrence.emitted = true
            emit(occurrence.songId, occurrence.playTimeMs)
        }
    }
}
