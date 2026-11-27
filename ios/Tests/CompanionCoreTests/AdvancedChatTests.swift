import Foundation
import XCTest
@testable import CompanionCore

private final class AdvancedRequestStub: URLProtocol {
    static var capturedRequest: URLRequest?
    static var capturedBody: Data?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.capturedRequest = request
        Self.capturedBody = Self.readBody(from: request)
        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: 202,
            httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data("{}".utf8))
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}

    private static func readBody(from request: URLRequest) -> Data? {
        if let body = request.httpBody { return body }
        guard let stream = request.httpBodyStream else { return nil }
        stream.open()
        defer { stream.close() }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 1_024)
        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)
            guard count >= 0 else { return nil }
            if count == 0 { break }
            data.append(buffer, count: count)
        }
        return data
    }
}

final class AdvancedChatTests: XCTestCase {
    private var session: URLSession!
    private var client: CompanionClient!

    override func setUp() {
        super.setUp()
        AdvancedRequestStub.capturedRequest = nil
        AdvancedRequestStub.capturedBody = nil
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [AdvancedRequestStub.self]
        session = URLSession(configuration: configuration)
        client = CompanionClient(
            connection: Connection(name: "Test", host: "127.0.0.1", port: 8810),
            token: "paired-token",
            session: session
        )
    }

    override func tearDown() {
        session?.invalidateAndCancel()
        session = nil
        client = nil
        super.tearDown()
    }

    private func sentBody() throws -> [String: Any] {
        let body = try XCTUnwrap(AdvancedRequestStub.capturedBody)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: body) as? [String: Any])
    }

    private func bot() -> Bot {
        Bot(
            id: "b", threadId: "t", name: "Ada", title: "", description: "", notifications: true,
            color: "blue", unread: false, modelSelection: ModelSelection(instanceId: "i", model: "m"), createdAt: 1
        )
    }

    private func room(dm: Bool = false) -> Room {
        var room = Room(
            id: "r", threadId: "rt", name: "Channel", memberIds: [],
            defaultResponder: GroupResponder(kind: "all"), bulletin: "", unread: false, createdAt: 1,
            tasks: []
        )
        room.dm = dm
        return room
    }

    private func task(_ json: String) throws -> BotTask {
        try JSONDecoder().decode(BotTask.self, from: Data(json.utf8))
    }

    private func card(allowSession: Bool?, tool: String? = "Bash") -> OptionCard {
        var card = OptionCard(title: "Run a command?", subtitle: "git status", options: ["Allow", "Deny"])
        card.requestId = "r1"
        card.requestType = "permission"
        card.tool = tool
        card.allowSession = allowSession
        return card
    }

    // MARK: Approval

    func testAlwaysIsSentOnlyWithAllowAndOnlyWhenAsked() async throws {
        try await client.respond(threadId: "t", requestId: "r1", behavior: "allow", always: true)
        XCTAssertEqual(try sentBody()["always"] as? Bool, true)

        try await client.respond(threadId: "t", requestId: "r1", behavior: "allow")
        XCTAssertNil(try sentBody()["always"])

        try await client.respond(threadId: "t", requestId: "r1", behavior: "deny", always: true)
        XCTAssertNil(try sentBody()["always"])
    }

    func testTheSessionButtonShowsOnlyInAdvancedModeOnACardThatAllowsIt() {
        XCTAssertTrue(card(allowSession: true).offersSessionAllow(advancedMode: true))
        XCTAssertFalse(card(allowSession: true).offersSessionAllow(advancedMode: false))
        XCTAssertFalse(card(allowSession: false).offersSessionAllow(advancedMode: true))
        XCTAssertFalse(card(allowSession: nil).offersSessionAllow(advancedMode: true))
        XCTAssertFalse(card(allowSession: true, tool: nil).offersSessionAllow(advancedMode: true))
    }

    func testAnAnsweredCardNoLongerOffersTheSessionButton() {
        var answered = card(allowSession: true)
        answered.answered = "allow"
        XCTAssertFalse(answered.offersSessionAllow(advancedMode: true))
    }

    func testAllowSessionDecodesFromTheCard() throws {
        let json = #"{"title":"t","subtitle":"s","options":["Allow","Deny"],"requestId":"r","tool":"Bash","allowSession":true}"#
        let decoded = try JSONDecoder().decode(OptionCard.self, from: Data(json.utf8))
        XCTAssertEqual(decoded.allowSession, true)
    }

    // MARK: Compact

    func testCompactPostsTheThread() async throws {
        try await client.compact(botId: "b", threadId: "thread-1")
        XCTAssertEqual(AdvancedRequestStub.capturedRequest?.url?.path, "/api/bots/b/compact")
        XCTAssertEqual(AdvancedRequestStub.capturedRequest?.httpMethod, "POST")
        XCTAssertEqual(try sentBody()["threadId"] as? String, "thread-1")
    }

    // MARK: Menu and chips

    func testAdvancedOffLeavesTheOneOneZeroMenuAndNoChips() {
        let meter = ContextMeter(percent: 42, tokens: 84_000, window: 200_000)
        for chat in [Chat.bot(bot()), Chat.room(room())] {
            XCTAssertEqual(ChatMenuRules.items(chat), ChatMenuRules.items(chat, advanced: false))
            XCTAssertEqual(
                ChatAdvanced.tools(chat, advanced: false, learnAllowed: true, goalRunning: false, context: meter),
                []
            )
        }
        let advancedOnly: Set<ChatMenuItem> = [.rename, .copyMarkdown, .downloadMarkdown, .activity]
        XCTAssertTrue(ChatMenuRules.items(.bot(bot())).allSatisfy { !advancedOnly.contains($0) })
    }

    func testAdvancedOnAddsFourRowsToABotChat() {
        let chat = Chat.bot(bot())
        XCTAssertEqual(
            ChatMenuRules.items(chat, advanced: true),
            ChatMenuRules.items(chat) + [.rename, .copyMarkdown, .downloadMarkdown, .activity]
        )
    }

    func testAGroupGetsOnlyTheRowsThatWorkForAGroup() {
        let chat = Chat.room(room())
        XCTAssertEqual(
            ChatMenuRules.items(chat, advanced: true),
            ChatMenuRules.items(chat) + [.copyMarkdown, .downloadMarkdown]
        )
    }

    func testTheChipsFollowTheGatesTheGoalAndTheContextNumbers() {
        let bot = Chat.bot(bot())
        let meter = ContextMeter(percent: 42, tokens: 84_000, window: 200_000)
        XCTAssertEqual(
            ChatAdvanced.tools(bot, advanced: true, learnAllowed: true, goalRunning: false, context: meter),
            [.teach, .goal, .context]
        )
        XCTAssertEqual(
            ChatAdvanced.tools(bot, advanced: true, learnAllowed: false, goalRunning: false, context: nil),
            [.goal]
        )
        XCTAssertEqual(
            ChatAdvanced.tools(bot, advanced: true, learnAllowed: false, goalRunning: true, context: meter),
            [.context]
        )
        XCTAssertEqual(
            ChatAdvanced.tools(.room(room()), advanced: true, learnAllowed: false, goalRunning: false, context: meter),
            [.goal]
        )
        XCTAssertEqual(
            ChatAdvanced.tools(.room(room(dm: true)), advanced: true, learnAllowed: false, goalRunning: false, context: meter),
            []
        )
    }

    // MARK: Context meter

    func testTheMeterNeedsBothTokensAndAWindow() throws {
        let full = try task(#"{"threadId":"t","title":"A","createdAt":1,"usage":{"input":900,"output":100,"turns":3,"context":{"tokens":84000,"window":200000}}}"#)
        XCTAssertEqual(ChatAdvanced.contextMeter(full), ContextMeter(percent: 42, tokens: 84_000, window: 200_000))

        let noWindow = try task(#"{"threadId":"t","title":"A","createdAt":1,"usage":{"input":900,"output":100,"turns":3,"context":{"tokens":84000}}}"#)
        XCTAssertNil(ChatAdvanced.contextMeter(noWindow))

        let zeroWindow = try task(#"{"threadId":"t","title":"A","createdAt":1,"usage":{"turns":3,"context":{"tokens":5,"window":0}}}"#)
        XCTAssertNil(ChatAdvanced.contextMeter(zeroWindow))

        let none = try task(#"{"threadId":"t","title":"A","createdAt":1}"#)
        XCTAssertNil(ChatAdvanced.contextMeter(none))
        XCTAssertNil(ChatAdvanced.contextMeter(nil))

        let over = try task(#"{"threadId":"t","title":"A","createdAt":1,"usage":{"turns":1,"context":{"tokens":300000,"window":200000}}}"#)
        XCTAssertEqual(ChatAdvanced.contextMeter(over)?.percent, 100)
    }

    // MARK: Small pure helpers

    func testCountsReadShort() {
        XCTAssertEqual(ChatAdvanced.compactCount(850), "850")
        XCTAssertEqual(ChatAdvanced.compactCount(1_500), "1.5k")
        XCTAssertEqual(ChatAdvanced.compactCount(2_000), "2k")
        XCTAssertEqual(ChatAdvanced.compactCount(12_400), "12k")
        XCTAssertEqual(ChatAdvanced.compactCount(200_000), "200k")
        XCTAssertEqual(ChatAdvanced.compactCount(1_200_000), "1.2M")
    }

    func testAChipWritesItsCommandFirstAndKeepsTheTypedWords() {
        XCTAssertEqual(ChatAdvanced.insertion(draft: "", command: "/learn "), "/learn ")
        XCTAssertEqual(ChatAdvanced.insertion(draft: "ship it", command: "/goal "), "/goal ship it")
        XCTAssertEqual(ChatAdvanced.insertion(draft: "/goal ship it", command: "/goal "), "/goal ship it")
    }

    func testADownloadedFileIsNamedFromTheThreadTitle() {
        XCTAssertEqual(ChatAdvanced.markdownFileName("Weekly summary"), "Weekly-summary.md")
        XCTAssertEqual(ChatAdvanced.markdownFileName("  "), "conversation.md")
        XCTAssertEqual(ChatAdvanced.markdownFileName(nil), "conversation.md")
        XCTAssertEqual(ChatAdvanced.markdownFileName("../a/b"), "a-b.md")
    }

    func testARenameIsTrimmedAndCutToEightyCharacters() {
        XCTAssertEqual(ChatAdvanced.renameTitle("  Plan "), "Plan")
        XCTAssertEqual(ChatAdvanced.renameTitle(String(repeating: "x", count: 200)).count, 80)
        XCTAssertFalse(ChatAdvanced.canSaveRename("   "))
        XCTAssertTrue(ChatAdvanced.canSaveRename("Plan"))
    }
}
