import Foundation
import XCTest
@testable import CompanionCore

private final class RedesignStub: URLProtocol {
    struct Reply {
        var status: Int
        var body: String
    }

    static var replies: [Reply] = []
    static var requests: [URLRequest] = []
    static var bodies: [Data?] = []

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.requests.append(request)
        Self.bodies.append(Self.readBody(from: request))
        let reply = Self.replies.isEmpty ? Reply(status: 200, body: "{}") : Self.replies.removeFirst()
        let response = HTTPURLResponse(
            url: request.url!, statusCode: reply.status, httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(reply.body.utf8))
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
            guard count > 0 else { break }
            data.append(buffer, count: count)
        }
        return data
    }
}

final class RedesignClientTests: XCTestCase {
    private var session: URLSession!
    private var client: CompanionClient!

    override func setUp() {
        super.setUp()
        RedesignStub.replies = []
        RedesignStub.requests = []
        RedesignStub.bodies = []
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [RedesignStub.self]
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

    private func reply(_ body: String, _ status: Int = 200) {
        RedesignStub.replies.append(.init(status: status, body: body))
    }

    private func line(_ index: Int = 0) -> String {
        let request = RedesignStub.requests[index]
        let query = request.url?.query.map { "?\($0)" } ?? ""
        return "\(request.httpMethod ?? "") \(request.url?.path ?? "")\(query)"
    }

    private func body(_ index: Int = 0) throws -> [String: Any] {
        let data = try XCTUnwrap(RedesignStub.bodies[index])
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    private func botJSON(approvalMode: String? = nil) -> String {
        let mode = approvalMode.map { #","approvalMode":"\#($0)""# } ?? ""
        return """
        {"id":"bot-1","threadId":"t1","name":"Ayu","title":"","description":"","notifications":true,
        "color":"green","unread":false,"modelSelection":{"instanceId":"i","model":"m"},"createdAt":1\(mode),
        "tasks":[{"threadId":"t1","title":"Task 1","createdAt":1\(mode)}]}
        """
    }

    func testCreateBotWithoutASpecSendsNoBody() async throws {
        reply(#"{"bot":\#(botJSON())}"#)
        _ = try await client.createBot()
        XCTAssertEqual(line(), "POST /api/bots")
        XCTAssertNil(RedesignStub.requests[0].httpBody)
        XCTAssertNil(RedesignStub.requests[0].httpBodyStream)
    }

    func testCreateBotSendsOnlyTheFieldsItWasGiven() async throws {
        reply(#"{"bot":\#(botJSON())}"#)
        _ = try await client.createBot(NewBot(
            name: "Ayu", description: "Answers mail", soul: "Kind.", replyStyle: .conversational,
            mascotAccessories: ["hat"], retryKey: "retry-1"
        ))
        let sent = try body()
        XCTAssertEqual(Set(sent.keys), ["name", "description", "soul", "replyStyle", "mascotAccessories", "retryKey"])
        XCTAssertEqual(sent["replyStyle"] as? String, "conversational")
        XCTAssertEqual(sent["mascotAccessories"] as? [String], ["hat"])
    }

    func testSteerQueuedPostsTheThreadAndReadsTheOutcome() async throws {
        reply(#"{"ok":true,"steered":true,"threadId":"t1","queueIds":["q1","q2"]}"#)
        let result = try await client.steerQueued(queueId: "q1", to: .bot(id: "bot-1", threadId: "t1"))
        XCTAssertEqual(result, .steered(queueIds: ["q1", "q2"]))
        XCTAssertEqual(line(), "POST /api/bots/bot-1/queue/q1/steer")
        XCTAssertEqual(try body()["threadId"] as? String, "t1")
    }

    func testSteerQueuedInARoomUsesTheGroupRouteAndKeepsABlockedEntryQueued() async throws {
        reply(#"{"ok":true,"queued":true,"threadId":"t1","blockedBy":"images"}"#)
        let result = try await client.steerQueued(queueId: "q1", to: .room(id: "g1", threadId: "t1"))
        XCTAssertEqual(result, .stillQueued(blockedBy: "images"))
        XCTAssertEqual(line(), "POST /api/groups/g1/queue/q1/steer")
    }

    func testSteerQueuedReadsADrainedEntryAsGoneAndAnUnknownRouteAsAnOldComputer() async throws {
        reply(#"{"error":"no such queued message"}"#, 404)
        let gone = try await client.steerQueued(queueId: "q1", to: .bot(id: "bot-1", threadId: "t1"))
        XCTAssertEqual(gone, .gone)
        reply(#"{"error":"no route"}"#, 404)
        do {
            _ = try await client.steerQueued(queueId: "q1", to: .bot(id: "bot-1", threadId: "t1"))
            XCTFail("expected a failure")
        } catch let APIError.status(code, message) {
            XCTAssertEqual(code, 404)
            XCTAssertTrue(message?.contains("too old") == true)
        }
    }

    func testSetApprovalModePatchesTheTaskAndReturnsTheBot() async throws {
        reply(#"{"task":{"threadId":"t1","title":"Task 1","updatedAt":1},"bot":\#(botJSON(approvalMode: "edits"))}"#)
        let bot = try await client.setApprovalMode(botId: "bot-1", threadId: "t1", mode: .edits)
        XCTAssertEqual(bot.approvalLevel(in: "t1"), .edits)
        XCTAssertEqual(line(), "PATCH /api/bots/bot-1/tasks/t1")
        XCTAssertEqual(Set(try body().keys), ["approvalMode"])
    }

    func testSetApprovalModeSendsTheAcknowledgementOnlyWhenGiven() async throws {
        reply(#"{"bot":\#(botJSON(approvalMode: "auto"))}"#)
        _ = try await client.setApprovalMode(botId: "bot-1", threadId: "t1", mode: .auto, acknowledgeLocalAuto: true)
        let sent = try body()
        XCTAssertEqual(sent["approvalMode"] as? String, "auto")
        XCTAssertEqual(sent["acknowledgeLocalAuto"] as? Bool, true)
    }

    func testCustomCannotBeSentFromAPhone() async {
        do {
            _ = try await client.setApprovalMode(botId: "bot-1", threadId: "t1", mode: .custom)
            XCTFail("expected a failure")
        } catch {}
        XCTAssertTrue(RedesignStub.requests.isEmpty)
    }

    func testRefusalsAreToldApartByTheirWording() {
        func refusal(_ code: Int, _ text: String) -> ApprovalModeRefusal {
            ApprovalModeRefusal.from(APIError.status(code: code, message: text))
        }
        XCTAssertEqual(
            refusal(403, "On a shared company workspace Full access comes only from the operator's dedicated policy"),
            .fullNotAllowedHere("On a shared company workspace Full access comes only from the operator's dedicated policy")
        )
        guard case .leaveCustomOnDesktop = refusal(403, "Leaving Custom approval requires confirmation in the packaged desktop app") else {
            return XCTFail("leave custom")
        }
        guard case .customOnDesktop = refusal(403, "Custom access requires trusted desktop confirmation") else {
            return XCTFail("custom")
        }
        guard case .localAutoNeedsAcknowledgement = refusal(400, "Auto mode on this computer requires confirming the warning first (acknowledgeLocalAuto)") else {
            return XCTFail("local auto")
        }
        guard case .ownerOnly = refusal(403, "On this Cloud only its owner can change how a bot asks for approval, or its default model.") else {
            return XCTFail("owner only")
        }
        guard case .providerUnsupported = refusal(400, "This provider does not support the selected approval level") else {
            return XCTFail("provider")
        }
        guard case .stillConfirming = refusal(409, "the bot's approval mode is still being confirmed") else {
            return XCTFail("confirming")
        }
        guard case .other = refusal(500, "boom") else { return XCTFail("other") }
        guard case .other = ApprovalModeRefusal.from(APIError.transport("offline")) else { return XCTFail("transport") }
    }

    func testDeleteBotUsesTheBotRoute() async throws {
        reply(#"{"ok":true}"#)
        try await client.deleteBot(botId: "bot-1")
        XCTAssertEqual(line(), "DELETE /api/bots/bot-1")
    }

    func testActivityReadsRowsAndPassesTheLimit() async throws {
        reply(#"{"rows":[{"at":"2026-11-26T01:02:03Z","threadId":"t1","turnId":"u1","tool":"shell","label":"Ran a command","labelKey":"ran","outcome":"ran","summary":"ls"}]}"#)
        let rows = try await client.activity(botId: "bot-1", limit: 50)
        XCTAssertEqual(rows.first?.label, "Ran a command")
        XCTAssertEqual(rows.first?.outcome, "ran")
        XCTAssertEqual(line(), "GET /api/bots/bot-1/activity?limit=50")
    }

    func testSkillsDecodeWithTheirOrigin() async throws {
        reply(#"{"skills":[{"name":"research","description":"Finds things","enabled":true,"editable":false,"origin":"library","warnings":["slow"]}]}"#)
        let skill = try await client.skills(botId: "bot-1").first
        XCTAssertEqual(skill?.name, "research")
        XCTAssertEqual(skill?.enabled, true)
        XCTAssertEqual(skill?.origin, "library")
        XCTAssertEqual(skill?.warnings, ["slow"])
        XCTAssertEqual(line(), "GET /api/bots/bot-1/skills")
    }

    func testBotDataDecodesTheSheetAndTables() async throws {
        reply(#"{"sheet":{"version":1,"botId":"bot-1","updatedAt":"2026-11-26","sources":[],"cards":[{"id":"c_1","kind":"table","title":"Sales","status":"ready","sql":"select 1","result":"r1","rowCount":2,"by":"bot","createdAt":"a","updatedAt":"b","columns":[{"name":"n","type":"INTEGER"}],"chart":{"mark":"bar"}}]},"tables":[{"name":"sales","rowCount":2,"columns":[{"name":"n","type":"INTEGER","nullPct":0}]}]}"#)
        let data = try await client.botData(botId: "bot-1")
        XCTAssertEqual(data.sheet.cards?.first?.title, "Sales")
        XCTAssertEqual(data.sheet.cards?.first?.chart?["mark"], .string("bar"))
        XCTAssertEqual(data.tables?.first?.rowCount, 2)
        XCTAssertEqual(line(), "GET /api/bots/bot-1/data")
    }

    func testDataPagePostsOneSourceAndSortAndDecodesMixedCells() async throws {
        reply(#"{"columns":[{"name":"n","type":"BIGINT"}],"rows":[[1],["9007199254740993"],[null]],"rowCount":3,"offset":0}"#)
        let page = try await client.dataPage(
            botId: "bot-1",
            request: DataPageRequest(table: "sales", sortColumn: "n", sortDescending: true)
        )
        XCTAssertEqual(page.rows.count, 3)
        XCTAssertEqual(page.rows[1][0].display, "9007199254740993")
        XCTAssertEqual(page.rows[0][0].display, "1")
        XCTAssertEqual(line(), "POST /api/bots/bot-1/data/page")
        let sent = try body()
        XCTAssertEqual(sent["table"] as? String, "sales")
        XCTAssertEqual((sent["sort"] as? [String: String])?["direction"], "desc")
        XCTAssertNil(sent["cardId"])
    }

    func testDataPageRefusesBothOrNeitherSource() async {
        do {
            _ = try await client.dataPage(botId: "bot-1", request: DataPageRequest(table: "a", cardId: "c_1"))
            XCTFail("both")
        } catch {}
        do {
            _ = try await client.dataPage(botId: "bot-1", request: DataPageRequest())
            XCTFail("neither")
        } catch {}
        XCTAssertTrue(RedesignStub.requests.isEmpty)
    }

    func testDataErrorsAreObjectsAndStillReachThePerson() async {
        reply(#"{"error":{"code":"table_not_found","message":"No table sales."}}"#, 404)
        do {
            _ = try await client.botData(botId: "bot-1")
            XCTFail("expected a failure")
        } catch let APIError.status(code, message) {
            XCTAssertEqual(code, 404)
            XCTAssertEqual(message, "No table sales.")
        } catch {
            XCTFail("\(error)")
        }
    }

    func testTeamMapDecodesLinksAndHandoffs() async throws {
        reply(#"{"collaborations":[{"groupId":"g1","botIds":["a","b"],"lastAt":5}],"queued":[],"running":[{"sourceBotId":"a","targetBotId":"b","threadId":"t1"}]}"#)
        let map = try await client.teamMap()
        XCTAssertEqual(map.collaborations.first?.botIds, ["a", "b"])
        XCTAssertEqual(map.running.first?.targetBotId, "b")
        XCTAssertEqual(line(), "GET /api/team-map")
    }

    func testTeamMemoryReadsASectionByQueryAndKeepsTheRevision() async throws {
        reply(#"{"section":"Sales","label":"Sales","entries":[{"id":"m1","kind":"person","name":"Budi","detail":"","aliases":[],"status":"accepted","revision":"r1","updatedAt":3,"source":{"botId":"a","botName":"Ayu","threadId":"t1","at":2}}],"loadedIds":["m1"]}"#)
        let page = try await client.teamMemory(section: "Sales")
        XCTAssertEqual(page.entries.first?.revision, "r1")
        XCTAssertEqual(page.loadedIds, ["m1"])
        XCTAssertEqual(line(), "GET /api/team-memory?section=Sales")
    }

    func testTeamMemoryWritesCarryTheSectionAndTheRevision() async throws {
        let edit = #"{"entries":[],"loadedIds":[]}"#
        reply(edit, 201)
        reply(edit)
        reply(edit)
        _ = try await client.addTeamMemory(section: "", kind: .term, name: "ARR", detail: "Annual recurring revenue", aliases: ["arr"])
        _ = try await client.updateTeamMemory(section: "Sales", entryId: "m1", revision: "r1", detail: "New", pinned: true)
        _ = try await client.deleteTeamMemory(section: "Sales", entryId: "m1", revision: "r2")

        XCTAssertEqual(line(0), "POST /api/team-memory?section=")
        XCTAssertEqual(try body(0)["kind"] as? String, "term")
        XCTAssertEqual(line(1), "PATCH /api/team-memory/m1?section=Sales")
        let patch = try body(1)
        XCTAssertEqual(patch["expectedRevision"] as? String, "r1")
        XCTAssertEqual(Set(patch.keys), ["expectedRevision", "detail", "pinned"])
        XCTAssertEqual(line(2), "DELETE /api/team-memory/m1?section=Sales&revision=r2")
    }

    func testAcceptingAProposedMemoryRidesTheSamePatch() async throws {
        reply(#"{"entries":[],"loadedIds":[]}"#)
        _ = try await client.updateTeamMemory(section: "", entryId: "m1", revision: "r1", accept: true)
        XCTAssertEqual(try body()["accept"] as? Bool, true)
    }

    func testDeleteConnectorAccountNamesBothIds() async throws {
        reply(#"{"ok":true}"#)
        try await client.deleteConnectorAccount(connectorId: "gmail", accountId: "ca_123")
        XCTAssertEqual(line(), "DELETE /api/connectors/gmail/accounts/ca_123")
    }

    func testProfilePatchSendsSoulAndReplyStyle() async throws {
        reply(#"{"bot":\#(botJSON())}"#)
        _ = try await client.updateProfile(botId: "bot-1", patch: BotProfilePatch(soul: "Be brief.", replyStyle: .default))
        XCTAssertEqual(line(), "PATCH /api/bots/bot-1/profile")
        let sent = try body()
        XCTAssertEqual(Set(sent.keys), ["soul", "replyStyle"])
        XCTAssertEqual(sent["replyStyle"] as? String, "default")
    }

    func testComputerControlReadsAndTakesWithALease() async throws {
        reply(#"{"held":false}"#)
        reply(#"{"held":true,"owned":true,"acquired":true,"heldSinceMs":10}"#)
        reply(#"{"held":false,"released":true}"#)
        let before = try await client.computerControl(botId: "bot-1", threadId: "t1")
        XCTAssertFalse(before.held)
        let taken = try await client.controlComputer(botId: "bot-1", action: .take, leaseId: "lease-1", threadId: "t1")
        XCTAssertEqual(taken.owned, true)
        _ = try await client.controlComputer(botId: "bot-1", action: .release, leaseId: "lease-1", threadId: "t1")

        XCTAssertEqual(line(0), "GET /api/bots/bot-1/computer/control?threadId=t1")
        XCTAssertEqual(line(1), "POST /api/bots/bot-1/computer/control?threadId=t1")
        XCTAssertEqual(try body(1)["action"] as? String, "take")
        XCTAssertEqual(try body(1)["controlLeaseId"] as? String, "lease-1")
        XCTAssertEqual(try body(2)["action"] as? String, "release")
    }

    func testUnsafeIdsNeverReachTheWire() async {
        do { try await client.deleteBot(botId: "../x"); XCTFail("bot") } catch {}
        do { try await client.deleteConnectorAccount(connectorId: "gmail", accountId: ""); XCTFail("account") } catch {}
        XCTAssertTrue(RedesignStub.requests.isEmpty)
    }
}
