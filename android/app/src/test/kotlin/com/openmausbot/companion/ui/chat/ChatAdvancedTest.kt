package com.openmausbot.companion.ui.chat

import com.openmausbot.companion.core.BotTask
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.ContextUsage
import com.openmausbot.companion.core.OptionCard
import com.openmausbot.companion.core.TaskUsage
import com.openmausbot.companion.ui.bot
import com.openmausbot.companion.ui.room
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatAdvancedTest {
    private val botChat = Chat.BotChat(bot())
    private val groupChat = Chat.RoomChat(room().copy(tasks = emptyList()))
    private val meter = ContextMeter(42, 84_000, 200_000)

    private fun task(context: ContextUsage?, turns: Int = 3) = BotTask(
        threadId = "thread-bot-1",
        title = "Weekly summary",
        createdAt = 1.0,
        usage = TaskUsage(input = 900, output = 100, turns = turns, context = context),
    )

    @Test
    fun `advanced off leaves the 1_1_0 menu and an empty chip row`() {
        for (chat in listOf(botChat, groupChat)) {
            assertEquals(ChatMenuRules.items(chat), ChatMenuRules.items(chat, advanced = false))
            assertEquals(emptyList(), ChatAdvanced.tools(chat, false, learnAllowed = true, goalRunning = false, context = meter))
        }
        val advancedOnly = setOf(
            ChatMenuItem.RENAME, ChatMenuItem.COPY_MARKDOWN, ChatMenuItem.DOWNLOAD_MARKDOWN, ChatMenuItem.ACTIVITY,
        )
        assertTrue(ChatMenuRules.items(botChat).none { it in advancedOnly })
    }

    @Test
    fun `advanced on adds rename, copy, download and activity to a bot chat`() {
        val base = ChatMenuRules.items(botChat)
        assertEquals(
            base + listOf(
                ChatMenuItem.RENAME, ChatMenuItem.COPY_MARKDOWN, ChatMenuItem.DOWNLOAD_MARKDOWN, ChatMenuItem.ACTIVITY,
            ),
            ChatMenuRules.items(botChat, advanced = true),
        )
    }

    @Test
    fun `a group gets only the rows that work for a group`() {
        val extra = ChatMenuRules.items(groupChat, advanced = true) - ChatMenuRules.items(groupChat).toSet()
        assertEquals(listOf(ChatMenuItem.COPY_MARKDOWN, ChatMenuItem.DOWNLOAD_MARKDOWN), extra)
    }

    @Test
    fun `the chips follow the gates, the running goal and the context numbers`() {
        assertEquals(
            listOf(ComposerTool.TEACH, ComposerTool.GOAL, ComposerTool.CONTEXT),
            ChatAdvanced.tools(botChat, true, learnAllowed = true, goalRunning = false, context = meter),
        )
        assertEquals(
            listOf(ComposerTool.GOAL),
            ChatAdvanced.tools(botChat, true, learnAllowed = false, goalRunning = false, context = null),
        )
        assertEquals(
            listOf(ComposerTool.CONTEXT),
            ChatAdvanced.tools(botChat, true, learnAllowed = false, goalRunning = true, context = meter),
        )
        assertEquals(
            listOf(ComposerTool.GOAL),
            ChatAdvanced.tools(groupChat, true, learnAllowed = false, goalRunning = false, context = meter),
        )
    }

    @Test
    fun `the context meter needs both tokens and a window`() {
        assertEquals(ContextMeter(42, 84_000, 200_000), ChatAdvanced.contextMeter(task(ContextUsage(84_000, 200_000))))
        assertNull(ChatAdvanced.contextMeter(task(ContextUsage(84_000, null))))
        assertNull(ChatAdvanced.contextMeter(task(ContextUsage(84_000, 0))))
        assertNull(ChatAdvanced.contextMeter(task(null)))
        assertNull(ChatAdvanced.contextMeter(null))
        assertEquals(100, ChatAdvanced.contextMeter(task(ContextUsage(300_000, 200_000)))?.percent)
    }

    @Test
    fun `counts read short`() {
        assertEquals("850", ChatAdvanced.compactCount(850))
        assertEquals("1.5k", ChatAdvanced.compactCount(1_500))
        assertEquals("2k", ChatAdvanced.compactCount(2_000))
        assertEquals("12k", ChatAdvanced.compactCount(12_400))
        assertEquals("200k", ChatAdvanced.compactCount(200_000))
        assertEquals("1.2M", ChatAdvanced.compactCount(1_200_000))
    }

    @Test
    fun `a chip writes its command first and keeps the typed words`() {
        assertEquals("/learn ", ChatAdvanced.insertion("", "/learn "))
        assertEquals("/goal ship it", ChatAdvanced.insertion("ship it", "/goal "))
        assertEquals("/goal ship it", ChatAdvanced.insertion("/goal ship it", "/goal "))
    }

    @Test
    fun `a downloaded file is named from the thread title`() {
        assertEquals("Weekly-summary.md", ChatAdvanced.markdownFileName("Weekly summary"))
        assertEquals("conversation.md", ChatAdvanced.markdownFileName("  "))
        assertEquals("conversation.md", ChatAdvanced.markdownFileName(null))
        assertEquals("a-b.md", ChatAdvanced.markdownFileName("../a/b"))
    }

    @Test
    fun `a rename is trimmed and cut to 80 characters`() {
        assertEquals("Plan", ChatAdvanced.renameTitle("  Plan "))
        assertEquals(80, ChatAdvanced.renameTitle("x".repeat(200)).length)
        assertFalse(ChatAdvanced.canSaveRename("   "))
        assertTrue(ChatAdvanced.canSaveRename("Plan"))
    }

    private fun card(allowSession: Boolean?, tool: String? = "Bash") = OptionCard(
        title = "Run a command?",
        subtitle = "git status",
        options = listOf("Allow once", "Deny", "Always allow"),
        requestId = "r1",
        tool = tool,
        requestType = "permission",
        allowSession = allowSession,
    )

    @Test
    fun `the session button shows only in advanced mode on a pending card that allows it`() {
        assertTrue(ApprovalButtons.offersSessionAllow(card(true), advancedMode = true))
        assertFalse(ApprovalButtons.offersSessionAllow(card(true), advancedMode = false))
        assertFalse(ApprovalButtons.offersSessionAllow(card(false), advancedMode = true))
        assertFalse(ApprovalButtons.offersSessionAllow(card(null), advancedMode = true))
        assertFalse(ApprovalButtons.offersSessionAllow(card(true, tool = null), advancedMode = true))
    }
}
