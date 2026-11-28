package bot.seira.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuickReplyContextTest {
    private fun text(id: String) = Message(id = id, role = Message.Role.BOT, kind = Message.Kind.TEXT, at = 1.0, text = "hello")

    private fun tool(id: String, name: String, notice: Boolean? = null) = Message(
        id = id,
        role = Message.Role.BOT,
        kind = Message.Kind.ACTIVITY,
        at = 1.0,
        tool = ToolActivity(name = name, ok = true, notice = notice),
    )

    private val titles = { replies: List<QuickReply> -> replies.map { it.title } }

    @Test
    fun `a conversation with no tool activity has no code activity`() {
        assertFalse(QuickReplyContext.hasCodeActivity(emptyList()))
        assertFalse(QuickReplyContext.hasCodeActivity(listOf(text("a"))))
    }

    @Test
    fun `a command or a file change counts as code activity`() {
        for (name in listOf("Bash", "Edit", "MultiEdit", "Write", "NotebookEdit", "apply_patch", "Ran a command", "Edited a file", "Wrote a file")) {
            assertTrue(QuickReplyContext.hasCodeActivity(listOf(tool("t", name))), name)
        }
        assertTrue(QuickReplyContext.hasCodeActivity(listOf(tool("t", "/bin/bash -lc 'git status'"))))
    }

    @Test
    fun `reading, searching, teammates and notices do not count`() {
        for (name in listOf("Read", "Glob", "WebSearch", "Searched the web", "Sent an email", "notice: Resumed after a restart")) {
            assertFalse(QuickReplyContext.hasCodeActivity(listOf(tool("t", name))), name)
        }
        assertFalse(QuickReplyContext.hasCodeActivity(listOf(tool("t", "Bash", notice = true))))
    }

    @Test
    fun `the stock chips hide Show diff and Run tests until there is code activity`() {
        val shown = QuickReplyContext.chips(QuickReply.DEFAULTS, codeActivity = false)
        assertEquals(listOf("Explain steps", "What's next?"), titles(shown))
    }

    @Test
    fun `the stock chips all show once there is code activity`() {
        assertEquals(QuickReply.DEFAULTS, QuickReplyContext.chips(QuickReply.DEFAULTS, codeActivity = true))
    }

    @Test
    fun `a chip the user wrote always shows`() {
        val mine = QuickReply(id = "mine", title = "Show diff", prompt = "Show the diff", icon = "diff")
        val saved = listOf(mine) + QuickReply.DEFAULTS
        assertEquals(mine, QuickReplyContext.chips(saved, codeActivity = false).first())
    }

    @Test
    fun `a stock chip the user edited counts as the user's own and always shows`() {
        val edited = QuickReply.DEFAULTS.first { it.id == "default.tests" }.copy(prompt = "Run the unit tests")
        val saved = QuickReply.DEFAULTS.map { if (it.id == edited.id) edited else it }
        assertTrue(edited in QuickReplyContext.chips(saved, codeActivity = false))
        assertFalse(QuickReply.DEFAULTS.first { it.id == "default.diff" } in QuickReplyContext.chips(saved, codeActivity = false))
    }

    @Test
    fun `an empty saved list stays empty`() {
        assertEquals(emptyList(), QuickReplyContext.chips(emptyList(), codeActivity = false))
    }

    @Test
    fun `each conversation is judged on its own messages`() {
        val coding = listOf(tool("a", "Edit"))
        val chatting = listOf(text("b"))
        assertTrue(QuickReplyContext.hasCodeActivity(coding))
        assertFalse(QuickReplyContext.hasCodeActivity(chatting))
    }
}
