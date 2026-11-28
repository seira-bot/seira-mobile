package bot.seira.companion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import bot.seira.companion.core.Session
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmptyStatesTest {
    @get:Rule val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    @Composable
    private fun Roster(empty: RosterEmpty, refreshing: Boolean = false) {
        CompanionTheme(darkTheme = false) {
            RosterEmptyState(
                empty = empty,
                refreshing = refreshing,
                onNewChat = { calls += "new" },
                onPairComputer = { calls += "pair" },
                onRefresh = { calls += "refresh" },
            )
        }
    }

    private fun primaryButton() =
        compose.onNode(hasClickAction() and hasAnyAncestor(hasTestTag(EMPTY_PRIMARY_TAG)).or(hasTestTag(EMPTY_PRIMARY_TAG)))

    private fun hasArt(name: String) =
        compose.onAllNodesWithTag("icon3d.$name", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the state follows the connection and the phone's internet`() {
        val offline = Session.Status.Offline("No route")
        assertEquals(RosterEmpty.CHATS, rosterEmpty(Session.Status.Live, phoneOnline = true))
        assertEquals(RosterEmpty.CHATS, rosterEmpty(Session.Status.Live, phoneOnline = false))
        assertEquals(RosterEmpty.CHATS, rosterEmpty(Session.Status.Connecting, phoneOnline = false))
        assertEquals(RosterEmpty.NOT_CONNECTED, rosterEmpty(offline, phoneOnline = true))
        assertEquals(RosterEmpty.OFFLINE, rosterEmpty(offline, phoneOnline = false))
    }

    @Test
    fun `no chats offers a new chat`() {
        compose.setContent { Roster(RosterEmpty.CHATS) }
        compose.onNodeWithText("No chats yet").assertIsDisplayed()
        compose.onNodeWithText("Ask Seira anything. Your chats show up here.").assertIsDisplayed()
        assertTrue(hasArt("empty_chats"))
        compose.onNodeWithTag(EMPTY_SECONDARY_TAG).assertDoesNotExist()

        compose.onNodeWithTag(EMPTY_PRIMARY_TAG).assertIsDisplayed().performClick()
        assertEquals(listOf("new"), calls)
    }

    @Test
    fun `a computer that is not connected offers pairing and a retry`() {
        compose.setContent { Roster(RosterEmpty.NOT_CONNECTED) }
        compose.onNodeWithText("Your computer is not connected").assertIsDisplayed()
        compose.onNodeWithText("Open Seira on your computer. Your chats come back when it connects.").assertIsDisplayed()
        assertTrue(hasArt("not_connected"))

        primaryButton().assertTextContains("Pair a computer").performClick()
        compose.onNodeWithTag(EMPTY_SECONDARY_TAG).assertTextContains("Try again").performClick()
        assertEquals(listOf("pair", "refresh"), calls)
    }

    @Test
    fun `an offline phone offers a retry only`() {
        compose.setContent { Roster(RosterEmpty.OFFLINE) }
        compose.onNodeWithText("You are offline").assertIsDisplayed()
        compose.onNodeWithText("Check your internet connection. Seira picks up where you left off.").assertIsDisplayed()
        assertTrue(hasArt("offline"))
        compose.onNodeWithTag(EMPTY_SECONDARY_TAG).assertDoesNotExist()

        primaryButton().assertTextContains("Try again").performClick()
        assertEquals(listOf("refresh"), calls)
    }

    @Test
    fun `the second button waits while a refresh runs`() {
        compose.setContent { Roster(RosterEmpty.NOT_CONNECTED, refreshing = true) }
        compose.onNodeWithTag(EMPTY_SECONDARY_TAG).assertIsNotEnabled()
        primaryButton().assertIsEnabled()
    }

    @Test
    fun `the offline retry waits while a refresh runs`() {
        compose.setContent { Roster(RosterEmpty.OFFLINE, refreshing = true) }
        primaryButton().assertIsNotEnabled()
    }

    @Test
    fun `the Updates sheet with nothing to show closes from Back to chats`() {
        val scene = WiringScene()
        var dismissed = 0
        compose.setContent {
            CompositionLocalProvider(LocalCompanion provides scene.environment) {
                CompanionTheme(darkTheme = false) { UpdatesSheet(onOpen = {}, onDismiss = { dismissed++ }) }
            }
        }
        compose.onNodeWithText("All caught up").assertIsDisplayed()
        compose.onNodeWithTag(EMPTY_PRIMARY_TAG).performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `the empty Updates sheet says all caught up and goes back`() {
        compose.setContent { CompanionTheme(darkTheme = false) { UpdatesEmptyState(onBack = { calls += "back" }) } }
        compose.onNodeWithText("All caught up").assertIsDisplayed()
        compose.onNodeWithText("Nothing waits for your answer right now.").assertIsDisplayed()
        assertTrue(hasArt("all_done"))

        primaryButton().assertTextContains("Back to chats").performClick()
        assertEquals(listOf("back"), calls)
    }
}
