package bot.seira.companion.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import bot.seira.companion.core.Bot
import bot.seira.companion.core.Chat
import bot.seira.companion.core.ChatApp
import bot.seira.companion.core.ChatAppMcp
import bot.seira.companion.core.Fleet
import bot.seira.companion.core.Message
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatAppRowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val bot: Bot = bot()
    private val chat = Chat.BotChat(bot)

    private fun app(
        source: String = "html",
        doc: String = "d".repeat(64),
        blocked: String? = null,
        live: Boolean? = null,
        mcp: ChatAppMcp? = null,
    ) = ChatApp(source = source, title = "Coffee counter", doc = doc, blocked = blocked, live = live, mcp = mcp)

    private fun row(app: ChatApp?, text: String? = "[App] Coffee counter\nThe counter shows 3 cups.") =
        Message("app-1", Message.Role.BOT, Message.Kind.APP, 1.0, text = text, app = app)

    private fun show(message: Message) {
        val wiring = WiringScene(fleet = Fleet(listOf(bot), emptyList()))
        compose.setContent {
            CompositionLocalProvider(LocalCompanion provides wiring.environment) {
                CompanionTheme(darkTheme = false) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { MessageRow(chat, message) }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertNoOpen() = assertTrue(compose.onAllNodesWithTagCount(CHAT_APP_OPEN_TAG) == 0)

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    @Test
    fun aStoredHtmlPageShowsItsTitleItsTextAndOpen() {
        show(row(app()))
        compose.onNodeWithText("Coffee counter").assertIsDisplayed()
        compose.onNodeWithText("The counter shows 3 cups.").assertIsDisplayed()
        compose.onNodeWithTag(CHAT_APP_OPEN_TAG).assertIsDisplayed()
        compose.onNodeWithText("Open").assertIsDisplayed()
    }

    @Test
    fun tappingOpenShowsThePageScreenWithTheTitleAndAClose() {
        show(row(app()))
        compose.onNodeWithTag(CHAT_APP_OPEN_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Close").assertExists()
        compose.onNodeWithTag(CHAT_APP_ERROR_TAG).assertExists()
    }

    @Test
    fun aMissingDocumentShowsTheTextAndNoOpen() {
        show(row(app(doc = "")))
        compose.onNodeWithText("The counter shows 3 cups.", substring = true).assertIsDisplayed()
        assertNoOpen()
    }

    @Test
    fun aBlockedPageShowsTheTextAndNoOpen() {
        show(row(app(doc = "", blocked = "too-large"), text = "[App] Huge map\nA map too large to keep."))
        compose.onNodeWithText("A map too large to keep.", substring = true).assertIsDisplayed()
        assertNoOpen()
    }

    @Test
    fun anMcpAppShowsTheTextAndNoOpen() {
        show(row(app(source = "mcp", mcp = ChatAppMcp(server = "basic", tool = "get-time")), text = "Basic Server · Get Time\n2026-10-14"))
        compose.onNodeWithText("2026-10-14", substring = true).assertIsDisplayed()
        assertNoOpen()
    }

    @Test
    fun aFolderPageThatReloadsShowsTheTextAndNoOpen() {
        show(row(app(live = true)))
        compose.onNodeWithText("The counter shows 3 cups.", substring = true).assertIsDisplayed()
        assertNoOpen()
    }

    @Test
    fun anUnreadablePayloadShowsTheTextAndNoOpen() {
        show(row(null, text = "An app this build cannot read"))
        compose.onNodeWithText("An app this build cannot read").assertIsDisplayed()
        assertNoOpen()
    }
}
