import Foundation
import XCTest
@testable import CompanionCore

private final class LookRequestStub: URLProtocol {
    static var capturedRequest: URLRequest?
    static var capturedBody: Data?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.capturedRequest = request
        Self.capturedBody = Self.readBody(from: request)
        let response = HTTPURLResponse(
            url: request.url!, statusCode: 200, httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: SeiraLookTests.botResponse)
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

private struct LookSeededGenerator: RandomNumberGenerator {
    var state: UInt64

    mutating func next() -> UInt64 {
        state = state &* 6364136223846793005 &+ 1442695040888963407
        return state
    }
}

final class SeiraLookTests: XCTestCase {
    static let botJSON = """
    {
      "id":"look-bot","threadId":"look-thread","name":"Dina Pratama","title":"Analyst",
      "description":"Reads reports.","notifications":true,"color":"blue","unread":false,
      "modelSelection":{"instanceId":"local","model":"default"},"createdAt":1786742441013,
      "mascotBody":"bob","mascotFaceColor":"black","mascotExpression":"happy",
      "mascotAccessories":["glasses","ribbon"]
    }
    """

    static let botResponse = Data("{\"bot\":\(botJSON)}".utf8)

    private var session: URLSession!
    private var client: CompanionClient!

    override func setUp() {
        super.setUp()
        LookRequestStub.capturedRequest = nil
        LookRequestStub.capturedBody = nil
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [LookRequestStub.self]
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

    private func jsonObject(_ patch: BotLookPatch) throws -> [String: Any] {
        let data = try JSONEncoder().encode(patch)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    // MARK: - Catalog

    func testHairCatalogHasSeventeenStyles() {
        XCTAssertEqual(SeiraLookCatalog.hairIds.count, 17)
        XCTAssertEqual(SeiraLookCatalog.hairIds.first, "seira")
        XCTAssertEqual(SeiraLookCatalog.hairIds.last, "none")
    }

    func testUnknownAndRetiredHairReadAsSeira() {
        XCTAssertEqual(SeiraLookCatalog.hair("pumpkin"), "seira")
        XCTAssertEqual(SeiraLookCatalog.hair("cursor"), "seira")
        XCTAssertEqual(SeiraLookCatalog.hair("nonsense"), "seira")
        XCTAssertEqual(SeiraLookCatalog.hair(nil), "seira")
        XCTAssertEqual(SeiraLookCatalog.hair("twin-tails"), "twin-tails")
    }

    func testUnknownAccessoryIsDropped() {
        XCTAssertEqual(SeiraLookCatalog.accessories(["monocle", "glasses"]), ["glasses"])
        XCTAssertEqual(SeiraLookCatalog.accessories(nil), [])
    }

    func testTwoAccessoriesInOneSlotKeepTheFirst() {
        XCTAssertEqual(SeiraLookCatalog.accessories(["sunglasses", "glasses", "bowtie"]), ["sunglasses", "bowtie"])
    }

    func testMoreThanThreeAccessoriesKeepThree() {
        let result = SeiraLookCatalog.accessories(["glasses", "earring", "ribbon", "cap", "scarf"])
        XCTAssertEqual(result, ["glasses", "earring", "ribbon"])
    }

    func testEveryAccessoryHasASlot() {
        XCTAssertEqual(SeiraLookCatalog.accessoryIds.count, 12)
        for id in SeiraLookCatalog.accessoryIds {
            XCTAssertNotNil(SeiraLookCatalog.slot(of: id), id)
        }
        XCTAssertEqual(SeiraLookCatalog.slot(of: "headset-mic"), .ears)
        XCTAssertNil(SeiraLookCatalog.slot(of: "monocle"))
    }

    func testToggleTurnsAnAccessoryOn() {
        XCTAssertEqual(SeiraLookCatalog.toggle("glasses", in: []), ["glasses"])
    }

    func testToggleTurnsAnAccessoryOff() {
        XCTAssertEqual(SeiraLookCatalog.toggle("glasses", in: ["glasses", "bowtie"]), ["bowtie"])
    }

    func testToggleReplacesTheAccessoryInTheSameSlot() {
        let result = SeiraLookCatalog.toggle("sunglasses", in: ["earring", "glasses", "scarf"])
        XCTAssertEqual(result, ["earring", "scarf", "sunglasses"])
    }

    func testToggleDropsTheOldestOnAFourthSlot() {
        let result = SeiraLookCatalog.toggle("cap", in: ["glasses", "earring", "ribbon"])
        XCTAssertEqual(result, ["earring", "ribbon", "cap"])
    }

    func testToggleLeavesTheListForAnUnknownId() {
        XCTAssertEqual(SeiraLookCatalog.toggle("monocle", in: ["glasses"]), ["glasses"])
    }

    func testToggleNeverExceedsTheLimitOrRepeatsASlot() {
        var worn: [String] = []
        for id in SeiraLookCatalog.accessoryIds + SeiraLookCatalog.accessoryIds.reversed() {
            worn = SeiraLookCatalog.toggle(id, in: worn)
            XCTAssertLessThanOrEqual(worn.count, SeiraLookCatalog.maxAccessories)
            let slots = worn.compactMap { SeiraLookCatalog.slot(of: $0) }
            XCTAssertEqual(Set(slots).count, slots.count)
        }
    }

    func testColorCatalog() {
        XCTAssertEqual(SeiraLookCatalog.starterColors.count, 10)
        XCTAssertFalse(SeiraLookCatalog.starterColors.contains("silver"))
        XCTAssertFalse(SeiraLookCatalog.starterColors.contains("chrome"))
        XCTAssertEqual(SeiraLookCatalog.colorHex.count, 12)
        for name in SeiraLookCatalog.starterColors {
            XCTAssertNotNil(SeiraLookCatalog.colorHex[name], name)
        }
        XCTAssertEqual(SeiraLookCatalog.color("silver"), "silver")
        XCTAssertEqual(SeiraLookCatalog.color("magenta"), "green")
        XCTAssertEqual(SeiraLookCatalog.color(nil), "green")
    }

    // MARK: - Look

    func testLookInitNormalizesEveryField() {
        let look = SeiraLook(
            hair: "ghost", color: "magenta", faceInk: "grey",
            expression: "dancing", accessories: ["monocle", "glasses"]
        )
        XCTAssertEqual(look.hair, "seira")
        XCTAssertEqual(look.color, "green")
        XCTAssertNil(look.faceInk)
        XCTAssertNil(look.expression)
        XCTAssertEqual(look.accessories, ["glasses"])
    }

    func testLookInitKeepsValidFields() {
        let look = SeiraLook(
            hair: "bun", color: "teal", faceInk: "black",
            expression: "proud", accessories: ["tiara"]
        )
        XCTAssertEqual(look.hair, "bun")
        XCTAssertEqual(look.color, "teal")
        XCTAssertEqual(look.faceInk, "black")
        XCTAssertEqual(look.expression, "proud")
        XCTAssertEqual(look.accessories, ["tiara"])
    }

    func testSeiraLookIsSeiraHairWithNoExtras() {
        XCTAssertEqual(SeiraLook.seira.hair, "seira")
        XCTAssertEqual(SeiraLook.seira.color, "red")
        XCTAssertNil(SeiraLook.seira.faceInk)
        XCTAssertNil(SeiraLook.seira.expression)
        XCTAssertTrue(SeiraLook.seira.accessories.isEmpty)
    }

    func testLookReadsABotWithTheNewFields() throws {
        let bot = try JSONDecoder().decode(Bot.self, from: Data(Self.botJSON.utf8))
        XCTAssertEqual(bot.mascotFaceColor, "black")
        XCTAssertEqual(bot.mascotAccessories, ["glasses", "ribbon"])
        let look = SeiraLook(bot: bot)
        XCTAssertEqual(look, SeiraLook(
            hair: "bob", color: "blue", faceInk: "black", expression: "happy", accessories: ["glasses", "ribbon"]
        ))
    }

    func testLookReadsABotWithoutTheNewFields() throws {
        let json = """
        {
          "id":"old-bot","threadId":"old-thread","name":"Dina Pratama","title":"","description":"",
          "notifications":true,"color":"pink","unread":false,
          "modelSelection":{"instanceId":"local","model":"default"},"createdAt":1786742441013,
          "mascotBody":"cursor"
        }
        """
        let bot = try JSONDecoder().decode(Bot.self, from: Data(json.utf8))
        XCTAssertNil(bot.mascotFaceColor)
        XCTAssertNil(bot.mascotAccessories)
        let look = SeiraLook(bot: bot)
        XCTAssertEqual(look.hair, "seira")
        XCTAssertEqual(look.color, "pink")
        XCTAssertNil(look.faceInk)
        XCTAssertTrue(look.accessories.isEmpty)
    }

    // MARK: - Cast

    private static let castJSON = """
    [
      {"id":"mira","name":"Mira","mascotBody":"long-straight","mascotAccessories":["hairpin"],"color":"orange"},
      {"id":"lumi","name":"Lumi","mascotBody":"bob","mascotAccessories":["headset-mic"],"color":"cyan"},
      {"id":"aldo","name":"Aldo","mascotBody":"slick-back","mascotAccessories":[],"color":"chrome"}
    ]
    """

    func testCastDecodesFromJSON() throws {
        let cast = try SeiraCast.decode(Data(Self.castJSON.utf8))
        XCTAssertEqual(cast.map(\.id), ["mira", "lumi", "aldo"])
        XCTAssertEqual(cast[1].look, SeiraLook(hair: "bob", color: "cyan", accessories: ["headset-mic"]))
        XCTAssertNil(cast[1].look.faceInk)
        XCTAssertNil(cast[1].look.expression)
    }

    func testSurpriseNeverReturnsTheCurrentLook() throws {
        let cast = try SeiraCast.decode(Data(Self.castJSON.utf8))
        let current = cast[0].look
        var generator = LookSeededGenerator(state: 42)
        for _ in 0..<50 {
            let pick = SeiraCast.surprise(from: cast, current: current, using: &generator)
            XCTAssertNotEqual(pick, current)
        }
    }

    func testSurpriseFallsBackWhenNoOtherLookExists() throws {
        let cast = try SeiraCast.decode(Data(Self.castJSON.utf8))
        var generator = LookSeededGenerator(state: 7)
        let single = [cast[0]]
        XCTAssertEqual(
            SeiraCast.surprise(from: single, current: cast[0].look, using: &generator),
            cast[0].look
        )
        XCTAssertEqual(
            SeiraCast.surprise(from: [], current: SeiraLook.seira, using: &generator),
            SeiraLook.seira
        )
    }

    // MARK: - Patch

    func testPatchWithOneChangedFieldEncodesExactlyOneKey() throws {
        let old = SeiraLook(hair: "bob", color: "blue")
        let new = SeiraLook(hair: "bob", color: "teal")
        let body = try jsonObject(BotLookPatch(from: old, to: new))
        XCTAssertEqual(body.keys.sorted(), ["color"])
        XCTAssertEqual(body["color"] as? String, "teal")
    }

    func testPatchEncodesHairAsMascotBody() throws {
        let body = try jsonObject(BotLookPatch(from: SeiraLook.seira, to: SeiraLook(hair: "buzz", color: "red")))
        XCTAssertEqual(body.keys.sorted(), ["mascotBody"])
        XCTAssertEqual(body["mascotBody"] as? String, "buzz")
    }

    func testClearingFaceInkEncodesNull() throws {
        let old = SeiraLook(hair: "seira", color: "red", faceInk: "black")
        let new = SeiraLook(hair: "seira", color: "red", faceInk: nil)
        let body = try jsonObject(BotLookPatch(from: old, to: new))
        XCTAssertEqual(body.keys.sorted(), ["mascotFaceColor"])
        XCTAssertTrue(body["mascotFaceColor"] is NSNull)
    }

    func testClearingExpressionEncodesNullAndSettingEncodesTheValue() throws {
        let plain = SeiraLook(hair: "seira", color: "red")
        let happy = SeiraLook(hair: "seira", color: "red", expression: "happy")
        let cleared = try jsonObject(BotLookPatch(from: happy, to: plain))
        XCTAssertTrue(cleared["mascotExpression"] is NSNull)
        let set = try jsonObject(BotLookPatch(from: plain, to: happy))
        XCTAssertEqual(set["mascotExpression"] as? String, "happy")
    }

    func testRemovingAllExtrasEncodesAnEmptyList() throws {
        let old = SeiraLook(hair: "seira", color: "red", accessories: ["glasses"])
        let new = SeiraLook(hair: "seira", color: "red")
        let body = try jsonObject(BotLookPatch(from: old, to: new))
        XCTAssertEqual(body.keys.sorted(), ["mascotAccessories"])
        XCTAssertEqual((body["mascotAccessories"] as? [Any])?.count, 0)
    }

    func testPatchWithNoChangeIsEmpty() throws {
        let look = SeiraLook(hair: "bun", color: "pink", faceInk: "white", expression: "idle", accessories: ["cap"])
        let patch = BotLookPatch(from: look, to: look)
        XCTAssertTrue(patch.isEmpty)
        XCTAssertTrue(try jsonObject(patch).isEmpty)
        XCTAssertFalse(BotLookPatch(from: look, to: SeiraLook.seira).isEmpty)
    }

    // MARK: - Client

    func testUpdateLookSendsPatchToTheBotRouteWithOnlyChangedKeys() async throws {
        let old = SeiraLook(hair: "bob", color: "blue", accessories: ["glasses"])
        let new = SeiraLook(hair: "bob", color: "blue", faceInk: "black", accessories: ["glasses"])

        let bot = try await client.updateLook(botId: "look-bot", patch: BotLookPatch(from: old, to: new))

        let request = try XCTUnwrap(LookRequestStub.capturedRequest)
        XCTAssertEqual(request.httpMethod, "PATCH")
        XCTAssertEqual(request.url?.path, "/api/bots/look-bot")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Content-Type"), "application/json")
        let data = try XCTUnwrap(LookRequestStub.capturedBody)
        let body = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(body.keys.sorted(), ["mascotFaceColor"])
        XCTAssertEqual(body["mascotFaceColor"] as? String, "black")
        XCTAssertEqual(bot.id, "look-bot")
        XCTAssertEqual(bot.mascotAccessories, ["glasses", "ribbon"])
    }

    func testUpdateLookRejectsAnInvalidRouteID() async {
        do {
            _ = try await client.updateLook(botId: "../bots", patch: BotLookPatch())
            XCTFail("expected badURL")
        } catch {
            XCTAssertNil(LookRequestStub.capturedRequest)
        }
    }
}
