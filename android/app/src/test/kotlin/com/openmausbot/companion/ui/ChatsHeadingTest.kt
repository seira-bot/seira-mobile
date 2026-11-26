package com.openmausbot.companion.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class ChatsHeadingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun mount(
        computerName: String? = "Studio PC",
        workspaceName: String? = null,
        onAddComputer: () -> Unit = {},
        onComputer: () -> Unit = {},
    ) {
        compose.setContent {
            CompanionTheme(darkTheme = false) {
                ChatsHeading(
                    onUpdates = {},
                    onCalendar = {},
                    onSettings = {},
                    computerName = computerName,
                    onComputer = onComputer,
                    onAddComputer = onAddComputer,
                    workspaceName = workspaceName,
                )
            }
        }
    }

    @Test
    fun `the add button shows while a computer is paired`() {
        mount()
        compose.onNodeWithTag("home-add-computer").assertIsDisplayed()
    }

    @Test
    fun `no add button without a computer`() {
        mount(computerName = null)
        assertEquals(0, compose.onAllNodesWithTag("home-add-computer").fetchSemanticsNodes().size)
    }

    @Test
    fun `a tap on the add button adds a computer and does not open the sheet`() {
        var added = 0
        var opened = 0
        mount(onAddComputer = { added++ }, onComputer = { opened++ })
        compose.onNodeWithTag("home-add-computer").performClick()
        assertEquals(1, added)
        assertEquals(0, opened)
        compose.onNodeWithTag("home-computer").performClick()
        assertEquals(1, opened)
        assertEquals(1, added)
    }

    @Test
    fun `the add button is 48 dp and named`() {
        mount()
        val node = compose.onNodeWithTag("home-add-computer").assert(hasContentDescription("Add a computer"))
        val bounds = node.getBoundsInRoot()
        assertTrue(bounds.right - bounds.left >= 48.dp, "wide ${bounds.right - bounds.left}")
        assertTrue(bounds.bottom - bounds.top >= 48.dp, "high ${bounds.bottom - bounds.top}")
    }

    @Test
    fun `the title shows in full at 360 dp beside both chips`() {
        mount(computerName = "A very long computer name for a narrow phone", workspaceName = "Marketing and growth team")
        compose.onNodeWithText("Chats").assertIsDisplayed()
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Chats").fetchSemanticsNode().config
            .getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        val layout = results.single()
        assertEquals(1, layout.lineCount)
        assertEquals("Chats".length, layout.getLineEnd(0, visibleEnd = true))
        assertFalse(layout.isLineEllipsized(0))
        for (tag in listOf("home-add-computer", "home-more")) {
            val bounds = compose.onNodeWithTag(tag).getBoundsInRoot()
            assertTrue(bounds.right - bounds.left >= 48.dp, "$tag wide")
            assertTrue(bounds.right <= 360.dp, "$tag inside the screen")
        }
    }
}
