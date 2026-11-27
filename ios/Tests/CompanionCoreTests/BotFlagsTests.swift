import Foundation
import XCTest
@testable import CompanionCore

private final class BotFlagsRequestStub: URLProtocol {
    static var capturedRequest: URLRequest?
    static var capturedBody: Data?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.capturedRequest = request
        Self.capturedBody = request.httpBody ?? Self.readStream(request.httpBodyStream)
        let response = HTTPURLResponse(
            url: request.url!, statusCode: 200, httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(#"""
        {"bot":{"id":"bot-1","threadId":"t1","name":"Scout","title":"","description":"",
         "notifications":true,"color":"green","unread":true,"pinned":true,"createdAt":1,
         "modelSelection":{"instanceId":"codex","model":"gpt-5"}}}
        """#.utf8))
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

final class BotFlagsClientTests: XCTestCase {
    private var session: URLSession!
    private var client: CompanionClient!

    override func setUp() {
        super.setUp()
        BotFlagsRequestStub.capturedRequest = nil
        BotFlagsRequestStub.capturedBody = nil
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [BotFlagsRequestStub.self]
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

    private func sentKeys() throws -> Set<String> {
        let data = try XCTUnwrap(BotFlagsRequestStub.capturedBody)
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        return Set(object.keys)
    }

    func testPinSendsOnlyPinnedAndThePairedHeader() async throws {
        let bot = try await client.setBotFlags(botId: "bot-1", pinned: true)
        let request = try XCTUnwrap(BotFlagsRequestStub.capturedRequest)
        XCTAssertEqual(request.httpMethod, "PATCH")
        XCTAssertEqual(request.url?.path, "/api/bots/bot-1")
        XCTAssertEqual(request.value(forHTTPHeaderField: "x-seira-companion"), "1")
        XCTAssertEqual(try sentKeys(), ["pinned"])
        XCTAssertEqual(bot.pinned, true)
    }

    func testMarkUnreadSendsOnlyUnread() async throws {
        _ = try await client.setBotFlags(botId: "bot-1", unread: true)
        let request = try XCTUnwrap(BotFlagsRequestStub.capturedRequest)
        XCTAssertEqual(request.value(forHTTPHeaderField: "x-seira-companion"), "1")
        XCTAssertEqual(try sentKeys(), ["unread"])
    }
}

final class BotDepartmentsTests: XCTestCase {
    private func bot(_ id: String, section: String?) -> Bot {
        var bot = Bot(
            id: id, threadId: "t-\(id)", name: id, title: "", description: "",
            notifications: true, color: "green", unread: false,
            modelSelection: ModelSelection(instanceId: "codex", model: "m"), createdAt: 1
        )
        bot.section = section
        return bot
    }

    func testNamesAreDistinctSortedAndSkipBlank() {
        let bots = [bot("a", section: "Sales"), bot("b", section: " "), bot("c", section: "ops"),
                    bot("d", section: "sales"), bot("e", section: nil)]
        XCTAssertEqual(BotDepartments.names(in: bots), ["ops", "Sales"])
    }

    func testNamesKeepADepartmentWithNoBot() {
        let bots = [bot("a", section: "Sales")]
        XCTAssertEqual(BotDepartments.names(in: bots, declared: ["sales", "Legal", " "]), ["Legal", "sales"])
    }

    func testANewNameNeedsOneToSixtyCharacters() {
        XCTAssertNil(BotDepartments.cleanName("   "))
        XCTAssertNil(BotDepartments.cleanName(String(repeating: "x", count: 61)))
        XCTAssertEqual(BotDepartments.cleanName("  Sales "), "Sales")
        XCTAssertEqual(BotDepartments.cleanName(String(repeating: "x", count: 60))?.count, 60)
    }

    func testDepartmentsMatchWithoutRegardToCase() {
        XCTAssertTrue(BotDepartments.same("Sales", " sales"))
        XCTAssertTrue(BotDepartments.same("", " "))
        XCTAssertFalse(BotDepartments.same("Sales", ""))
    }
}
