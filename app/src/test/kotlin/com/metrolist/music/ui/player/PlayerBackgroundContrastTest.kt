package com.metrolist.music.ui.player
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.metrolist.music.ui.theme.readablePlayerBackdrop
import org.junit.Assert.*
import org.junit.Test
class PlayerBackgroundContrastTest {
 @Test fun brightArtworkCannotMakeWhitePlayerControlsUnreadable() {
  for (color in listOf(Color.White, Color.Yellow, Color.Cyan, Color.Red)) {
   val result = readablePlayerBackdrop(color)
   assertTrue("Background must support white text", 1.05f / (result.luminance() + .05f) >= 4.5f)
  }
  assertEquals(Color(0xFF102030), readablePlayerBackdrop(Color(0xFF102030)))
 }
}
