package com.metrolist.music.playback

import org.junit.Assert.*
import org.junit.Test

class MusicVideoStatsTest {
    private val results = mutableListOf<Pair<String, Long>>()
    private val stats = MusicVideoStats { song, duration -> results += song to duration }

    @Test fun shortSourceSegmentsCrossOneHistoryThresholdTogether() {
        val occurrence = stats.beginReplacement("audio", "original")
        stats.attachReplacement(occurrence, "video")
        assertTrue(stats.onSegment("audio", 20_000))
        assertTrue(stats.onSegment("video", 20_000))
        assertTrue(results.isEmpty())
        stats.finishOccurrence()
        assertEquals(listOf("original" to 40_000L), results)
    }

    @Test fun longSegmentsAndAudioRestoreProduceOneCountWithActualTime() {
        val occurrence = stats.beginReplacement("audio", "original")
        stats.attachReplacement(occurrence, "video")
        stats.onSegment("audio", 40_000)
        assertSame(occurrence, stats.beginReplacement("video", "original"))
        stats.attachReplacement(occurrence, "restored")
        stats.onSegment("video", 40_000)
        stats.finishOccurrence()
        assertTrue(results.isEmpty())
        stats.onSegment("restored", 10_000)
        assertEquals(listOf("original" to 90_000L), results)
    }

    @Test fun sameIdAdjacentOccurrenceAndLateCallbacksRemainSeparate() {
        val first = stats.beginReplacement("first-audio", "same-id")
        stats.attachReplacement(first, "first-video")
        stats.finishOccurrence()
        val second = stats.beginReplacement("second-audio", "same-id")
        stats.attachReplacement(second, "second-video")
        stats.onSegment("second-audio", 20_000)
        stats.onSegment("first-video", 30_000)
        stats.onSegment("first-audio", 10_000)
        assertEquals(listOf("same-id" to 40_000L), results)
        stats.onSegment("second-video", 30_000)
        stats.finishOccurrence()
        assertEquals(listOf("same-id" to 40_000L, "same-id" to 50_000L), results)
    }

    @Test fun repeatReusingWindowUidDoesNotMergeOccurrences() {
        val first = stats.beginReplacement("audio", "same-id")
        stats.attachReplacement(first, "video")
        stats.finishOccurrence()
        val repeat = stats.beginReplacement("video", "same-id")
        stats.attachReplacement(repeat, "repeat-audio")
        stats.onSegment("audio", 20_000)
        stats.onSegment("video", 20_000)
        assertEquals(listOf("same-id" to 40_000L), results)
        stats.onSegment("video", 25_000)
        stats.onSegment("repeat-audio", 25_000)
        stats.finishOccurrence()
        assertEquals(listOf("same-id" to 40_000L, "same-id" to 50_000L), results)
        assertFalse(stats.onSegment("ordinary-next", 40_000))
    }

    @Test fun sourceNeverObservedByNativeStatsDoesNotLoseHistory() {
        val group = stats.beginReplacement("audio", "original")
        stats.attachReplacement(group, "skipped-video")
        stats.attachReplacement(group, "restored")
        stats.onSegment("audio", 20_000)
        stats.onAnalyticsBatchComplete(stats, "restored")
        stats.finishOccurrence()
        stats.onSegment("restored", 20_000)
        assertEquals(listOf("original" to 40_000L), results)
    }

    @Test fun ordinaryRepeatCallbackBeforeFirstSwitchKeepsItsOwnCount() {
        stats.finishOccurrence("reused-window")
        val group = stats.beginReplacement("reused-window", "same-id")
        stats.attachReplacement(group, "video")
        assertFalse(stats.onSegment("reused-window", 40_000))
        assertTrue(stats.onSegment("reused-window", 20_000))
        stats.finishOccurrence()
        stats.onSegment("video", 20_000)
        assertEquals(listOf("same-id" to 40_000L), results)
    }

    @Test fun closedZeroSessionOccurrenceFinishesAtNativeTransitionBatch() {
        val group = stats.beginReplacement("audio", "original")
        stats.attachReplacement(group, "never-observed")
        stats.onSegment("audio", 35_000)
        stats.finishOccurrence()
        stats.onAnalyticsBatchComplete(stats, "next", transitioned = true)
        assertEquals(listOf("original" to 35_000L), results)
    }

    @Test fun releaseClosesOccurrenceAndWaitsForFinalNativeCallback() {
        val group = stats.beginReplacement("audio", "original")
        stats.attachReplacement(group, "video")
        stats.onSegment("audio", 15_000)
        stats.finishOccurrence()
        stats.onSegment("video", 20_000)
        stats.finishOccurrence()
        assertEquals(listOf("original" to 35_000L), results)
    }
}
