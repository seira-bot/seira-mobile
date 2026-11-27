import XCTest
@testable import CompanionCore

final class ChatRulesTests: XCTestCase {
    private func text(_ id: String, _ body: String, at: Double = 1, role: Message.Role = .bot) -> Message {
        var message = Message(id: id, role: role, kind: .text, at: at)
        message.text = body
        return message
    }

    private func step(_ id: String, at: Double) -> Message {
        var message = Message(id: id, role: .bot, kind: .activity, at: at)
        message.tool = ToolActivity(name: id)
        return message
    }

    private func bot() -> Bot {
        Bot(
            id: "b", threadId: "t", name: "Ada", title: "", description: "", notifications: true,
            color: "blue", unread: false, modelSelection: ModelSelection(instanceId: "i", model: "m"), createdAt: 1
        )
    }

    private func room(tasks: [BotTask]?) -> Room {
        Room(
            id: "r", threadId: "rt", name: "Channel", memberIds: [],
            defaultResponder: GroupResponder(kind: "all"), bulletin: "", unread: false, createdAt: 1,
            tasks: tasks
        )
    }

    private let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }()

    private func millis(_ iso: String) -> Double {
        (ISO8601DateFormatter().date(from: iso) ?? Date()).timeIntervalSince1970 * 1_000
    }

    private func rows(_ items: [(String, String)]) -> [TranscriptRow] {
        items.map { .message(text($0.0, "x", at: millis($0.1))) }
    }

    func testFindIgnoresCaseAndKeepsTheOrder() {
        let messages = [
            text("a", "Send the March invoices"),
            text("b", "Done"),
            text("c", "The march totals are ready", role: .user),
        ]
        XCTAssertEqual(ChatFindRules.hits(messages, query: "MARCH"), ["a", "c"])
    }

    func testABlankSearchFindsNothing() {
        let messages = [text("a", "Hello")]
        XCTAssertTrue(ChatFindRules.hits(messages, query: "").isEmpty)
        XCTAssertTrue(ChatFindRules.hits(messages, query: "   ").isEmpty)
    }

    func testAToolChipIsNeverAMatch() {
        let messages = [text("a", "march"), step("march", at: 5)]
        XCTAssertEqual(ChatFindRules.hits(messages, query: "march"), ["a"])
    }

    func testSteppingWrapsPastBothEnds() {
        XCTAssertEqual(ChatFindRules.step(current: 0, count: 3, forward: true), 1)
        XCTAssertEqual(ChatFindRules.step(current: 2, count: 3, forward: true), 0)
        XCTAssertEqual(ChatFindRules.step(current: 0, count: 3, forward: false), 2)
        XCTAssertEqual(ChatFindRules.step(current: 2, count: 3, forward: false), 1)
    }

    func testSteppingWithNothingFoundOrNoPosition() {
        XCTAssertEqual(ChatFindRules.step(current: 0, count: 0, forward: true), -1)
        XCTAssertEqual(ChatFindRules.step(current: -1, count: 3, forward: true), 0)
        XCTAssertEqual(ChatFindRules.step(current: -1, count: 3, forward: false), 2)
    }

    func testTheFirstRowNamesTodayYesterdayOrADate() {
        let now = Date(timeIntervalSince1970: millis("2026-11-26T12:00:00Z") / 1_000)
        let list = rows([("a", "2026-11-26T08:00:00Z"), ("b", "2026-11-25T08:00:00Z"), ("c", "2026-11-01T08:00:00Z")])
        XCTAssertEqual(ChatDaySeparators.label(rows: [list[0]], index: 0, now: now, calendar: calendar)?.kind, .today)
        XCTAssertEqual(ChatDaySeparators.label(rows: [list[1]], index: 0, now: now, calendar: calendar)?.kind, .yesterday)
        XCTAssertEqual(ChatDaySeparators.label(rows: [list[2]], index: 0, now: now, calendar: calendar)?.kind, .date)
    }

    func testARowOnTheSameDayHasNoLabel() {
        let now = Date(timeIntervalSince1970: millis("2026-11-26T12:00:00Z") / 1_000)
        let list = rows([("a", "2026-11-26T08:00:00Z"), ("b", "2026-11-26T09:30:00Z")])
        XCTAssertNil(ChatDaySeparators.label(rows: list, index: 1, now: now, calendar: calendar))
    }

    func testANewDayGetsALabelAMinuteAfterMidnight() {
        let now = Date(timeIntervalSince1970: millis("2026-11-26T12:00:00Z") / 1_000)
        let list = rows([("a", "2026-11-25T23:59:00Z"), ("b", "2026-11-26T00:01:00Z")])
        XCTAssertEqual(ChatDaySeparators.label(rows: list, index: 1, now: now, calendar: calendar)?.kind, .today)
        XCTAssertNil(ChatDaySeparators.label(rows: [], index: 0, now: now, calendar: calendar))
    }

    func testEveryLineOfAReplyStartsWithAQuoteMark() {
        XCTAssertEqual(ReplyQuote.quote("one\ntwo"), "> one\n> two")
        XCTAssertEqual(ReplyQuote.quote("one\r\ntwo"), "> one\n> two")
    }

    func testTheQuoteIsTheFirst280Characters() {
        XCTAssertEqual(ReplyQuote.quote(String(repeating: "a", count: 400)), "> " + String(repeating: "a", count: 280))
    }

    func testTheReplyFollowsAfterOneBlankLine() {
        XCTAssertEqual(ReplyQuote.wrap("Send it", text: "Yes, now"), "> Send it\n\nYes, now")
        XCTAssertEqual(ReplyQuote.wrap(nil, text: "Yes"), "Yes")
        XCTAssertEqual(ReplyQuote.wrap("  ", text: "Yes"), "Yes")
    }

    func testTheReplyStripKeepsTheFirstLineWithWords() {
        XCTAssertEqual(ReplyQuote.firstLine("\n\nHello\nWorld"), "Hello")
    }

    func testABotMenuHasAModelAndAScreenButNoMembers() {
        XCTAssertEqual(
            ChatMenuRules.items(.bot(bot())),
            [.find, .newThread, .allThreads, .model, .botScreen, .share]
        )
    }

    func testAGroupMenuListsMembers() {
        let items = ChatMenuRules.items(.room(room(tasks: [])))
        XCTAssertTrue(items.contains(.members))
        XCTAssertTrue(items.contains(.allThreads))
        XCTAssertFalse(items.contains(.model))
        XCTAssertFalse(items.contains(.botScreen))
    }

    func testAGroupWithoutThreadsOffersNoThreadItems() {
        let items = ChatMenuRules.items(.room(room(tasks: nil)))
        XCTAssertFalse(items.contains(.newThread))
        XCTAssertFalse(items.contains(.allThreads))
        XCTAssertTrue(items.contains(.find))
        XCTAssertFalse(ChatMenuRules.showsScreenButton(.room(room(tasks: nil))))
        XCTAssertTrue(ChatMenuRules.showsScreenButton(.bot(bot())))
    }

    func testTheSharedTranscriptNamesWhoSpoke() {
        let messages = [text("a", "Hi", role: .user), text("b", "Hello"), step("c", at: 3)]
        XCTAssertEqual(ChatMenuRules.transcriptText(messages, botName: "Ada"), "You: Hi\n\nAda: Hello")
    }
}
