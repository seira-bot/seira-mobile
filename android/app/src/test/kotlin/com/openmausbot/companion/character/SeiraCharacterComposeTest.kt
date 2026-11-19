package com.openmausbot.companion.character

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.ui.CharacterMood
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeiraCharacterComposeTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the character draws and survives a mood change`() {
        var mood by mutableStateOf(CharacterMood.IDLE)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SeiraCharacter(
                hair = "bob", color = "blue", accessories = listOf("glasses"), faceInk = null,
                size = 120.dp, mood = mood, sway = true, talkLevel = { 0.5f }, contentDescription = "Seira",
            )
        }
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithContentDescription("Seira").assertHeightIsEqualTo(120.dp)
        mood = CharacterMood.HAPPY
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithContentDescription("Seira").assertHeightIsEqualTo(120.dp)
    }
}
