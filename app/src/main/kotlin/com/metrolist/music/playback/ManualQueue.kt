package com.metrolist.music.playback

internal class ManualQueue {
    private val pending = mutableSetOf<String>()

    fun clear() = pending.clear()

    fun add(ids: List<String>) {
        pending.addAll(ids)
    }

    fun reconcile(itemIds: List<String?>, currentIndex: Int) {
        // Seeking past queued occurrences consumes them, so seeking back cannot resurrect priority.
        pending.retainAll(itemIds.drop((currentIndex + 1).coerceAtLeast(0)).filterNotNull().toSet())
    }

    fun insertionIndex(itemIds: List<String?>, currentIndex: Int, playNext: Boolean = false): Int {
        reconcile(itemIds, currentIndex)
        val lastPending = if (playNext) null else pendingIndices(itemIds, currentIndex).lastOrNull()
        return ((lastPending ?: currentIndex) + 1).coerceIn(0, itemIds.size)
    }

    fun prioritize(order: List<Int>, itemIds: List<String?>, currentIndex: Int): List<Int> {
        val next = pendingIndices(itemIds, currentIndex)
        if (next.isEmpty()) return order
        val pendingIndices = next.toSet()
        val remaining = order.filterNot { it in pendingIndices }
        val insertion = remaining.indexOf(currentIndex) + 1
        return remaining.take(insertion) + next + remaining.drop(insertion)
    }

    private fun pendingIndices(itemIds: List<String?>, currentIndex: Int): List<Int> =
        itemIds.indices.filter { it > currentIndex && itemIds[it] in pending }
}
