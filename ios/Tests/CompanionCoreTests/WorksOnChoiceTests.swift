import Foundation
import XCTest
@testable import CompanionCore

private final class WorksOnRequestStub: URLProtocol {
    static var status = 200
    static var responseBody = Data()
    static var capturedRequest: URLRequest?
    static var capturedBody: Data?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.capturedRequest = request
        Self.capturedBody = request.httpBody ?? Self.readStream(request.httpBodyStream)
        let response = HTTPURLResponse(
            url: request.url!, statusCode: Self.status, httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Self.responseBody)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}

    private static func readStream(_ stream: InputStream?) -> Data? {
        guard let stream else { return nil }
        stream.open()
        defer { stream.close() }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 1_024)
        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)
            guard count > 0 else { break }
            data.append(buffer, count: count)
        }
        return data
    }
}

final class WorksOnChoiceTests: XCTestCase {
    private func bot(computer: String? = "cloud", backend: String? = nil, vpsAlias: String? = nil) -> Bot {
        var bot = Bot(
            id: "bot-1", threadId: "t1", name: "Scout", title: "", description: "",
            notifications: true, color: "green", unread: false,
            modelSelection: ModelSelection(instanceId: "codex", model: "gpt-5"), createdAt: 1
        )
        bot.computer = computer
        bot.cloudBackend = backend
        bot.vpsAlias = vpsAlias
        return bot
    }

    private func offer(_ admin: Bool, _ running: Bool, _ bot: Bot, _ ready: Bool) -> WorksOnOffer? {
        WorksOnChoice.offer(
            canAdminister: admin, running: running, computer: bot.computer, cloudBackend: bot.cloudBackend,
            ownServerReady: ready
        )
    }

    private func notice(_ id: String) -> Message {
        var message = Message(id: id, role: .bot, kind: .activity, at: 1)
        message.tool = ToolActivity(
            name: "This bot's cloud computer isn't set up.", ok: true,
            place: FailedPlace(state: "cc-not-set-up"), notice: true
        )
        return message
    }

    private func legacy(_ id: String) -> Message {
        var message = Message(id: id, role: .bot, kind: .activity, at: 1)
        message.tool = ToolActivity(name: "error: Connect your Boat account in AI.", ok: false)
        return message
    }

    private func user(_ id: String) -> Message {
        var message = Message(id: id, role: .user, kind: .text, at: 1)
        message.text = "hi"
        return message
    }

    private func reply(_ id: String) -> Message {
        var message = Message(id: id, role: .bot, kind: .text, at: 1)
        message.text = "ok"
        return message
    }

    func testBothActionsWhenAdminIdleAndOwnServerReady() {
        XCTAssertEqual(
            offer(true, false, bot(), true),
            WorksOnOffer(withoutComputer: true, ownServer: true)
        )
    }

    func testOnlyWithoutAComputerWhenNoOwnServer() {
        XCTAssertEqual(
            offer(true, false, bot(), false),
            WorksOnOffer(withoutComputer: true, ownServer: false)
        )
    }

    func testNothingForAPhoneThatMayNotAdminister() {
        XCTAssertNil(offer(false, false, bot(), true))
    }

    func testNothingWhileATurnRuns() {
        XCTAssertNil(offer(true, true, bot(), true))
    }

    func testABotWithoutAComputerOffersOnlyItsOwnServer() {
        XCTAssertEqual(
            offer(true, false, bot(computer: "off"), true),
            WorksOnOffer(withoutComputer: false, ownServer: true)
        )
        XCTAssertNil(offer(true, false, bot(computer: "off"), false))
    }

    func testABotOnItsOwnServerOffersOnlyNoComputer() {
        XCTAssertEqual(
            offer(true, false, bot(backend: "vps"), true),
            WorksOnOffer(withoutComputer: true, ownServer: false)
        )
    }

    func testOwnServerIsReadyFromTheBotOrTheConfig() throws {
        let ready = try JSONDecoder().decode(ConfigStatus.self, from: Data(#"{"vps":{"configured":true}}"#.utf8))
        let notReady = try JSONDecoder().decode(ConfigStatus.self, from: Data(#"{"vps":{"configured":false}}"#.utf8))
        let bare = try JSONDecoder().decode(ConfigStatus.self, from: Data("{}".utf8))
        XCTAssertTrue(WorksOnChoice.ownServerReady(vpsAlias: "my-vps", configured: nil))
        XCTAssertTrue(WorksOnChoice.ownServerReady(vpsAlias: nil, configured: ready.vps?.configured))
        XCTAssertFalse(WorksOnChoice.ownServerReady(vpsAlias: " ", configured: notReady.vps?.configured))
        XCTAssertFalse(WorksOnChoice.ownServerReady(vpsAlias: nil, configured: bare.vps?.configured))
        XCTAssertFalse(WorksOnChoice.ownServerReady(vpsAlias: nil, configured: nil))
    }

    func testOnlyTheNewestNoticeCarriesTheActions() {
        let messages = [user("u1"), notice("n1"), reply("r1"), user("u2"), notice("n2")]
        XCTAssertEqual(WorksOnChoice.noticeMessageId(in: messages), "n2")
    }

    func testTheNewServerRowAndTheOlderServerRowBothCount() {
        XCTAssertEqual(WorksOnChoice.noticeMessageId(in: [user("u1"), notice("n1")]), "n1")
        XCTAssertEqual(WorksOnChoice.noticeMessageId(in: [user("u1"), legacy("l1")]), "l1")
    }

    func testARepeatedRowTheTranscriptHidesIsNotTheNewest() {
        XCTAssertEqual(WorksOnChoice.noticeMessageId(in: [user("u1"), legacy("l1"), user("u2"), legacy("l2")]), "l1")
    }

    func testOtherRowsAreNoNotice() {
        var other = Message(id: "o1", role: .bot, kind: .activity, at: 1)
        other.tool = ToolActivity(name: "error: overloaded", ok: false)
        var unrelated = Message(id: "o2", role: .bot, kind: .activity, at: 1)
        unrelated.tool = ToolActivity(name: "x", place: FailedPlace(state: "cc-asleep"), notice: true)
        XCTAssertNil(WorksOnChoice.noticeMessageId(in: [user("u1"), other, unrelated]))
    }

    func testARefusalKeepsTheServersSentenceWhenThereIsOne() {
        XCTAssertEqual(
            WorksOnChoice.refusal(APIError.status(code: 409, message: "Set up your own server first.")),
            "Set up your own server first."
        )
        XCTAssertNil(WorksOnChoice.refusal(APIError.status(code: 403, message: nil)))
        XCTAssertNil(WorksOnChoice.refusal(APIError.transport("timeout")))
    }
}

final class WorksOnClientTests: XCTestCase {
    private var session: URLSession!
    private var client: CompanionClient!

    override func setUp() {
        super.setUp()
        WorksOnRequestStub.status = 200
        WorksOnRequestStub.capturedRequest = nil
        WorksOnRequestStub.capturedBody = nil
        WorksOnRequestStub.responseBody = Data(#"""
        {"bot":{"id":"bot-1","threadId":"t1","name":"Scout","title":"","description":"",
         "notifications":true,"color":"green","unread":false,"createdAt":1,
         "modelSelection":{"instanceId":"codex","model":"gpt-5"},"computer":"off"}}
        """#.utf8)
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [WorksOnRequestStub.self]
        session = URLSession(configuration: configuration)
        client = CompanionClient(
            connection: Connection(name: "Mac", host: "127.0.0.1", port: 8810),
            token: "paired-token",
            session: session
        )
    }

    override func tearDown() {
        session.invalidateAndCancel()
        session = nil
        client = nil
        super.tearDown()
    }

    private func sentBody() throws -> [String: String] {
        let data = try XCTUnwrap(WorksOnRequestStub.capturedBody)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: String])
    }

    func testWithoutAComputerSendsExactlyComputerOff() async throws {
        let bot = try await client.setWorksOn(botId: "bot-1", ownServer: false)
        let request = try XCTUnwrap(WorksOnRequestStub.capturedRequest)
        XCTAssertEqual(request.httpMethod, "PATCH")
        XCTAssertEqual(request.url?.path, "/api/bots/bot-1")
        XCTAssertEqual(try sentBody(), ["computer": "off"])
        XCTAssertEqual(bot.computer, "off")
    }

    func testOwnServerSendsExactlyCloudAndVps() async throws {
        _ = try await client.setWorksOn(botId: "bot-1", ownServer: true)
        XCTAssertEqual(try sentBody(), ["computer": "cloud", "cloudBackend": "vps"])
    }

    func testARefusalCarriesTheServersSentence() async {
        WorksOnRequestStub.status = 409
        WorksOnRequestStub.responseBody = Data(#"{"error":"Set up your own server first."}"#.utf8)
        do {
            _ = try await client.setWorksOn(botId: "bot-1", ownServer: true)
            XCTFail("expected a refusal")
        } catch {
            XCTAssertEqual(WorksOnChoice.refusal(error), "Set up your own server first.")
        }
    }
}
