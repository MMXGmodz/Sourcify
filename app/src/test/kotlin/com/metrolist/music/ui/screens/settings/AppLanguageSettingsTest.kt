package com.metrolist.music.ui.screens.settings

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import com.metrolist.music.constants.AppLanguageKey
import com.metrolist.music.constants.SYSTEM_DEFAULT
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class AppLanguageSettingsTest {
    @Test
    fun legacyPickerPersistsLanguageBeforeRecreatingTheWrappedActivity() = runBlocking {
        val controller = Robolectric.buildActivity(LanguageActivity::class.java).setup()
        val activity = controller.get()
        val originalLanguage = activity.dataStore[AppLanguageKey]
        try {
            val wrappedContext = ContextWrapper(ContextWrapper(activity))
            applyLegacyAppLanguage(wrappedContext, "de")
            assertEquals("de", activity.languageAtRecreation)
            applyLegacyAppLanguage(wrappedContext, SYSTEM_DEFAULT)
            assertEquals(SYSTEM_DEFAULT, activity.languageAtRecreation)
            assertEquals(2, activity.recreations)
        } finally {
            activity.safeDataStoreEdit {
                if (originalLanguage == null) it.remove(AppLanguageKey)
                else it[AppLanguageKey] = originalLanguage
            }
            controller.pause().stop().destroy()
        }
    }

    class LanguageActivity : ComponentActivity() {
        var languageAtRecreation: String? = null
        var recreations = 0
        override fun recreate() {
            languageAtRecreation = dataStore[AppLanguageKey]
            recreations++
        }
    }
}
