package com.openmausbot.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlanceTest {
    // Order and timing (AC-01, AC-02, AC-03)

    @Test
    fun needsAndQuestionsComeFirstThenFailedThenTheLongestWorkingThenFinished() {
        var memory = GlanceMemory()
        memory = Glance.step(memory, listOf(entry("w1", GlanceState.WORKING), entry("f", GlanceState.WORKING)), now = 0).first
        memory = Glance.step(
            memory,
            listOf(entry("w1", GlanceState.WORKING), entry("f", GlanceState.FAILED), entry("d", GlanceState.WORKING)),
            now = 100,
        ).first
        val (_, view) = Glance.step(
            memory,
            listOf(
                entry("w2", GlanceState.WORKING),
                entry("w1", GlanceState.WORKING),
                entry("f", GlanceState.FAILED),
                entry("q", GlanceState.QUESTION),
                entry("n", GlanceState.NEEDS),
            ),
            now = 200,
        )
        assertEquals(listOf("q", "n", "f", "w1", "w2", "d"), view.entries.map { it.bot.botId })
        assertEquals(GlanceState.DONE, view.entries.last().bot.state)
        assertEquals("q", view.focus?.bot?.botId)
    }

    @Test
    fun aBotThatStopsWorkingShowsAsFinishedForFourSecondsThenLeaves() {
        var memory = Glance.step(GlanceMemory(), listOf(entry("a", GlanceState.WORKING, line = "Reading")), now = 1_000).first
        val (afterStop, stopped) = Glance.step(memory, emptyList(), now = 2_000)
        val done = stopped.entries.single()
        assertEquals(GlanceState.DONE, done.bot.state)
        assertEquals("", done.bot.line)
        assertNull(done.bot.ask)
        assertEquals(2_000 + Glance.DONE_MS, stopped.nextAt)
        memory = afterStop

        val (stillDone, view) = Glance.step(memory, emptyList(), now = 2_000 + Glance.DONE_MS - 1)
        assertEquals(listOf(GlanceState.DONE), view.entries.map { it.bot.state })
        val (_, gone) = Glance.step(stillDone, emptyList(), now = 2_000 + Glance.DONE_MS)
        assertTrue(gone.entries.isEmpty())
        assertNull(gone.nextAt)
    }

    @Test
    fun aBotThatGoesFromWorkingToAnAskIsNotFinished() {
        val memory = Glance.step(GlanceMemory(), listOf(entry("a", GlanceState.WORKING)), now = 0).first
        val (_, view) = Glance.step(memory, listOf(entry("a", GlanceState.NEEDS)), now = 10)
        assertEquals(listOf(GlanceState.NEEDS), view.entries.map { it.bot.state })
    }

    @Test
    fun aFailureSeenFirstIsHiddenAndAFailureAfterWorkIsShown() {
        val (memory, first) = Glance.step(GlanceMemory(), listOf(entry("old", GlanceState.FAILED)), now = 0)
        assertTrue(first.entries.isEmpty())
        val (_, again) = Glance.step(memory, listOf(entry("old", GlanceState.FAILED)), now = 10)
        assertTrue(again.entries.isEmpty())

        val working = Glance.step(GlanceMemory(), listOf(entry("new", GlanceState.WORKING)), now = 0).first
        val (failedMemory, failed) = Glance.step(working, listOf(entry("new", GlanceState.FAILED)), now = 10)
        assertEquals(listOf(GlanceState.FAILED), failed.entries.map { it.bot.state })
        val (_, stays) = Glance.step(failedMemory, listOf(entry("new", GlanceState.FAILED)), now = 20)
        assertEquals(listOf(GlanceState.FAILED), stays.entries.map { it.bot.state })
    }

    @Test
    fun workingRanksByTheTimeTheModelFirstSawIt() {
        val early = Glance.step(GlanceMemory(), listOf(entry("b", GlanceState.WORKING)), now = 0).first
        val (_, view) = Glance.step(early, listOf(entry("a", GlanceState.WORKING), entry("b", GlanceState.WORKING)), now = 50)
        assertEquals(listOf("b", "a"), view.entries.map { it.bot.botId })
        assertEquals(listOf(0L, 50L), view.entries.map { it.startedAt })
    }

    // What the state says (AC-04, AC-05)

    @Test
    fun aWorkingBotCarriesItsOwnLastLineAndNoToolName() {
        val bot = bot("a", busy = true)
        val state = CompanionState(
            bots = listOf(bot),
            messages = mapOf(bot.threadId to listOf(activity("t1", "Bash: rm -rf build"))),
            streaming = mapOf(bot.threadId to "I looked at the tests.\n\n**Now** I am fixing the `login` form"),
        )
        val glance = Glance.bots(state).single()
        assertEquals(GlanceState.WORKING, glance.state)
        assertEquals("Now I am fixing the login form", glance.line)
    }

    @Test
    fun aWorkingBotWithNoTextOfItsOwnHasNoLine() {
        val bot = bot("a", busy = true)
        val state = CompanionState(
            bots = listOf(bot),
            messages = mapOf(bot.threadId to listOf(activity("t1", "Bash: npm test"))),
        )
        assertEquals("", Glance.bots(state).single().line)
    }

    @Test
    fun theLineSkipsCodeAndKeepsTheTailOfALongSentence() {
        assertEquals("I will run this:", Glance.line("I will run this:\n```sh\nrm -rf /tmp/x\n```"))
        assertEquals("I will run this:", Glance.line("I will run this:\n```sh\nrm -rf /tmp/x"))
        val long = "word ".repeat(80).trim()
        val line = Glance.line(long)
        assertEquals(Glance.LINE_LIMIT, line.codePointCount(0, line.length))
        assertTrue(line.startsWith("…"))
        assertTrue(line.endsWith("word"))
        assertEquals("", Glance.line("   "))
        assertEquals("", Glance.line(null))
    }

    @Test
    fun onlyAWorkingBotHasALine() {
        val bot = bot("a", busy = true, activity = "waiting-on-you")
        val state = CompanionState(bots = listOf(bot), streaming = mapOf(bot.threadId to "Some words"))
        val glance = Glance.bots(state).single()
        assertEquals(GlanceState.NEEDS, glance.state)
        assertEquals("", glance.line)
        assertNull(glance.ask)
    }

    @Test
    fun aPlainPermissionCardCarriesItsWholeText() {
        val card = permission("r1", subtitle = "  npm test --filter login\n  ")
        assertEquals("npm test --filter login", Glance.approvalDetail(card))
        val exact = "a".repeat(Glance.DETAIL_LIMIT)
        assertEquals(exact, Glance.approvalDetail(permission("r2", subtitle = exact)))
    }

    @Test
    fun aLongEmptyOrNonPermissionCardHasNoDetail() {
        assertEquals("", Glance.approvalDetail(permission("r1", subtitle = "a".repeat(Glance.DETAIL_LIMIT + 1))))
        assertEquals("", Glance.approvalDetail(permission("r1", subtitle = "  ")))
        assertEquals("", Glance.approvalDetail(permission("r1", subtitle = "ls").copy(requestType = "question")))
        assertEquals("", Glance.approvalDetail(permission("r1", subtitle = "ls").copy(tool = null)))
        assertEquals(
            "",
            Glance.approvalDetail(
                permission("r1", subtitle = "ls").copy(outboundRequest = OutboundRequest(calls = emptyList())),
            ),
        )
    }

    @Test
    fun theDetailDropsControlCharactersButKeepsLines() {
        val card = permission("r1", subtitle = "echo a‮\r\necho b\u0007")
        assertEquals("echo a\necho b", Glance.approvalDetail(card))
    }

    @Test
    fun aPendingPermissionMakesTheBotNeedTheOldestApproval() {
        val bot = bot("a", busy = true)
        val state = CompanionState(
            bots = listOf(bot),
            messages = mapOf(
                bot.threadId to listOf(
                    options("m1", permission("old", subtitle = "git status"), at = 1.0),
                    options("m2", permission("new", subtitle = "git push"), at = 2.0),
                ),
            ),
        )
        val glance = Glance.bots(state).single()
        assertEquals(GlanceState.NEEDS, glance.state)
        assertEquals("old", glance.ask?.requestId)
        assertEquals(GlanceAskKind.APPROVAL, glance.ask?.kind)
        assertEquals("git status", glance.ask?.detail)
        assertEquals("", glance.line)
    }

    @Test
    fun anApprovalComesBeforeAQuestionAndAReviewCardOnlyOpensTheChat() {
        val bot = bot("a")
        val question = permission("q", subtitle = "Which file?").copy(tool = null, requestType = "question")
        val skill = permission("s", subtitle = "skill").copy(tool = null)
        val state = CompanionState(
            bots = listOf(bot),
            messages = mapOf(
                bot.threadId to listOf(options("m1", question, at = 1.0), options("m2", permission("p", "ls"), at = 2.0)),
            ),
        )
        assertEquals("p", Glance.bots(state).single().ask?.requestId)

        val onlyQuestion = state.copy(messages = mapOf(bot.threadId to listOf(options("m1", question, at = 1.0))))
        val asked = Glance.bots(onlyQuestion).single()
        assertEquals(GlanceState.QUESTION, asked.state)
        assertFalse(asked.ask!!.canDeny)
        assertEquals("", asked.ask!!.detail)

        val review = state.copy(messages = mapOf(bot.threadId to listOf(options("m1", skill, at = 1.0))))
        val reviewed = Glance.bots(review).single()
        assertEquals(GlanceState.NEEDS, reviewed.state)
        assertEquals(GlanceAskKind.REVIEW, reviewed.ask?.kind)
        assertFalse(reviewed.ask!!.canDeny)
        assertFalse(reviewed.ask!!.canAllow(GlanceBudget(lineChars = 40, lines = 10)))
    }

    @Test
    fun anUnreadFailedTurnIsFailedAndAReadOneIsNot() {
        val failedTurn = listOf(text("u1", Message.Role.USER, "Fix it"), activity("e1", "error: The engine stopped"))
        val unread = bot("a", unread = true)
        val failed = CompanionState(bots = listOf(unread), messages = mapOf(unread.threadId to failedTurn))
        assertEquals(GlanceState.FAILED, Glance.bots(failed).single().state)

        val read = bot("a", unread = false)
        assertTrue(Glance.bots(failed.copy(bots = listOf(read))).isEmpty())
    }

    @Test
    fun anIdleOrHiddenBotIsNotInGlance() {
        assertTrue(Glance.bots(CompanionState(bots = listOf(bot("a")))).isEmpty())
        assertTrue(Glance.bots(CompanionState(bots = listOf(bot("a", busy = true).copy(hidden = true)))).isEmpty())
        assertTrue(Glance.bots(CompanionState(bots = listOf(bot("a", busy = true).copy(waitingOnTeammate = true)))).isEmpty())
    }

    @Test
    fun aWorkingTaskThreadIsTheEntryThread() {
        val bot = bot("a").copy(
            tasks = listOf(
                BotTask(threadId = "a-thread", title = "Main", createdAt = 1.0),
                BotTask(threadId = "a-side", title = "Side", createdAt = 2.0, activity = "working"),
            ),
        )
        val glance = Glance.bots(CompanionState(bots = listOf(bot))).single()
        assertEquals("a-side", glance.threadId)
        assertEquals(GlanceState.WORKING, glance.state)
    }

    // Allow once and the surface budget (AC-05, AC-10)

    @Test
    fun allowOnceNeedsADetailThatFitsTheSurfaceWhole() {
        val budget = GlanceBudget(lineChars = 10, lines = 2)
        assertTrue(ask(detail = "npm test").canAllow(budget))
        assertTrue(ask(detail = "0123456789abcdefghij").canAllow(budget))
        assertFalse(ask(detail = "0123456789abcdefghijk").canAllow(budget))
        assertFalse(ask(detail = "a\nb\nc").canAllow(budget))
        assertFalse(ask(detail = "").canAllow(budget))
        assertTrue(ask(detail = "").canDeny)
        assertFalse(ask(detail = "ls", kind = GlanceAskKind.QUESTION).canAllow(budget))
    }

    @Test
    fun currentAskNamesOnlyTheRequestThatStillWaits() {
        val bot = bot("a")
        val state = CompanionState(
            bots = listOf(bot),
            messages = mapOf(bot.threadId to listOf(options("m1", permission("r1", "git push"), at = 1.0))),
        )
        assertEquals("git push", Glance.currentAsk(state, "a", "r1")?.detail)
        assertNull(Glance.currentAsk(state, "a", "other"))
        assertNull(Glance.currentAsk(state, "nobody", "r1"))
        val answered = state.copy(
            messages = mapOf(bot.threadId to listOf(options("m1", permission("r1", "git push").copy(answered = "allow"), at = 1.0))),
        )
        assertNull(Glance.currentAsk(answered, "a", "r1"))
    }

    // Helpers

    private fun entry(id: String, state: GlanceState, line: String = ""): GlanceBot =
        GlanceBot(id, "$id-thread", id, null, state, if (state == GlanceState.WORKING) line else "", null)

    private fun ask(detail: String, kind: GlanceAskKind = GlanceAskKind.APPROVAL): GlanceAsk =
        GlanceAsk(kind, "r1", "t", "Approval needed", detail, 1.0)

    private fun bot(id: String, busy: Boolean? = null, activity: String? = null, unread: Boolean = false): Bot = Bot(
        id = id, threadId = "$id-thread", name = "Bot $id", title = "Helper",
        description = "", notifications = true, color = "blue", unread = unread,
        modelSelection = ModelSelection("i", "m"), createdAt = 1.0,
        busy = busy, activity = activity,
    )

    private fun permission(requestId: String, subtitle: String): OptionCard = OptionCard(
        title = "Approval needed",
        subtitle = subtitle,
        options = listOf("Allow", "Deny"),
        requestId = requestId,
        tool = "Bash",
        requestType = "permission",
    )

    private fun options(id: String, card: OptionCard, at: Double): Message =
        Message(id = id, role = Message.Role.BOT, kind = Message.Kind.OPTIONS, at = at, card = card)

    private fun activity(id: String, name: String): Message =
        Message(id = id, role = Message.Role.BOT, kind = Message.Kind.ACTIVITY, at = 2.0, tool = ToolActivity(name = name))

    private fun text(id: String, role: Message.Role, body: String): Message =
        Message(id = id, role = role, kind = Message.Kind.TEXT, at = 1.0, text = body)
}
