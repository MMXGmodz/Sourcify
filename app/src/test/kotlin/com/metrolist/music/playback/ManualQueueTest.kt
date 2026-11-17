package com.metrolist.music.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class ManualQueueTest {
    private val queue = ManualQueue()

    private fun MutableList<String?>.enqueue(vararg ids: String, next: Boolean = false, current: Int = 0) {
        val index = queue.insertionIndex(this, current, next)
        queue.add(ids.toList())
        addAll(index, ids.toList())
    }

    @Test
    fun `separate additions and batches play FIFO before the playlist`() {
        val items = mutableListOf<String?>("current", "playlist1", "playlist2")
        items.enqueue("a")
        items.enqueue("b", "c")
        items.enqueue("d")
        assertEquals(listOf("current", "a", "b", "c", "d", "playlist1", "playlist2"), items)
    }

    @Test
    fun `play next precedes manual queue and later additions follow it`() {
        val items = mutableListOf<String?>("current", "playlist")
        items.enqueue("a", "b")
        items.enqueue("next", next = true)
        items.enqueue("c")
        assertEquals(listOf("current", "next", "a", "b", "c", "playlist"), items)
    }

    @Test
    fun `consumed occurrences do not return when seeking backward`() {
        val items = mutableListOf<String?>("current", "playlist")
        items.enqueue("a", "b")
        queue.reconcile(items, 1)
        items.enqueue("c", current = 1)
        assertEquals(listOf("current", "a", "b", "c", "playlist"), items)
        queue.reconcile(items, 4)
        items.enqueue("new", current = 0)
        assertEquals(listOf("current", "new", "a", "b", "c", "playlist"), items)
    }

    @Test
    fun `removing and moving manual occurrences updates the next insertion`() {
        val items = mutableListOf<String?>("current", "playlist")
        items.enqueue("a", "b", "c")
        items.remove("b")
        items.remove("c")
        items.add(1, "c")
        queue.reconcile(items, 0)
        items.enqueue("d")
        assertEquals(listOf("current", "c", "a", "d", "playlist"), items)
    }

    @Test
    fun `shuffle keeps explicit queue ordered and background traversal intact`() {
        val items = mutableListOf<String?>("past", "current", "playlist1", "playlist2")
        items.enqueue("a", "b", current = 1)
        assertEquals(listOf(0, 1, 2, 3, 5, 4), queue.prioritize(listOf(0, 1, 5, 3, 4, 2), items, 1))
        assertEquals(listOf(1, 2, 3, 5, 4, 0), queue.prioritize(listOf(1, 5, 4, 0, 2, 3), items, 1))
    }

    @Test
    fun `shuffle after a backward seek prioritizes only unplayed manual occurrences`() {
        val items = mutableListOf<String?>("past", "current", "playlist")
        items.enqueue("a", "b", current = 1)
        queue.reconcile(items, 2)
        queue.reconcile(items, 0)
        assertEquals(listOf(0, 3, 1, 2, 4), queue.prioritize(listOf(0, 1, 2, 3, 4), items, 0))
    }

    @Test
    fun `new playback queue clears priority even when occurrences are reused`() {
        val items = mutableListOf<String?>("current", "playlist")
        items.enqueue("a", "b")
        queue.clear()
        assertEquals(1, queue.insertionIndex(items, 0))
        assertEquals(listOf(0, 3, 2, 1), queue.prioritize(listOf(0, 3, 2, 1), items, 0))
    }

    @Test
    fun `empty playback queue inserts at the start`() {
        assertEquals(0, queue.insertionIndex(emptyList(), -1))
    }
}
