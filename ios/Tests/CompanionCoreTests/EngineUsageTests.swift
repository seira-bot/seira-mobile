import Foundation
import XCTest
@testable import CompanionCore

private final class LimitsStub: URLProtocol {
    static var statusCode = 200
    static var responseBody = Data()
    static var capturedRequest: URLRequest?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.capturedRequest = request
        let response = HTTPURLResponse(
            url: request.url!, statusCode: Self.statusCode, httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Self.responseBody)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

final class EngineUsageTests: XCTestCase {
    private var session: URLSession!
    private var client: CompanionClient!

    override func setUp() {
        super.setUp()
        LimitsStub.statusCode = 200
        LimitsStub.capturedRequest = nil
        LimitsStub.responseBody = (try? fixture("limit-resets")) ?? Data()
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [LimitsStub.self]
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

    private func fixture(_ name: String) throws -> Data {
        guard let url = Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "Fixtures")
            ?? Bundle.module.url(forResource: name, withExtension: "json")
        else { throw CocoaError(.fileNoSuchFile) }
        return try Data(contentsOf: url)
    }

    private func report(_ json: String) throws -> EngineLimitsReport {
        try JSONDecoder().decode(EngineLimitsReport.self, from: Data(json.utf8))
    }

    // MARK: - Decoding

    func testDecodesTheFixtureWithAndWithoutWindows() throws {
        let decoded = try JSONDecoder().decode(EngineLimitsReport.self, from: try fixture("limit-resets"))
        XCTAssertEqual(decoded.engines.map(\.engine), ["claude", "codex", "grok", "opencode-local"])
        XCTAssertEqual(decoded.engines[0].windows?.map(\.window), ["5-hour", "weekly"])
        XCTAssertTrue(decoded.engines[0].blocked)
        XCTAssertNil(decoded.engines[1].windows)
        XCTAssertNil(decoded.engines[2].usedPercent)
        XCTAssertNil(decoded.engines[2].window)
    }

    func testKeepsAWindowNameThisBuildDoesNotKnow() throws {
        let decoded = try JSONDecoder().decode(EngineLimitsReport.self, from: try fixture("limit-resets"))
        XCTAssertEqual(decoded.engines[3].window, "monthly")
        XCTAssertEqual(decoded.engines[3].driver, "opencode")
    }

    func testReadsAnEngineThatSendsNoWindowsAndNoHold() throws {
        let decoded = try report(#"""
        {"checkedAt":1,"engines":[{"engine":"claude","name":"Claude","driver":"claude","window":"5-hour",
        "usedPercent":12,"resetsAt":null,"blocked":false}]}
        """#)
        XCTAssertNil(decoded.engines[0].windows)
        XCTAssertNil(decoded.engines[0].held)
    }

    func testToolActivityReadsLimitResetsAt() throws {
        let tool = try JSONDecoder().decode(
            ToolActivity.self,
            from: Data(#"{"name":"error: limit","ok":false,"limitResetsAt":1795284000000}"#.utf8)
        )
        XCTAssertEqual(tool.limitResetsAt, 1_795_284_000_000)
        let old = try JSONDecoder().decode(ToolActivity.self, from: Data(#"{"name":"error: x","ok":false}"#.utf8))
        XCTAssertNil(old.limitResetsAt)
    }

    // MARK: - Client

    func testEngineLimitsAsksForTheStoredReadWithoutRefresh() async throws {
        let loaded = try await client.engineLimits()
        let request = try XCTUnwrap(LimitsStub.capturedRequest)
        XCTAssertEqual(request.httpMethod, "GET")
        XCTAssertEqual(request.url?.path, "/api/limit-resets")
        XCTAssertNil(request.url?.query)
        XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer paired-token")
        XCTAssertEqual(loaded?.engines.count, 4)
    }

    func testEngineLimitsRefreshAddsTheQuery() async throws {
        _ = try await client.engineLimits(refresh: true)
        let request = try XCTUnwrap(LimitsStub.capturedRequest)
        XCTAssertEqual(request.url?.path, "/api/limit-resets")
        XCTAssertEqual(request.url?.query, "refresh=1")
    }

    func testForbiddenGivesNil() async throws {
        LimitsStub.statusCode = 403
        LimitsStub.responseBody = try fixture("forbidden")
        let loaded = try await client.engineLimits()
        XCTAssertNil(loaded)
    }

    func testNotFoundGivesNil() async throws {
        LimitsStub.statusCode = 404
        LimitsStub.responseBody = Data(#"{"error":"Not found"}"#.utf8)
        let loaded = try await client.engineLimits()
        XCTAssertNil(loaded)
    }

    func testAServerErrorStillThrows() async {
        LimitsStub.statusCode = 500
        LimitsStub.responseBody = Data(#"{"error":"boom"}"#.utf8)
        do {
            _ = try await client.engineLimits()
            XCTFail("a 500 must not read as no usage")
        } catch let APIError.status(code, _) {
            XCTAssertEqual(code, 500)
        } catch {
            XCTFail("unexpected \(error)")
        }
    }

    // MARK: - Tone, rows and line

    func testToneFollowsTheNearLimitBand() {
        XCTAssertEqual(EngineUsage.tone(usedPercent: 40, blocked: false), .fine)
        XCTAssertEqual(EngineUsage.tone(usedPercent: 90, blocked: false), .near)
        XCTAssertEqual(EngineUsage.tone(usedPercent: 100, blocked: false), .out)
        XCTAssertEqual(EngineUsage.tone(usedPercent: 10, blocked: true), .out)
        XCTAssertEqual(usageTone(EngineLimit(engine: "a", name: "A", driver: "claude", usedPercent: 95)), .near)
    }

    func testAnEngineWithoutWindowsReportsNothing() {
        let apiKey = EngineLimit(engine: "grok", name: "Grok", driver: "grok")
        XCTAssertFalse(EngineUsage.reportsUsage(apiKey))
        XCTAssertFalse(EngineUsage.reportsUsage(nil))
        XCTAssertTrue(usageRows(apiKey).isEmpty)
        XCTAssertTrue(EngineUsage.reportsUsage(EngineLimit(engine: "a", name: "A", driver: "claude", blocked: true)))
    }

    func testNoEngineReportingUsageLeavesTheListEmpty() {
        let engines = [
            EngineLimit(engine: "grok", name: "Grok", driver: "grok"),
            EngineLimit(engine: "local", name: "Local model", driver: "opencode"),
        ]
        XCTAssertTrue(EngineUsage.reporting(engines).isEmpty)
        XCTAssertTrue(EngineUsage.reporting([]).isEmpty)
        let withPlan = engines + [EngineLimit(engine: "claude", name: "Claude", driver: "claude", usedPercent: 5)]
        XCTAssertEqual(EngineUsage.reporting(withPlan).map(\.engine), ["claude"])
    }

    func testRowsListEveryWindowWithTheShareLeft() {
        let limit = EngineLimit(
            engine: "claude", name: "Claude", driver: "claude", window: "5-hour", usedPercent: 92,
            resetsAt: 2_000_000, windows: [
                LimitWindowUsage(window: "5-hour", usedPercent: 92, resetsAt: 2_000_000),
                LimitWindowUsage(window: "weekly", usedPercent: 40.4, resetsAt: 9_000_000),
            ]
        )
        let rows = usageRows(limit)
        XCTAssertEqual(rows.map(\.window), ["5-hour", "weekly"])
        XCTAssertEqual(rows.map(\.left), [8, 60])
        XCTAssertEqual(rows.map(\.tone), [.near, .fine])
        XCTAssertEqual(rows[1].resetsAt, Date(timeIntervalSince1970: 9_000))
    }

    func testABlockedEngineIsAtLimitInItsDecidingWindowOnly() {
        let limit = EngineLimit(
            engine: "claude", name: "Claude", driver: "claude", window: "5-hour", usedPercent: 30,
            resetsAt: 5_000_000, blocked: true, windows: [
                LimitWindowUsage(window: "5-hour", usedPercent: 30, resetsAt: 1_000_000),
                LimitWindowUsage(window: "weekly", usedPercent: 20, resetsAt: 9_000_000),
            ]
        )
        let rows = usageRows(limit)
        XCTAssertEqual(rows[0].tone, .out)
        XCTAssertEqual(rows[0].left, 0)
        XCTAssertEqual(rows[0].resetsAt, Date(timeIntervalSince1970: 5_000))
        XCTAssertEqual(rows[1].tone, .fine)
        XCTAssertEqual(rows[1].left, 80)
    }

    func testAServerWithoutAListGivesTheDecidingWindowAlone() {
        let limit = EngineLimit(
            engine: "codex", name: "Codex", driver: "codex", window: "weekly", usedPercent: 62, resetsAt: 7_000_000
        )
        let rows = usageRows(limit)
        XCTAssertEqual(rows.count, 1)
        XCTAssertEqual(rows[0].window, "weekly")
        XCTAssertEqual(rows[0].left, 38)
    }

    func testLimitLineKeepsOnlyAResetStillAhead() {
        let now = Date(timeIntervalSince1970: 1_000)
        let used = limitLine(
            EngineLimit(engine: "a", name: "Claude", driver: "claude", usedPercent: 91.6, resetsAt: 5_000_000), now: now
        )
        XCTAssertEqual(used.usedPercent, 92)
        XCTAssertFalse(used.atLimit)
        XCTAssertEqual(used.resetsAt, Date(timeIntervalSince1970: 5_000))
        let blocked = limitLine(
            EngineLimit(engine: "a", name: "Claude", driver: "claude", usedPercent: 100, resetsAt: 500_000, blocked: true),
            now: now
        )
        XCTAssertTrue(blocked.atLimit)
        XCTAssertNil(blocked.usedPercent)
        XCTAssertNil(blocked.resetsAt)
    }

    func testResetTimeNamesTheWeekdayOnlyWhenNotToday() {
        let utc = TimeZone(identifier: "UTC")!
        let locale = Locale(identifier: "en_US")
        let now = Date(timeIntervalSince1970: 1_795_277_400)
        let sameDay = limitResetTime(now.addingTimeInterval(600), now: now, locale: locale, timeZone: utc)
        let laterDay = limitResetTime(now.addingTimeInterval(3 * 86_400), now: now, locale: locale, timeZone: utc)
        XCTAssertTrue(sameDay.contains(":"))
        XCTAssertTrue(laterDay.count > sameDay.count)
    }

    // MARK: - Switch model offer

    private func failed(_ id: String, claudeUpdate: Bool = false) -> Message {
        var message = Message(id: id, role: .bot, kind: .activity, at: 1)
        message.tool = ToolActivity(name: "error: limit", ok: false, claudeUpdate: claudeUpdate ? true : nil)
        return message
    }

    private func text(_ id: String, role: Message.Role) -> Message {
        var message = Message(id: id, role: role, kind: .text, at: 1)
        message.text = "hi"
        return message
    }

    func testTheLatestFailedTurnOffersASwitch() {
        let messages = [text("u1", role: .user), failed("f1"), text("u2", role: .user), failed("f2")]
        XCTAssertEqual(switchModelOfferMessageId(in: messages), "f2")
    }

    func testNoOfferOnceSomethingWasSaidAfterTheFailure() {
        XCTAssertNil(switchModelOfferMessageId(in: [failed("f1"), text("u2", role: .user)]))
        XCTAssertNil(switchModelOfferMessageId(in: [text("u1", role: .user)]))
    }

    func testNoOfferBesideAClaudeUpdateCard() {
        XCTAssertNil(switchModelOfferMessageId(in: [failed("f1", claudeUpdate: true)]))
    }

    func testNoOfferWhileAUsageLimitOfferIsOpen() throws {
        var card = Message(id: "c1", role: .bot, kind: .options, at: 2)
        card.card = try JSONDecoder().decode(OptionCard.self, from: Data(#"""
        {"title":"Switch?","subtitle":"","options":["Codex","Not now"],"requestId":"r1",
         "quotaSwitch":{"engines":[{"instanceId":"codex","name":"Codex"}],"messageId":"u1"}}
        """#.utf8))
        XCTAssertNil(switchModelOfferMessageId(in: [failed("f1"), card]))
        card.card?.answered = "Not now"
        XCTAssertEqual(switchModelOfferMessageId(in: [failed("f1"), card]), "f1")
    }
}
