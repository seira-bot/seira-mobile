package com.openmausbot.companion.ui.home

import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.BotTask
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.ModelSelection
import com.openmausbot.companion.core.OptionCard
import com.openmausbot.companion.core.Routine
import com.openmausbot.companion.core.RoutineRun
import com.openmausbot.companion.core.RoutineSchedule
import com.openmausbot.companion.ui.ChatUpdate
import com.openmausbot.companion.ui.UpdateKind
import com.openmausbot.companion.ui.home.inbox.DecisionAnswer
import com.openmausbot.companion.ui.home.inbox.DecisionKind
import com.openmausbot.companion.ui.home.inbox.InboxDecisions
import com.openmausbot.companion.ui.home.inbox.InboxNews
import com.openmausbot.companion.ui.home.inbox.NewsItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InboxLogicTest {
    private val now = 10L * 24 * 60 * 60 * 1000

    private fun bot(id: String, name: String, tasks: List<BotTask>? = null) = Bot(
        id, "$id-t", name, "Role", "", true, "blue", false,
        ModelSelection("default", "default"), 0.0, tasks = tasks,
    )

    private fun card(
        options: List<String>,
        tool: String? = null,
        title: String = "Send an email",
        requestId: String? = "r1",
        answered: String? = null,
    ) = OptionCard(
        title = title, subtitle = "To: Team", options = options,
        requestId = requestId, tool = tool, answered = answered,
    )

    private fun update(
        name: String = "Pippa",
        kind: UpdateKind = UpdateKind.NEEDS_YOU,
        card: OptionCard? = null,
        line: String = "",
    ) = ChatUpdate(Chat.BotChat(bot(name.lowercase(), name)), kind, line, card)

    @Test
    fun `only waiting chats are decisions and the count matches`() {
        val updates = listOf(
            update("Ann", UpdateKind.WORKING),
            update("Bea", UpdateKind.NEEDS_YOU),
            update("Cy", UpdateKind.TO_REVIEW),
            update("Di", UpdateKind.NEEDS_YOU),
        )
        assertEquals(listOf("Bea", "Di"), InboxDecisions.decisions(updates).map { it.chat.name })
        assertEquals(2, InboxDecisions.count(updates))
    }

    @Test
    fun `kind follows the card`() {
        assertEquals(DecisionKind.APPROVAL, InboxDecisions.kind(card(listOf("Allow", "Deny"), tool = "mail")))
        assertEquals(DecisionKind.QUESTION, InboxDecisions.kind(card(listOf("Warm leads", "All leads"))))
        assertEquals(DecisionKind.WAITING, InboxDecisions.kind(null))
    }

    @Test
    fun `an approval fills Allow once and leaves the others outlined`() {
        val answer = InboxDecisions.answer(card(listOf("Allow once", "Deny", "Always allow"), tool = "mail"))
        assertIs<DecisionAnswer.Options>(answer)
        assertEquals("Allow once", answer.primary)
        assertEquals(listOf("Allow once", "Deny", "Always allow"), answer.labels)
    }

    @Test
    fun `a question fills its first option that is not a refusal`() {
        val answer = InboxDecisions.answer(card(listOf("Not now", "Warm leads", "All leads")))
        assertIs<DecisionAnswer.Options>(answer)
        assertEquals("Warm leads", answer.primary)
    }

    @Test
    fun `a send, team memory or engine switch card is answered in the chat`() {
        val base = card(listOf("Allow", "Deny"), tool = "mail")
        assertEquals(
            DecisionAnswer.InChat,
            InboxDecisions.answer(base.copy(outboundRequest = com.openmausbot.companion.core.OutboundRequest())),
        )
        assertEquals(
            DecisionAnswer.InChat,
            InboxDecisions.answer(base.copy(teamMemoryRequest = com.openmausbot.companion.core.TeamMemoryRequest())),
        )
        assertEquals(
            DecisionAnswer.InChat,
            InboxDecisions.answer(base.copy(quotaSwitch = com.openmausbot.companion.core.QuotaSwitchCard())),
        )
    }

    @Test
    fun `a settled or option-less card offers no answer here`() {
        assertEquals(DecisionAnswer.None, InboxDecisions.answer(null))
        assertEquals(DecisionAnswer.None, InboxDecisions.answer(card(listOf("Allow"), answered = "allow")))
        assertEquals(DecisionAnswer.None, InboxDecisions.answer(card(emptyList())))
        assertEquals(DecisionAnswer.None, InboxDecisions.answer(card(listOf("Allow"), requestId = null)))
    }

    @Test
    fun `the title falls back to the chat name and the kind line names the bot when needed`() {
        val named = update("Pippa", card = card(listOf("Allow"), title = "Pippa wants to send an email"))
        assertEquals("Pippa wants to send an email", InboxDecisions.title(named))
        assertTrue(InboxDecisions.namesBot(named))

        val plain = update("Pippa", card = card(listOf("Allow"), title = "Send an email"))
        assertEquals(false, InboxDecisions.namesBot(plain))

        val bare = update("Pippa", card = null)
        assertEquals("Pippa", InboxDecisions.title(bare))
    }

    @Test
    fun `detail skips a line that repeats the title`() {
        val same = update(card = card(listOf("Allow"), title = "Send an email"), line = "Send an email")
        assertNull(InboxDecisions.detail(same))
        val different = update(card = card(listOf("Allow"), title = "Send an email"), line = "To: Team")
        assertEquals("To: Team", InboxDecisions.detail(different))
    }

    private fun routine(id: String, name: String = "Inbox summary") = Routine(
        id = id, name = name, prompt = "p", botId = "pippa", runOn = "maus", enabled = true,
        schedule = RoutineSchedule.daily("09:30", listOf(1)), durationMinutes = 30,
        createdAt = 0.0, updatedAt = 0.0,
    )

    private fun run(id: String, routineId: String, status: String, scheduledFor: Double, finishedAt: Double? = null) =
        RoutineRun(
            id = id, routineId = routineId, routineName = "x", botId = "pippa", runOn = "maus",
            scheduledFor = scheduledFor, status = status, manual = false, finishedAt = finishedAt, createdAt = 0.0,
        )

    @Test
    fun `a routine is a problem while its newest run failed`() {
        val routines = listOf(routine("a"), routine("b"), routine("c"))
        val day = 24.0 * 60 * 60 * 1000
        val runs = listOf(
            run("a1", "a", "failed", now - 2 * day),
            run("b1", "b", "failed", now - 3 * day),
            run("b2", "b", "completed", now - 1 * day),
            run("c1", "c", "failed", now - 9 * day),
            run("x1", "gone", "failed", now - day),
        )
        val problems = InboxNews.problems(routines, runs, now)
        assertEquals(listOf("a"), problems.map { it.first.id })
    }

    @Test
    fun `a retry that is running clears the problem`() {
        val day = 24.0 * 60 * 60 * 1000
        val runs = listOf(
            run("a1", "a", "failed", now - 2 * day),
            run("a2", "a", "running", now - 1000.0),
        )
        assertTrue(InboxNews.problems(listOf(routine("a")), runs, now).isEmpty())
    }

    @Test
    fun `news lists finished work and problems newest first`() {
        val finishedOld = update("Bix", UpdateKind.TO_REVIEW, line = "Preview is ready")
        val finishedNew = update("Mo", UpdateKind.TO_REVIEW, line = "Done")
        val stamps = mapOf("bix-t" to now - 5_000.0, "mo-t" to now - 1_000.0)
        val items = InboxNews.build(
            updates = listOf(finishedOld, update("Ann", UpdateKind.WORKING), finishedNew),
            routines = listOf(routine("a")),
            runs = listOf(run("a1", "a", "failed", now - 3_000.0, finishedAt = now - 3_000.0)),
            nowMillis = now,
            stampOf = { stamps[it.chat.threadId] },
        )
        assertEquals(3, items.size)
        assertEquals("Mo", (items[0] as NewsItem.Finished).update.chat.name)
        assertIs<NewsItem.RoutineProblem>(items[1])
        assertEquals("Bix", (items[2] as NewsItem.Finished).update.chat.name)
    }

    @Test
    fun `news without a stamp goes last and the list is capped`() {
        val many = (1..30).map { update("Bot$it", UpdateKind.TO_REVIEW) }
        val items = InboxNews.build(many, emptyList(), emptyList(), now, stampOf = { null })
        assertEquals(InboxNews.LIMIT, items.size)

        val mixed = InboxNews.build(
            listOf(update("A", UpdateKind.TO_REVIEW), update("B", UpdateKind.TO_REVIEW)),
            emptyList(), emptyList(), now,
            stampOf = { if (it.chat.name == "B") 5.0 else null },
        )
        assertEquals("B", (mixed.first() as NewsItem.Finished).update.chat.name)
    }

    @Test
    fun `the task stamp reads the thread's own time`() {
        val task = BotTask("pippa-t", "t", 100.0, updatedAt = 900.0)
        val withTask = ChatUpdate(Chat.BotChat(bot("pippa", "Pippa", listOf(task))), UpdateKind.TO_REVIEW, "")
        assertEquals(900.0, InboxNews.taskStamp(withTask))
        assertNull(InboxNews.taskStamp(update("Pippa", UpdateKind.TO_REVIEW)))
    }
}
