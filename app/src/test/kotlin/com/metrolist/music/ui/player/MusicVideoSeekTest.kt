package com.metrolist.music.ui.player

import androidx.media3.common.C
import androidx.media3.common.Player
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class MusicVideoSeekTest {
    @Test fun seeksClampToTheCurrentItemAndUntrustedPreferenceRange() {
        assertEquals(15_000L, musicVideoSeekTarget(10_000, 60_000, 5, true))
        assertEquals(5_000L, musicVideoSeekTarget(10_000, 60_000, 5, false))
        assertEquals(0L, musicVideoSeekTarget(2_000, 60_000, 5, false))
        assertEquals(60_000L, musicVideoSeekTarget(59_000, 60_000, 5, true))
        assertEquals(11_000L, musicVideoSeekTarget(10_000, 60_000, -5, true))
        assertEquals(30_000L, musicVideoSeekTarget(10_000, 60_000, 500, true))
        assertEquals(15_000L, musicVideoSeekTarget(10_000, C.TIME_UNSET, 5, true))
        assertEquals(0L, musicVideoSeekTarget(10_000, 0, 5, true))
        assertEquals(Long.MAX_VALUE, musicVideoSeekTarget(Long.MAX_VALUE - 1, C.TIME_UNSET, 5, true))
    }

    @Test fun repeatedSeeksReadThePrimaryPlayersCurrentPositionEachTime() {
        var position = 12_000L
        val seeks = mutableListOf<Long>()
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "isCommandAvailable" -> true
                "getCurrentPosition" -> position
                "getDuration" -> 60_000L
                "seekTo" -> { position = args!![0] as Long; seeks += position; null }
                else -> null
            }
        } as Player
        assertTrue(seekMusicVideo(player, 5, true))
        assertTrue(seekMusicVideo(player, 5, true))
        assertTrue(seekMusicVideo(player, 5, false))
        assertEquals(listOf(17_000L, 22_000L, 17_000L), seeks)
    }

    @Test fun unavailableSeekingDoesNotCallThePlayer() {
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            assertEquals("isCommandAvailable", method.name)
            false
        } as Player
        assertFalse(seekMusicVideo(player, 5, true))
    }
}
