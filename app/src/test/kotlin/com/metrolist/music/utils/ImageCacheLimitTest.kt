package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCacheLimitTest {
    @Test fun unlimitedAndDisabledAreDistinctAndNormalLimitsStayInBytes() {
        assertEquals(Long.MAX_VALUE, imageCacheLimitBytes(-1))
        assertEquals(0L, imageCacheLimitBytes(0))
        assertEquals(512L * 1024 * 1024, imageCacheLimitBytes(512))
    }
}
