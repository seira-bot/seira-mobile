import XCTest
@testable import CompanionCore

final class GlanceTests: XCTestCase {
    // Order and timing (AC-01, AC-02, AC-03)

    func testNeedsAndQuestionsComeFirstThenFailedThenTheLongestWorkingThenFinished() {
        var memory = GlanceMemory()
        memory = Glance.step(memory, bots: [entry("w1", .working), entry("f", .working)], now: 0).0
        memory = Glance.step(
            memory, bots: [entry("w1", .working), entry("f", .failed), entry("d", .working)], now: 100
        ).0
        let (_, view) = Glance.step(
            memory,
            bots: [
                entry("w2", .working),
                entry("w1", .working),
                entry("f", .failed),
                entry("q", .question),
                entry("n", .needs),
            ],
            now: 200
        )
        XCTAssertEqual(view.entries.map(\.bot.botId), ["q", "n", "f", "w1", "w2", "d"])
        XCTAssertEqual(view.entries.last?.bot.state, .done)
        XCTAssertEqual(view.focus?.bot.botId, "q")
    }

    func testABotThatStopsWorkingShowsAsFinishedForFourSecondsThenLeaves() throws {
        var memory = Glance.step(GlanceMemory(), bots: [entry("a", .working, line: "Reading")], now: 1_000).0
        let (afterStop, stopped) = Glance.step(memory, bots: [], now: 2_000)
        let done = try XCTUnwrap(stopped.entries.first)
        XCTAssertEqual(stopped.entries.count, 1)
        XCTAssertEqual(done.bot.state, .done)
        XCTAssertEqual(done.bot.line, "")
        XCTAssertNil(done.bot.ask)
        XCTAssertEqual(stopped.nextAt, 2_000 + Glance.doneMs)
        memory = afterStop

        let (stillDone, view) = Glance.step(memory, bots: [], now: 2_000 + Glance.doneMs - 1)
        XCTAssertEqual(view.entries.map(\.bot.state), [.done])
        let (_, gone) = Glance.step(stillDone, bots: [], now: 2_000 + Glance.doneMs)
        XCTAssertTrue(gone.entries.isEmpty)
        XCTAssertNil(gone.nextAt)
    }

    func testABotThatGoesFromWorkingToAnAskIsNotFinished() {
        let memory = Glance.step(GlanceMemory(), bots: [entry("a", .working)], now: 0).0
        let (_, view) = Glance.step(memory, bots: [entry("a", .needs)], now: 10)
        XCTAssertEqual(view.entries.map(\.bot.state), [.needs])
    }

    func testAFailureSeenFirstIsHiddenAndAFailureAfterWorkIsShown() {
        let (memory, first) = Glance.step(GlanceMemory(), bots: [entry("old", .failed)], now: 0)
        XCTAssertTrue(first.entries.isEmpty)
        let (_, again) = Glance.step(memory, bots: [entry("old", .failed)], now: 10)
        XCTAssertTrue(again.entries.isEmpty)

        let working = Glance.step(GlanceMemory(), bots: [entry("new", .working)], now: 0).0
        let (failedMemory, failed) = Glance.step(working, bots: [entry("new", .failed)], now: 10)
        XCTAssertEqual(failed.entries.map(\.bot.state), [.failed])
        let (_, stays) = Glance.step(failedMemory, bots: [entry("new", .failed)], now: 20)
        XCTAssertEqual(stays.entries.map(\.bot.state), [.failed])
    }

    func testWorkingRanksByTheTimeTheModelFirstSawIt() {
        let early = Glance.step(GlanceMemory(), bots: [entry("b", .working)], now: 0).0
        let (_, view) = Glance.step(early, bots: [entry("a", .working), entry("b", .working)], now: 50)
        XCTAssertEqual(view.entries.map(\.bot.botId), ["b", "a"])
        XCTAssertEqual(view.entries.map(\.startedAt), [0, 50])
    }

    // What the state says (AC-04, AC-05)

    func testAWorkingBotCarriesItsOwnLastLineAndNoToolName() throws {
        let bot = makeBot("a", busy: true)
        var state = CompanionState()
        state.bots = [bot]
        state.messages = [bot.threadId: [activity("t1", "Bash: rm -rf build")]]
        state.streaming = [bot.threadId: "I looked at the tests.\n\n**Now** I am fixing the `login` form"]
        let glance = try XCTUnwrap(Glance.bots(state).first)
        XCTAssertEqual(glance.state, .working)
        XCTAssertEqual(glance.line, "Now I am fixing the login form")
    }

    func testAWorkingBotWithNoTextOfItsOwnHasNoLine() throws {
        let bot = makeBot("a", busy: true)
        var state = CompanionState()
        state.bots = [bot]
        state.messages = [bot.threadId: [activity("t1", "Bash: npm test")]]
        XCTAssertEqual(try XCTUnwrap(Glance.bots(state).first).line, "")
    }

    func testTheLineSkipsCodeAndKeepsTheTailOfALongSentence() {
        XCTAssertEqual(Glance.line("I will run this:\n```sh\nrm -rf /tmp/x\n```"), "I will run this:")
        XCTAssertEqual(Glance.line("I will run this:\n```sh\nrm -rf /tmp/x"), "I will run this:")
        let long = String(repeating: "word ", count: 80).trimmingCharacters(in: .whitespaces)
        let line = Glance.line(long)
        XCTAssertEqual(line.unicodeScalars.count, Glance.lineLimit)
        XCTAssertTrue(line.hasPrefix("…"))
        XCTAssertTrue(line.hasSuffix("word"))
        XCTAssertEqual(Glance.line("   "), "")
        XCTAssertEqual(Glance.line(nil), "")
    }

    func testOnlyAWorkingBotHasALine() throws {
        let bot = makeBot("a", busy: true, activity: "waiting-on-you")
        var state = CompanionState()
        state.bots = [bot]
        state.streaming = [bot.threadId: "Some words"]
        let glance = try XCTUnwrap(Glance.bots(state).first)
        XCTAssertEqual(glance.state, .needs)
        XCTAssertEqual(glance.line, "")
        XCTAssertNil(glance.ask)
    }

    func testAPlainPermissionCardCarriesItsWholeText() {
        let card = permission("r1", subtitle: "  npm test --filter login\n  ")
        XCTAssertEqual(Glance.approvalDetail(card), "npm test --filter login")
        let exact = String(repeating: "a", count: Glance.detailLimit)
        XCTAssertEqual(Glance.approvalDetail(permission("r2", subtitle: exact)), exact)
    }

    func testALongEmptyOrNonPermissionCardHasNoDetail() {
        XCTAssertEqual(Glance.approvalDetail(permission("r1", subtitle: String(repeating: "a", count: Glance.detailLimit + 1))), "")
        XCTAssertEqual(Glance.approvalDetail(permission("r1", subtitle: "  ")), "")
        var question = permission("r1", subtitle: "ls")
        question.requestType = "question"
        XCTAssertEqual(Glance.approvalDetail(question), "")
        var noTool = permission("r1", subtitle: "ls")
        noTool.tool = nil
        XCTAssertEqual(Glance.approvalDetail(noTool), "")
        var outbound = permission("r1", subtitle: "ls")
        outbound.outboundRequest = OutboundRequest(calls: [])
        XCTAssertEqual(Glance.approvalDetail(outbound), "")
    }

    func testTheDetailDropsControlCharactersButKeepsLines() {
        let card = permission("r1", subtitle: "echo a\u{202E}\r\necho b\u{07}")
        XCTAssertEqual(Glance.approvalDetail(card), "echo a\necho b")
    }

    func testAPendingPermissionMakesTheBotNeedTheOldestApproval() throws {
        let bot = makeBot("a", busy: true)
        var state = CompanionState()
        state.bots = [bot]
        state.messages = [bot.threadId: [
            options("m1", permission("old", subtitle: "git status"), at: 1),
            options("m2", permission("new", subtitle: "git push"), at: 2),
        ]]
        let glance = try XCTUnwrap(Glance.bots(state).first)
        XCTAssertEqual(glance.state, .needs)
        XCTAssertEqual(glance.ask?.requestId, "old")
        XCTAssertEqual(glance.ask?.kind, .approval)
        XCTAssertEqual(glance.ask?.detail, "git status")
        XCTAssertEqual(glance.line, "")
    }

    func testAnApprovalComesBeforeAQuestionAndAReviewCardOnlyOpensTheChat() throws {
        let bot = makeBot("a")
        var question = permission("q", subtitle: "Which file?")
        question.tool = nil
        question.requestType = "question"
        var skill = permission("s", subtitle: "skill")
        skill.tool = nil
        var state = CompanionState()
        state.bots = [bot]
        state.messages = [bot.threadId: [
            options("m1", question, at: 1),
            options("m2", permission("p", subtitle: "ls"), at: 2),
        ]]
        XCTAssertEqual(Glance.bots(state).first?.ask?.requestId, "p")

        state.messages = [bot.threadId: [options("m1", question, at: 1)]]
        let asked = try XCTUnwrap(Glance.bots(state).first)
        XCTAssertEqual(asked.state, .question)
        XCTAssertFalse(try XCTUnwrap(asked.ask).canDeny)
        XCTAssertEqual(asked.ask?.detail, "")

        state.messages = [bot.threadId: [options("m1", skill, at: 1)]]
        let reviewed = try XCTUnwrap(Glance.bots(state).first)
        XCTAssertEqual(reviewed.state, .needs)
        XCTAssertEqual(reviewed.ask?.kind, .review)
        XCTAssertFalse(try XCTUnwrap(reviewed.ask).canDeny)
        XCTAssertFalse(try XCTUnwrap(reviewed.ask).canAllow(GlanceBudget(lineChars: 40, lines: 10)))
    }

    func testAnUnreadFailedTurnIsFailedAndAReadOneIsNot() throws {
        let failedTurn = [text("u1", .user, "Fix it"), activity("e1", "error: The engine stopped")]
        let unread = makeBot("a", unread: true)
        var failed = CompanionState()
        failed.bots = [unread]
        failed.messages = [unread.threadId: failedTurn]
        XCTAssertEqual(Glance.bots(failed).first?.state, .failed)

        failed.bots = [makeBot("a", unread: false)]
        XCTAssertTrue(Glance.bots(failed).isEmpty)
    }

    func testAnIdleOrHiddenBotIsNotInGlance() {
        var state = CompanionState()
        state.bots = [makeBot("a")]
        XCTAssertTrue(Glance.bots(state).isEmpty)
        var hidden = makeBot("a", busy: true)
        hidden.hidden = true
        state.bots = [hidden]
        XCTAssertTrue(Glance.bots(state).isEmpty)
        var waiting = makeBot("a", busy: true)
        waiting.waitingOnTeammate = true
        state.bots = [waiting]
        XCTAssertTrue(Glance.bots(state).isEmpty)
    }

    func testAWorkingTaskThreadIsTheEntryThread() throws {
        var bot = makeBot("a")
        var side = BotTask(threadId: "a-side", title: "Side", createdAt: 2)
        side.activity = "working"
        bot.tasks = [BotTask(threadId: "a-thread", title: "Main", createdAt: 1), side]
        var state = CompanionState()
        state.bots = [bot]
        let glance = try XCTUnwrap(Glance.bots(state).first)
        XCTAssertEqual(glance.threadId, "a-side")
        XCTAssertEqual(glance.state, .working)
    }

    // Allow once and the surface budget (AC-05, AC-10)

    func testAllowOnceNeedsADetailThatFitsTheSurfaceWhole() {
        let budget = GlanceBudget(lineChars: 10, lines: 2)
        XCTAssertTrue(ask(detail: "npm test").canAllow(budget))
        XCTAssertTrue(ask(detail: "0123456789abcdefghij").canAllow(budget))
        XCTAssertFalse(ask(detail: "0123456789abcdefghijk").canAllow(budget))
        XCTAssertFalse(ask(detail: "a\nb\nc").canAllow(budget))
        XCTAssertFalse(ask(detail: "").canAllow(budget))
        XCTAssertTrue(ask(detail: "").canDeny)
        XCTAssertFalse(ask(detail: "ls", kind: .question).canAllow(budget))
    }

    func testCurrentAskNamesOnlyTheRequestThatStillWaits() {
        let bot = makeBot("a")
        var state = CompanionState()
        state.bots = [bot]
        state.messages = [bot.threadId: [options("m1", permission("r1", subtitle: "git push"), at: 1)]]
        XCTAssertEqual(Glance.currentAsk(state, botId: "a", requestId: "r1")?.detail, "git push")
        XCTAssertNil(Glance.currentAsk(state, botId: "a", requestId: "other"))
        XCTAssertNil(Glance.currentAsk(state, botId: "nobody", requestId: "r1"))
        var settled = permission("r1", subtitle: "git push")
        settled.answered = "allow"
        state.messages = [bot.threadId: [options("m1", settled, at: 1)]]
        XCTAssertNil(Glance.currentAsk(state, botId: "a", requestId: "r1"))
    }

    // Helpers

    private func entry(_ id: String, _ state: GlanceState, line: String = "") -> GlanceBot {
        GlanceBot(
            botId: id, threadId: "\(id)-thread", name: id, color: nil, state: state,
            line: state == .working ? line : "", ask: nil
        )
    }

    private func ask(detail: String, kind: GlanceAskKind = .approval) -> GlanceAsk {
        GlanceAsk(kind: kind, requestId: "r1", threadId: "t", title: "Approval needed", detail: detail, at: 1)
    }

    private func makeBot(_ id: String, busy: Bool? = nil, activity: String? = nil, unread: Bool = false) -> Bot {
        var bot = Bot(
            id: id, threadId: "\(id)-thread", name: "Bot \(id)", title: "Helper",
            description: "", notifications: true, color: "blue", unread: unread,
            modelSelection: ModelSelection(instanceId: "i", model: "m"), createdAt: 1
        )
        bot.busy = busy
        bot.activity = activity
        return bot
    }

    private func permission(_ requestId: String, subtitle: String) -> OptionCard {
        OptionCard(
            title: "Approval needed", subtitle: subtitle, options: ["Allow", "Deny"],
            requestId: requestId, tool: "Bash", requestType: "permission"
        )
    }

    private func options(_ id: String, _ card: OptionCard, at: Double) -> Message {
        var message = Message(id: id, role: .bot, kind: .options, at: at)
        message.card = card
        return message
    }

    private func activity(_ id: String, _ name: String) -> Message {
        var message = Message(id: id, role: .bot, kind: .activity, at: 2)
        message.tool = ToolActivity(name: name)
        return message
    }

    private func text(_ id: String, _ role: Message.Role, _ body: String) -> Message {
        var message = Message(id: id, role: role, kind: .text, at: 1)
        message.text = body
        return message
    }
}
