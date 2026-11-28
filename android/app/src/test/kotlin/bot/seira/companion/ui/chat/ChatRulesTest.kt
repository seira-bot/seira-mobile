package bot.seira.companion.ui.chat

import bot.seira.companion.core.ActivityDetail
import bot.seira.companion.core.Chat
import bot.seira.companion.core.Message
import bot.seira.companion.core.OptionCard
import bot.seira.companion.core.ToolActivity
import bot.seira.companion.core.TranscriptRow
import bot.seira.companion.ui.bot
import bot.seira.companion.ui.room
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun text(id: String, body: String, at: Double = 1.0, role: Message.Role = Message.Role.BOT) =
    Message(id, role, Message.Kind.TEXT, at, text = body)

private fun step(id: String, at: Double, ok: Boolean? = true) =
    Message(id, Message.Role.BOT, Message.Kind.ACTIVITY, at, tool = ToolActivity(name = id, ok = ok))

class ChatFindRulesTest {
    private val messages = listOf(
        text("a", "Send the March invoices"),
        text("b", "Done"),
        text("c", "The march totals are ready", role = Message.Role.USER),
    )

    @Test
    fun `a match ignores case and keeps the order of the thread`() {
        assertEquals(listOf("a", "c"), ChatFindRules.hits(messages, "MARCH"))
    }

    @Test
    fun `a blank search finds nothing`() {
        assertEquals(emptyList(), ChatFindRules.hits(messages, ""))
        assertEquals(emptyList(), ChatFindRules.hits(messages, "   "))
    }

    @Test
    fun `a message with no text is never a match`() {
        val withTool = messages + step("t", 5.0)
        assertEquals(listOf("a", "c"), ChatFindRules.hits(withTool, "march"))
    }

    @Test
    fun `stepping wraps past both ends`() {
        assertEquals(1, ChatFindRules.step(0, 3, forward = true))
        assertEquals(0, ChatFindRules.step(2, 3, forward = true))
        assertEquals(2, ChatFindRules.step(0, 3, forward = false))
        assertEquals(1, ChatFindRules.step(2, 3, forward = false))
    }

    @Test
    fun `stepping with no match, or from no position, lands sensibly`() {
        assertEquals(-1, ChatFindRules.step(0, 0, forward = true))
        assertEquals(0, ChatFindRules.step(-1, 3, forward = true))
        assertEquals(2, ChatFindRules.step(-1, 3, forward = false))
    }
}

class ChatDaySeparatorsTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-11-26T12:00:00Z").toEpochMilli()

    private fun row(id: String, iso: String) =
        TranscriptRow.Single(text(id, "x", at = Instant.parse(iso).toEpochMilli().toDouble()))

    @Test
    fun `the first row carries a label and names today, yesterday or a date`() {
        val rows = listOf(
            row("a", "2026-11-26T08:00:00Z"),
            row("b", "2026-11-25T08:00:00Z"),
            row("c", "2026-11-01T08:00:00Z"),
        )
        assertEquals(DayKind.TODAY, ChatDaySeparators.label(rows.take(1), 0, now, zone)?.kind)
        assertEquals(DayKind.YESTERDAY, ChatDaySeparators.label(rows.drop(1).take(1), 0, now, zone)?.kind)
        assertEquals(DayKind.DATE, ChatDaySeparators.label(rows.drop(2), 0, now, zone)?.kind)
    }

    @Test
    fun `a row on the same day as the one before has no label`() {
        val rows = listOf(row("a", "2026-11-26T08:00:00Z"), row("b", "2026-11-26T09:30:00Z"))
        assertNull(ChatDaySeparators.label(rows, 1, now, zone))
    }

    @Test
    fun `a new day gets a label even a minute after midnight`() {
        val rows = listOf(row("a", "2026-11-25T23:59:00Z"), row("b", "2026-11-26T00:01:00Z"))
        assertEquals(DayKind.TODAY, ChatDaySeparators.label(rows, 1, now, zone)?.kind)
    }

    @Test
    fun `an index outside the list has no label`() {
        assertNull(ChatDaySeparators.label(emptyList(), 0, now, zone))
    }
}

class ReplyQuoteTest {
    @Test
    fun `every line of the replied text starts with a quote mark`() {
        assertEquals("> one\n> two", ReplyQuote.quote("one\ntwo"))
    }

    @Test
    fun `the quote is the first 280 characters`() {
        val long = "a".repeat(400)
        assertEquals("> " + "a".repeat(280), ReplyQuote.quote(long))
    }

    @Test
    fun `a cut never leaves half of a surrogate pair`() {
        val text = "a".repeat(279) + "😀" + "tail"
        val quoted = ReplyQuote.quote(text)
        assertEquals("> " + "a".repeat(279), quoted)
    }

    @Test
    fun `the person's words follow after one blank line`() {
        assertEquals("> Send it\n\nYes, now", ReplyQuote.wrap("Send it", "Yes, now"))
    }

    @Test
    fun `line endings are normalised`() {
        assertEquals("> one\n> two", ReplyQuote.quote("one\r\ntwo"))
    }

    @Test
    fun `with nothing to quote the text goes out unchanged`() {
        assertEquals("Yes", ReplyQuote.wrap(null, "Yes"))
        assertEquals("Yes", ReplyQuote.wrap("  ", "Yes"))
    }

    @Test
    fun `the target keeps the first line that has words`() {
        assertEquals("Hello", ReplyTarget("m", "Scout", "\n\nHello\nWorld").firstLine)
    }
}

class ChatMenuRulesTest {
    private val botChat = Chat.BotChat(bot())

    @Test
    fun `a bot has a model and a screen but no members`() {
        val items = ChatMenuRules.items(botChat)
        assertEquals(
            listOf(
                ChatMenuItem.FIND, ChatMenuItem.NEW_THREAD, ChatMenuItem.ALL_THREADS, ChatMenuItem.MODEL,
                ChatMenuItem.BOT_SCREEN, ChatMenuItem.SHARE_MARKDOWN, ChatMenuItem.SHARE_JSON,
            ),
            items,
        )
        assertFalse(ChatMenuItem.MEMBERS in items)
    }

    @Test
    fun `a group lists its members and has no model or screen`() {
        val items = ChatMenuRules.items(Chat.RoomChat(room().copy(tasks = emptyList())))
        assertTrue(ChatMenuItem.MEMBERS in items)
        assertTrue(ChatMenuItem.ALL_THREADS in items)
        assertFalse(ChatMenuItem.MODEL in items)
        assertFalse(ChatMenuItem.BOT_SCREEN in items)
    }

    @Test
    fun `a group without threads offers no thread items`() {
        val items = ChatMenuRules.items(Chat.RoomChat(room()))
        assertFalse(ChatMenuItem.NEW_THREAD in items)
        assertFalse(ChatMenuItem.ALL_THREADS in items)
        assertTrue(ChatMenuItem.FIND in items)
    }

    @Test
    fun `only a bot has a screen button`() {
        assertTrue(ChatMenuRules.showsScreenButton(botChat))
        assertFalse(ChatMenuRules.showsScreenButton(Chat.RoomChat(room())))
    }

    @Test
    fun `no menu item starts a conversation over or regenerates`() {
        val names = ChatMenuItem.entries.map { it.name }
        assertTrue(names.none { it.contains("FRESH") || it.contains("REGENERATE") || it.contains("WORKFLOW") })
    }
}

class WorkFoldTest {
    @Test
    fun `a short run reads as worked, a long one as seconds or minutes`() {
        assertEquals(WorkDuration.Instant, WorkFold.duration(400.0))
        assertEquals(WorkDuration.Seconds(21), WorkFold.duration(21_400.0))
        assertEquals(WorkDuration.Minutes(2, 5), WorkFold.duration(125_000.0))
        assertEquals(WorkDuration.Instant, WorkFold.duration(Double.NaN))
    }

    @Test
    fun `the elapsed time of a run is first step to last`() {
        assertEquals(0.0, WorkFold.runElapsed(listOf(step("a", 1000.0))))
        assertEquals(20_000.0, WorkFold.runElapsed(listOf(step("a", 1000.0), step("b", 11_000.0), step("c", 21_000.0))))
    }

    @Test
    fun `only Full starts open`() {
        assertTrue(WorkFold.startsOpen(ActivityDetail.FULL))
        assertFalse(WorkFold.startsOpen(ActivityDetail.REDUCED))
        assertFalse(WorkFold.startsOpen(ActivityDetail.HIDDEN))
    }

    @Test
    fun `two finished steps in a row fold into one run`() {
        val rows = listOf(step("a", 1.0), step("b", 2.0)).map(TranscriptRow::Single)
        val folded = WorkFold.foldSteps(rows)
        assertEquals(1, folded.size)
        assertEquals(2, (folded.single() as TranscriptRow.ActivityRun).items.size)
    }

    @Test
    fun `a lone step stays a lone row`() {
        val folded = WorkFold.foldSteps(listOf(TranscriptRow.Single(step("a", 1.0))))
        assertTrue(folded.single() is TranscriptRow.Single)
    }

    @Test
    fun `a failure and a reply split the run`() {
        val rows = listOf(
            TranscriptRow.Single(step("a", 1.0)),
            TranscriptRow.Single(step("b", 2.0)),
            TranscriptRow.Single(step("bad", 3.0, ok = false)),
            TranscriptRow.Single(text("t", "Done", at = 4.0)),
            TranscriptRow.Single(step("c", 5.0)),
        )
        val folded = WorkFold.foldSteps(rows)
        assertEquals(4, folded.size)
        assertTrue(folded[0] is TranscriptRow.ActivityRun)
        assertEquals("bad", (folded[1] as TranscriptRow.Single).message.id)
        assertEquals("t", (folded[2] as TranscriptRow.Single).message.id)
        assertEquals("c", (folded[3] as TranscriptRow.Single).message.id)
    }
}

class ApprovalButtonsTest {
    private val botChat = Chat.BotChat(bot())

    private fun permission(vararg options: String, allowKey: String? = null) = OptionCard(
        title = "Run a command",
        subtitle = "ls",
        options = options.toList(),
        requestId = "r1",
        tool = "Bash",
        requestType = "permission",
        allowKey = allowKey,
    )

    @Test
    fun `Allow becomes the filled Allow once and Deny stays outlined`() {
        val layout = ApprovalButtons.layout(permission("Allow", "Deny"), botChat)
        assertEquals(
            listOf(
                ApprovalButtonSpec("Allow", ApprovalLabel.ALLOW_ONCE, ApprovalLook.FILLED),
                ApprovalButtonSpec("Deny", ApprovalLabel.DENY, ApprovalLook.OUTLINED),
            ),
            layout,
        )
    }

    @Test
    fun `Always allow leaves the row when the text button takes it`() {
        val card = permission("Allow", "Always allow", "Deny", allowKey = "Bash:ls")
        val layout = ApprovalButtons.layout(card, botChat)
        assertEquals(listOf("Allow", "Deny"), layout.map { it.option })
    }

    @Test
    fun `Always allow stays in the row when it is the only way to say yes`() {
        val card = permission("Always allow", "Deny", allowKey = "Bash:ls")
        val layout = ApprovalButtons.layout(card, botChat)
        assertEquals(listOf("Always allow", "Deny"), layout.map { it.option })
        assertEquals(ApprovalLook.FILLED, layout.first().look)
    }

    @Test
    fun `a card of a group keeps its own Always allow option`() {
        val card = permission("Allow", "Always allow", "Deny", allowKey = "Bash:ls")
        val layout = ApprovalButtons.layout(card, Chat.RoomChat(room()))
        assertEquals(listOf("Allow", "Always allow", "Deny"), layout.map { it.option })
    }

    @Test
    fun `a question lists every answer as an outlined button`() {
        val card = permission("Yes, send it", "No, wait").copy(requestType = "question")
        val layout = ApprovalButtons.layout(card, botChat)
        assertTrue(layout.all { it.look == ApprovalLook.OUTLINED && it.label == ApprovalLabel.OWN })
        assertFalse(ApprovalButtons.isApproval(card))
    }

    @Test
    fun `a permission card is an approval`() {
        assertTrue(ApprovalButtons.isApproval(permission("Allow", "Deny")))
    }
}

class MessageActionRulesTest {
    @Test
    fun `a finished bot reply has the action row`() {
        assertTrue(MessageActionRules.showsRow(text("a", "Done")))
    }

    @Test
    fun `your own message has no action row`() {
        assertFalse(MessageActionRules.showsRow(text("a", "Hi", role = Message.Role.USER)))
    }

    @Test
    fun `narration inside a running turn waits for the turn to end`() {
        val running = text("a", "Looking").copy(turnId = "turn-1", turnTerminal = false)
        val ended = running.copy(turnTerminal = true)
        assertFalse(MessageActionRules.showsRow(running))
        assertTrue(MessageActionRules.showsRow(ended))
    }

    @Test
    fun `a step has no action row`() {
        assertFalse(MessageActionRules.showsRow(step("a", 1.0)))
    }

    @Test
    fun `members of a group come in the group's own order and skip missing bots`() {
        val first = bot(id = "bot-1", name = "Scout")
        val second = bot(id = "bot-2", name = "Nova")
        val group = room().copy(memberIds = listOf("bot-2", "ghost", "bot-1"))
        val members = ChatMembers.of(group, listOf(first, second))
        assertEquals(listOf("bot-2", "bot-1"), members.map { it.id })
        assertNotNull(members.firstOrNull())
    }
}

class ReplyOutgoingTest {
    @Test
    fun `a reply with attachments only sends the quote as its text`() {
        assertEquals("> Send it", ReplyQuote.outgoing("Send it", ""))
        assertEquals("> Send it", ReplyQuote.outgoing("Send it", "   "))
    }

    @Test
    fun `a reply with words quotes them`() {
        assertEquals("> Send it\n\nYes, now", ReplyQuote.outgoing("Send it", "Yes, now"))
    }

    @Test
    fun `a slash line stays a bare command even in a reply`() {
        assertEquals("/learn how we do refunds", ReplyQuote.outgoing("Send it", "/learn how we do refunds"))
    }

    @Test
    fun `with no reply the text is unchanged`() {
        assertEquals("", ReplyQuote.outgoing(null, ""))
        assertEquals("Yes", ReplyQuote.outgoing(null, "Yes"))
    }
}

class ChatFindShownTest {
    @Test
    fun `search covers only the messages the rows show`() {
        val shown = text("a", "March invoices")
        val hidden = text("b", "March totals, hidden by the activity setting")
        val rows = listOf<TranscriptRow>(TranscriptRow.Single(shown))
        val hits = ChatFindRules.hits(ChatFindRules.shownMessages(rows), "march")
        assertEquals(listOf("a"), hits)
        assertEquals(listOf("a", "b"), ChatFindRules.hits(listOf(shown, hidden), "march"))
    }

    @Test
    fun `folded messages count because opening the fold shows them`() {
        val rows = listOf<TranscriptRow>(
            TranscriptRow.ActivityRun(listOf(step("s1", 1.0), step("s2", 2.0))),
            TranscriptRow.AssistantTurn("turn", listOf(text("n1", "March narration")), 10.0),
        )
        assertEquals(listOf("s1", "s2", "n1"), ChatFindRules.shownMessages(rows).map { it.id })
        assertEquals(listOf("n1"), ChatFindRules.hits(ChatFindRules.shownMessages(rows), "march"))
    }
}

class NewThreadReasonTest {
    @Test
    fun `an enabled New thread has no reason`() {
        assertNull(ChatMenuRules.newThreadReason(canCreate = true, creating = false, decisionWaiting = false))
    }

    @Test
    fun `a disabled New thread always says why`() {
        assertNotNull(ChatMenuRules.newThreadReason(canCreate = false, creating = false, decisionWaiting = false))
        assertNotNull(ChatMenuRules.newThreadReason(canCreate = true, creating = true, decisionWaiting = false))
        assertNotNull(ChatMenuRules.newThreadReason(canCreate = true, creating = false, decisionWaiting = true))
    }
}

class AlwaysAllowOnceTest {
    private val botChat = Chat.BotChat(bot())

    private fun permission(vararg options: String, requestType: String = "permission") = OptionCard(
        title = "Run a command", subtitle = "ls", options = options.toList(), requestId = "r1",
        tool = "Bash", requestType = requestType, allowKey = "Bash:ls",
    )

    @Test
    fun `the button replaces the option when another way to say yes remains`() {
        val card = permission("Allow", "Always allow", "Deny")
        assertTrue(ApprovalButtons.movesAlways(card, botChat))
        assertEquals(listOf("Allow", "Deny"), ApprovalButtons.layout(card, botChat).map { it.option })
    }

    @Test
    fun `the option stays and the button hides when it is the only yes`() {
        val card = permission("Always allow", "Deny")
        assertFalse(ApprovalButtons.movesAlways(card, botChat))
        assertEquals(listOf("Always allow", "Deny"), ApprovalButtons.layout(card, botChat).map { it.option })
    }

    @Test
    fun `a stacked card keeps its own Always allow option`() {
        val card = permission("Allow", "Always allow", "Deny", requestType = "question")
        assertFalse(ApprovalButtons.movesAlways(card, botChat))
        assertEquals(listOf("Allow", "Always allow", "Deny"), ApprovalButtons.layout(card, botChat).map { it.option })
    }
}
