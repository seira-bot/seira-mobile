import XCTest
@testable import CompanionCore

final class ComposerRulesTests: XCTestCase {
    private func bot() -> Bot {
        Bot(
            id: "b", threadId: "t", name: "Ada", title: "", description: "", notifications: true,
            color: "blue", unread: false, modelSelection: ModelSelection(instanceId: "i", model: "m"), createdAt: 1
        )
    }

    private func room(dm: Bool? = nil) -> Room {
        var room = Room(
            id: "r", threadId: "rt", name: "Channel", memberIds: [],
            defaultResponder: GroupResponder(kind: "all"), bulletin: "", unread: false, createdAt: 1,
            tasks: []
        )
        room.dm = dm
        return room
    }

    // MARK: - Trailing buttons

    func testStopStaysBesideSendWhileTheBotWorks() {
        let typed = ComposerTrailing.of(hasContent: true, canStop: true, callAvailable: true)
        XCTAssertTrue(typed.showsStop)
        XCTAssertTrue(typed.showsSend)
        XCTAssertFalse(typed.showsCall)
        let empty = ComposerTrailing.of(hasContent: false, canStop: true, callAvailable: true)
        XCTAssertTrue(empty.showsStop)
        XCTAssertFalse(empty.showsSend)
        XCTAssertFalse(empty.showsCall)
    }

    func testAnIdleEmptyFieldOffersTheCall() {
        let idle = ComposerTrailing.of(hasContent: false, canStop: false, callAvailable: true)
        XCTAssertTrue(idle.showsCall)
        XCTAssertFalse(ComposerTrailing.of(hasContent: false, canStop: false, callAvailable: false).showsCall)
    }

    // MARK: - Mode chip

    func testOnlyABotChatShowsTheModeChip() {
        XCTAssertTrue(ModeChip.visible(.bot(bot())))
        XCTAssertFalse(ModeChip.visible(.room(room())))
    }

    func testTheSheetListsFourModesAndCustomOnlyWhileCurrent() {
        XCTAssertEqual(ModeChip.options(current: .ask), [.ask, .edits, .auto, .full])
        XCTAssertEqual(ModeChip.options(current: nil), [.ask, .edits, .auto, .full])
        XCTAssertEqual(ModeChip.options(current: .custom).last, .custom)
    }

    func testPickingAModeSendsThatMode() async {
        var sent: [(ApprovalMode, Bool)] = []
        let outcome = await ModeChange.request(current: .ask, target: .edits, acknowledged: false) { mode, ack in
            sent.append((mode, ack))
            return .changed(self.bot(), mode)
        }
        XCTAssertEqual(sent.count, 1)
        XCTAssertEqual(sent.first?.0, .edits)
        XCTAssertEqual(outcome, .changed(.edits))
    }

    func testARefusalKeepsTheOldMode() async {
        let outcome = await ModeChange.request(current: .ask, target: .full, acknowledged: false) { _, _ in
            .refused(.fullNotAllowedHere("x"))
        }
        XCTAssertEqual(outcome, .refused(kept: .ask, reason: .fullNotAllowedHere))
    }

    func testAutoOnTheComputerAsksFirstThenSendsTheAcknowledgement() async {
        var acks: [Bool] = []
        let set: (ApprovalMode, Bool) async -> ApprovalModeResult = { mode, ack in
            acks.append(ack)
            return ack ? .changed(self.bot(), mode) : .refused(.localAutoNeedsAcknowledgement("x"))
        }
        let first = await ModeChange.request(current: .ask, target: .auto, acknowledged: false, set: set)
        XCTAssertEqual(first, .needsConfirmation(.auto))
        let second = await ModeChange.request(current: .ask, target: .auto, acknowledged: true, set: set)
        XCTAssertEqual(second, .changed(.auto))
        XCTAssertEqual(acks, [false, true])
    }

    func testCustomIsNeverSentFromThePhone() async {
        var calls = 0
        let outcome = await ModeChange.request(current: .ask, target: .custom, acknowledged: false) { mode, _ in
            calls += 1
            return .changed(self.bot(), mode)
        }
        XCTAssertEqual(calls, 0)
        XCTAssertEqual(outcome, .refused(kept: .ask, reason: .customOnDesktop))
    }

    // MARK: - Steering

    func testSteeringReadsTheComputersAnswer() {
        XCTAssertEqual(QueuedSteering.outcome(of: .steered(queueIds: ["q"])), .done)
        XCTAssertEqual(QueuedSteering.outcome(of: .gone), .done)
        XCTAssertEqual(QueuedSteering.outcome(of: .stillQueued(blockedBy: nil)), .waiting)
        XCTAssertEqual(QueuedSteering.outcome(of: nil), .failed)
    }

    // MARK: - Slash commands

    private func ids(_ commands: [SlashCommand]) -> [SlashCommandID] { commands.map(\.id) }

    func testABotChatHasTheComputerAndThreadsCommands() {
        let list = SlashCommands.available(SlashGate(chat: .bot(bot())))
        XCTAssertEqual(ids(list), [.computer, .threads, .diff, .retry, .steer])
    }

    func testAGroupHasNoComputerCommand() {
        let list = SlashCommands.available(SlashGate(chat: .room(room())))
        XCTAssertEqual(ids(list), [.threads, .diff, .retry, .steer])
    }

    func testSetupAndBurstNeedABotWithAgents() {
        let bots = SlashCommands.available(SlashGate(chat: .bot(bot()), supportsAgents: true))
        XCTAssertEqual(ids(bots).suffix(2), [.setup, .burst])
        let group = SlashCommands.available(SlashGate(chat: .room(room()), supportsAgents: true))
        XCTAssertFalse(ids(group).contains(.setup))
        XCTAssertFalse(ids(group).contains(.burst))
    }

    func testLearnNeedsTheLearnGate() {
        XCTAssertFalse(ids(SlashCommands.available(SlashGate(chat: .bot(bot())))).contains(.learn))
        XCTAssertTrue(ids(SlashCommands.available(SlashGate(chat: .bot(bot()), canLearn: true))).contains(.learn))
    }

    func testNothingStartsAGoalBecausePhoneMessagesCarryNoGoalMode() {
        let all = SlashCommands.available(SlashGate(isBot: true, supportsThreads: true, supportsAgents: true, canLearn: true))
        XCTAssertFalse(all.contains { $0.title == "/goal" })
    }

    func testOnlySendCarriesAPrompt() {
        let list = SlashCommands.available(SlashGate(chat: .bot(bot()), supportsAgents: true))
        XCTAssertEqual(list.first { $0.id == .computer }?.effect, .openComputer)
        XCTAssertEqual(list.first { $0.id == .threads }?.effect, .openThreads)
        XCTAssertEqual(list.first { $0.id == .setup }?.effect, .insert("/setup "))
    }

    func testTheListOpensOnASlashWordOnly() {
        XCTAssertTrue(SlashCommands.opensOnDraft("/"))
        XCTAssertTrue(SlashCommands.opensOnDraft("/dif"))
        XCTAssertFalse(SlashCommands.opensOnDraft("/setup now"))
        XCTAssertFalse(SlashCommands.opensOnDraft("hello"))
    }

    func testMatchingReadsTitleAndDescription() {
        let list = SlashCommands.available(SlashGate(chat: .bot(bot())))
        XCTAssertEqual(ids(SlashCommands.matching(list, draft: "/git")), [.diff])
        XCTAssertEqual(SlashCommands.matching(list, draft: "/"), list)
    }

    // MARK: - Reply

    func testAReplyQuotesTheTypedWords() {
        let out = ComposerReply.outgoing(replied: "Done", typed: "Thanks", hasAttachments: false, answersQuestion: false)
        XCTAssertEqual(out.text, "> Done\n\nThanks")
        XCTAssertTrue(out.clearsReply)
    }

    func testAFileWithoutWordsSendsTheQuote() {
        let out = ComposerReply.outgoing(replied: "Done", typed: "", hasAttachments: true, answersQuestion: false)
        XCTAssertEqual(out.text, "> Done")
        XCTAssertTrue(out.clearsReply)
    }

    func testASlashLineStaysBare() {
        let out = ComposerReply.outgoing(replied: "Done", typed: "/setup x", hasAttachments: false, answersQuestion: false)
        XCTAssertEqual(out.text, "/setup x")
        XCTAssertFalse(out.clearsReply)
    }

    func testAnAnswerToAQuestionDropsTheQuoteAndClosesTheStrip() {
        let out = ComposerReply.outgoing(replied: "Done", typed: "Yes", hasAttachments: false, answersQuestion: true)
        XCTAssertEqual(out.text, "Yes")
        XCTAssertTrue(out.clearsReply)
    }

    func testNoReplyChangesNothing() {
        let out = ComposerReply.outgoing(replied: nil, typed: "Hi", hasAttachments: false, answersQuestion: false)
        XCTAssertEqual(out, ComposerOutgoing(text: "Hi", clearsReply: false))
    }

    // MARK: - Approval ladder

    func testTheLadderHasTheFourPhoneSteps() {
        XCTAssertEqual(ModeLadder.steps, [.ask, .edits, .auto, .full])
        XCTAssertEqual(ModeLadder.holdMilliseconds, 700)
    }

    func testEachStepHasAnIndexAndAFraction() {
        XCTAssertEqual(ModeLadder.index(of: .ask), 0)
        XCTAssertEqual(ModeLadder.index(of: .full), 3)
        XCTAssertEqual(ModeLadder.fraction(of: .ask), 0)
        XCTAssertEqual(ModeLadder.fraction(of: .edits) ?? -1, 1.0 / 3.0, accuracy: 1e-9)
        XCTAssertEqual(ModeLadder.fraction(of: .auto) ?? -1, 2.0 / 3.0, accuracy: 1e-9)
        XCTAssertEqual(ModeLadder.fraction(of: .full), 1)
    }

    func testNoModeAndCustomHaveNoStep() {
        XCTAssertNil(ModeLadder.index(of: nil))
        XCTAssertNil(ModeLadder.index(of: .custom))
        XCTAssertNil(ModeLadder.fraction(of: nil))
        XCTAssertNil(ModeLadder.fraction(of: .custom))
    }

    func testAPositionSnapsToTheNearestStep() {
        XCTAssertEqual(ModeLadder.step(atFraction: 0), .ask)
        XCTAssertEqual(ModeLadder.step(atFraction: 0.16), .ask)
        XCTAssertEqual(ModeLadder.step(atFraction: 0.17), .edits)
        XCTAssertEqual(ModeLadder.step(atFraction: 0.33), .edits)
        XCTAssertEqual(ModeLadder.step(atFraction: 0.67), .auto)
        XCTAssertEqual(ModeLadder.step(atFraction: 0.84), .full)
        XCTAssertEqual(ModeLadder.step(atFraction: 1), .full)
    }

    func testATieGoesToTheLowerStep() {
        XCTAssertEqual(ModeLadder.step(atFraction: 0.5), .edits)
    }

    func testAPositionOutsideTheTrackIsClamped() {
        XCTAssertEqual(ModeLadder.step(atFraction: -0.5), .ask)
        XCTAssertEqual(ModeLadder.step(atFraction: 1.7), .full)
        XCTAssertEqual(ModeLadder.step(atFraction: .nan), .ask)
    }

    func testOnlyFullNeedsAHold() {
        XCTAssertTrue(ModeLadder.needsHold(.full))
        XCTAssertFalse(ModeLadder.needsHold(.ask))
        XCTAssertFalse(ModeLadder.needsHold(.edits))
        XCTAssertFalse(ModeLadder.needsHold(.auto))
        XCTAssertFalse(ModeLadder.needsHold(.custom))
    }

    func testCustomShowsOnlyWhileItIsTheCurrentMode() {
        XCTAssertTrue(ModeLadder.showsCustom(current: .custom))
        XCTAssertFalse(ModeLadder.showsCustom(current: .ask))
        XCTAssertFalse(ModeLadder.showsCustom(current: nil))
    }
}
