package com.openmausbot.companion.ui

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.Fleet
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.OptionCard
import com.openmausbot.companion.core.SkillRequestCardData
import com.openmausbot.companion.core.ToolActivity
import com.openmausbot.companion.core.TranscriptCard
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The chat screen's parts, held to the approved design: the header's three
 * controls, the user bubble's width, the bot reply with no bubble, the activity
 * pill, the table card, the approval buttons, the stream caret and the + sheet.
 * Each part is mounted on its own, on a 393 dp phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class ChatDesignTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>(
        effectContext = object : MotionDurationScale { override val scaleFactor = 0f },
    )

    private val bot: Bot = bot()
    private val chat = Chat.BotChat(bot)
    private val hash = "a".repeat(64)

    @Before
    fun reduceMotion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    // The header

    @Test
    fun `the header has a back button, the name pill and a call button`() {
        showHeader(ChatCallAction.START)
        compose.onNodeWithContentDescription("Back").assertExists()
        compose.onNodeWithContentDescription("Open ${bot.name} settings").assertExists()
        compose.onNodeWithContentDescription("Start live call").assertExists()
        compose.onNodeWithText(bot.name, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the call button opens a call that is already running`() {
        showHeader(ChatCallAction.OPEN)
        compose.onNodeWithContentDescription("Open live call").assertExists()
        assertEquals(0, compose.onAllNodesWithContentDescription("Start live call").fetchSemanticsNodes().size)
    }

    @Test
    fun `the header offers no call where a call is not possible`() {
        showHeader(ChatCallAction.NONE)
        compose.onNodeWithContentDescription("Back").assertExists()
        assertEquals(0, compose.onAllNodesWithContentDescription("Start live call").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithContentDescription("Open live call").fetchSemanticsNodes().size)
    }

    @Test
    fun `the header controls are at least 48 dp square`() {
        showHeader(ChatCallAction.START)
        listOf("Back", "Start live call", "Open ${bot.name} settings").forEach { description ->
            val bounds = compose.onNodeWithContentDescription(description).getBoundsInRoot()
            assertTrue(bounds.width >= 48.dp && bounds.height >= 48.dp, "$description is ${bounds.width} by ${bounds.height}")
        }
    }

    // The transcript

    @Test
    fun `a user bubble stops at 280 dp`() {
        val message = Message("u", Message.Role.USER, Message.Kind.TEXT, 1.0, text = LONG)
        showRow(message)
        val width = compose.onNodeWithText(LONG, useUnmergedTree = true).getBoundsInRoot().width
        assertTrue(width <= USER_BUBBLE_MAX_WIDTH, "the bubble text is $width wide")
        assertTrue(width > 120.dp, "the long line still uses the width it has: $width")
    }

    @Test
    fun `a bot reply has no bubble and uses the full width`() {
        val message = Message("b", Message.Role.BOT, Message.Kind.TEXT, 1.0, text = LONG)
        showRow(message)
        val width = compose.onNodeWithText(LONG, useUnmergedTree = true).getBoundsInRoot().width
        assertTrue(width > USER_BUBBLE_MAX_WIDTH, "the reply is $width wide, wider than a bubble")
    }

    @Test
    fun `the activity pill opens and closes and its chevron stays`() {
        val items = listOf("Read invoices", "Check totals").mapIndexed { index, name ->
            Message("t$index", Message.Role.BOT, Message.Kind.ACTIVITY, index.toDouble(), tool = ToolActivity(name = name, ok = true))
        }
        show { ActivityRunChip(items = items, bots = listOf(bot)) }
        compose.onNodeWithContentDescription("Ran 2 steps, collapsed").assertExists()
        compose.onNodeWithTag(ACTIVITY_CHEVRON_TAG, useUnmergedTree = true).assertExists()

        compose.onNodeWithContentDescription("Ran 2 steps, collapsed").performClick()
        compose.onNodeWithContentDescription("Ran 2 steps, expanded").assertExists()
        compose.onNodeWithTag(ACTIVITY_CHEVRON_TAG, useUnmergedTree = true).assertExists()

        compose.onNodeWithContentDescription("Ran 2 steps, expanded").performClick()
        compose.onNodeWithContentDescription("Ran 2 steps, collapsed").assertExists()
    }

    @Test
    fun `the table card shows its rows, its count and a copy button`() {
        val card = TranscriptCard.Table(
            headers = listOf("invoice", "total"),
            rows = listOf(listOf("INV-1042", "1.250.000"), listOf("INV-1043", "480.000")),
        )
        show { DataTableCard(card) }
        compose.onNodeWithText("INV-1042").assertExists()
        compose.onNodeWithText("480.000").assertExists()
        compose.onNodeWithText("2 rows").assertExists()
        compose.onNodeWithText("Copy CSV").assertExists()
    }

    // The approval

    @Test
    fun `an approval has Allow and Deny, each 48 dp tall`() {
        showApproval(skill = null)
        compose.onNodeWithText("Allow").assertIsEnabled()
        compose.onNodeWithText("Deny").assertIsEnabled()
        listOf("Allow", "Deny").forEach { label ->
            val height = compose.onNodeWithText(label).getBoundsInRoot().height
            assertTrue(height >= 47.9.dp, "$label is $height tall")
        }
    }

    @Test
    fun `Allow stays off for a skill request the person could not read in full`() {
        showApproval(skill = skillRequest(sha256 = null, preview = null))
        compose.onNodeWithText("Allow").assertIsNotEnabled()
        compose.onNodeWithText("Deny").assertIsEnabled()
    }

    @Test
    fun `Allow comes on once the skill request carries its full review`() {
        showApproval(skill = skillRequest(sha256 = hash, preview = "Reads the invoice folder."))
        compose.onNodeWithText("Allow").assertIsEnabled()
    }

    // The stream

    @Test
    fun `a reply that is still arriving ends in a caret`() {
        show { StreamingBubble(text = "Checking the invoices for Dina Pratama", reasoning = null, bot = bot) }
        compose.onNodeWithTag(STREAM_CARET_TAG, useUnmergedTree = true).assertExists()
    }

    // The + sheet

    @Test
    fun `the sheet offers Camera, Photos and Files, then the live card and the rest`() {
        showSheet(ChatActions.sheet(chat, hasPendingApproval = false, canAddAttachment = true, hasCamera = true, offersCall = true, offersSchedule = true))
        listOf("Add to chat", "Camera", "Photos", "Files", "Live call", "Schedule", "Threads").forEach { title ->
            assertTrue(
                compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty(),
                "the sheet shows $title",
            )
        }
    }

    @Test
    fun `without a camera the sheet does not offer Camera`() {
        showSheet(ChatActions.sheet(chat, hasPendingApproval = false, canAddAttachment = true, hasCamera = false))
        assertEquals(0, compose.onAllNodesWithText("Camera").fetchSemanticsNodes().size)
        assertTrue(compose.onAllNodesWithText("Photos").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `a tap on a sheet item reports its action`() {
        val taken = mutableListOf<ChatActionId>()
        showSheet(
            ChatActions.sheet(chat, hasPendingApproval = false, canAddAttachment = true, hasCamera = true, offersCall = true, offersSchedule = true),
            onAction = { taken += it },
        )
        compose.onNodeWithText("Camera").performClick()
        compose.onNodeWithText("Live call").performClick()
        compose.onNodeWithText("Schedule").performClick()
        assertEquals(listOf(ChatActionId.CAMERA, ChatActionId.LIVE_CALL, ChatActionId.SCHEDULE), taken)
    }

    @Test
    fun `the sheet's close button is at least 48 dp square`() {
        showSheet(ChatActions.sheet(chat, hasPendingApproval = false, canAddAttachment = true))
        val close = compose.onAllNodesWithContentDescription("Close")[1].getBoundsInRoot()
        assertTrue(close.width >= 48.dp && close.height >= 48.dp, "Close is ${close.width} by ${close.height}")
    }

    // Scaffolding

    private fun showHeader(call: ChatCallAction) = show {
        ChatHeader(
            chat = chat,
            face = MausState.IDLE,
            status = ChatStatus.IDLE,
            unreadElsewhere = 0,
            call = call,
            onBack = {},
            onCall = {},
            onOpenPill = {},
            onHeight = {},
        )
    }

    private fun showRow(message: Message) = show {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { MessageRow(chat, message) }
    }

    private fun showApproval(skill: SkillRequestCardData?) {
        val card = OptionCard(
            title = "Approval needed",
            subtitle = "Send the March invoices to Dina Pratama",
            options = listOf("Allow", "Deny"),
            requestId = "p",
            tool = "Bash",
            requestType = "permission",
            skillRequest = skill,
        )
        showRow(Message("q", Message.Role.BOT, Message.Kind.OPTIONS, 1.0, card = card))
    }

    private fun skillRequest(sha256: String?, preview: String?) = SkillRequestCardData(
        version = 1,
        requestId = "p",
        botId = bot.id,
        threadId = bot.threadId,
        stagedId = "staged-1",
        action = "create",
        name = "Invoice reminder",
        gist = "Reminds a customer about an open invoice.",
        preview = preview,
        sha256 = sha256,
        createdAt = 1L,
    )

    private fun showSheet(actions: List<ChatAction>, onAction: (ChatActionId) -> Unit = {}) = show {
        Box(Modifier.fillMaxSize()) {
            ChatPlusSheet(open = true, chat = chat, actions = actions, onDismiss = {}, onAction = onAction)
        }
    }

    private fun show(content: @Composable () -> Unit) {
        val wiring = WiringScene(fleet = Fleet(listOf(bot), emptyList()))
        compose.setContent {
            CompositionLocalProvider(LocalCompanion provides wiring.environment) {
                CompanionTheme(darkTheme = false) { content() }
            }
        }
        compose.waitForIdle()
    }

    private companion object {
        const val LONG = "The March invoices for Dina Pratama are ready. Two of them are still open, " +
            "and the larger one is due on the fifth, so it is worth sending the reminder today."
    }
}
