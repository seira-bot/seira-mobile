package com.openmausbot.companion.ui

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.ModelSelection
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChangeLookSheetMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val bot = Bot(
        id = "b1",
        threadId = "thread-b1",
        name = "Dina Pratama",
        title = "research",
        description = "",
        notifications = true,
        color = "green",
        unread = false,
        modelSelection = ModelSelection("instance-1", "model-1"),
        createdAt = 0.0,
    )

    private var dismissed = 0

    private fun open() {
        val wiring = WiringScene()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalCompanion provides wiring.environment) {
                CompanionTheme(darkTheme = false) { ChangeLookSheet(bot = bot, onDismiss = { dismissed++ }) }
            }
        }
        compose.mainClock.advanceTimeBy(700)
    }

    private fun tapClose() {
        compose.onAllNodesWithContentDescription("Close").onLast().performClick()
    }

    @Test
    fun `the sheet slides out before it reports the dismiss`() {
        open()
        compose.onNodeWithText("Change look").assertIsDisplayed()
        tapClose()
        compose.mainClock.advanceTimeBy(50)
        assertEquals(0, dismissed)
        compose.mainClock.advanceTimeBy(600)
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1, dismissed)
    }

    @Test
    fun `a second tap on close while it slides out reports one dismiss`() {
        open()
        tapClose()
        compose.mainClock.advanceTimeBy(50)
        tapClose()
        compose.mainClock.advanceTimeBy(700)
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1, dismissed)
    }

    @Test
    fun `with animations removed the dismiss comes at once`() {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        open()
        tapClose()
        compose.mainClock.advanceTimeBy(100)
        assertEquals(1, dismissed)
    }
}
