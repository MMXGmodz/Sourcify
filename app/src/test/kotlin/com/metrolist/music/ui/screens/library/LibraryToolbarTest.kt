package com.metrolist.music.ui.screens.library

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class LibraryToolbarTest {
    @Test
    fun `opening a search or create command consumes it exactly once`() {
        for (action in listOf(LIBRARY_SEARCH_ACTION, LIBRARY_CREATE_ACTION)) {
            val state = SavedStateHandle(mapOf(LIBRARY_TOOLBAR_ACTION to action))
            assertTrue(consumeLibraryToolbarAction(state, action))
            assertNull(state.get<String>(LIBRARY_TOOLBAR_ACTION))
            assertFalse(consumeLibraryToolbarAction(state, action))
        }
    }

    @Test
    fun `stale consumers cannot clear a newer toolbar command`() {
        val state = SavedStateHandle(mapOf(LIBRARY_TOOLBAR_ACTION to LIBRARY_CREATE_ACTION))
        assertFalse(consumeLibraryToolbarAction(state, LIBRARY_SEARCH_ACTION))
        assertTrue(consumeLibraryToolbarAction(state, LIBRARY_CREATE_ACTION))
    }
}
