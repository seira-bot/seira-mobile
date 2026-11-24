import Foundation
import XCTest
@testable import CompanionCore

private final class WorkspacesStub: URLProtocol {
    static var statusCode = 200
    static var responseBody = Data()
    static var capturedRequest: URLRequest?
    static var capturedBody: Data?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.capturedRequest = request
        if let stream = request.httpBodyStream {
            stream.open()
            var data = Data()
            var buffer = [UInt8](repeating: 0, count: 1024)
            while stream.hasBytesAvailable {
                let count = stream.read(&buffer, maxLength: buffer.count)
                if count <= 0 { break }
                data.append(buffer, count: count)
            }
            stream.close()
            Self.capturedBody = data
        } else {
            Self.capturedBody = request.httpBody
        }
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

/// The workspace list and the two routes. The Kotlin twins are `WorkspacesTest` and
/// `WorkspacesClientTest`.
final class WorkspacesTests: XCTestCase {
    private var session: URLSession!
    private var client: CompanionClient!

    override func setUp() {
        super.setUp()
        WorkspacesStub.statusCode = 200
        WorkspacesStub.responseBody = Data()
        WorkspacesStub.capturedRequest = nil
        WorkspacesStub.capturedBody = nil
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [WorkspacesStub.self]
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

    private func decode(_ text: String) throws -> [PhoneWorkspace] {
        try JSONDecoder().decode(PhoneWorkspaceList.self, from: Data(text.utf8)).workspaces
    }

    // MARK: - Decoding

    func testReadsTheListWithEveryField() throws {
        let list = try decode(#"""
        {"workspaces":[
          {"id":"default","name":null,"color":null,"current":true,"state":"ready"},
          {"id":"trading","name":"Trading","color":"teal","current":false,"state":"waiting","waitingFor":"Nadia"}
        ]}
        """#)
        XCTAssertEqual(list.map(\.id), ["default", "trading"])
        XCTAssertTrue(list[0].current)
        XCTAssertEqual(list[1].color, "teal")
        XCTAssertEqual(list[1].availability, .waiting)
        XCTAssertEqual(list[1].waitingFor, "Nadia")
    }

    func testAnOlderShapeWithOnlyAnIDStillReads() throws {
        let only = try XCTUnwrap(try decode(#"{"workspaces":[{"id":"a"}]}"#).first)
        XCTAssertFalse(only.current)
        XCTAssertEqual(only.availability, .ready)
        XCTAssertNil(only.name)
    }

    func testAMissingListIsEmpty() throws {
        XCTAssertEqual(try decode("{}"), [])
    }

    func testAStateThisBuildDoesNotKnowIsUnavailableNotAFailure() throws {
        let list = try decode(#"{"workspaces":[{"id":"a","state":"hibernating"},{"id":"b"}]}"#)
        XCTAssertEqual(list[0].availability, .unavailable)
        XCTAssertEqual(list[0].state, "hibernating")
        XCTAssertEqual(list.count, 2)
    }

    // MARK: - Policy

    func testNormalizingDropsBlankAndRepeatedIDsAndKeepsOneOpenWorkspace() {
        let list = WorkspacePolicy.normalized([
            PhoneWorkspace(id: "a", current: true),
            PhoneWorkspace(id: " "),
            PhoneWorkspace(id: "a", name: "again"),
            PhoneWorkspace(id: "b", current: true),
            PhoneWorkspace(id: "c"),
        ])
        XCTAssertEqual(list.map(\.id), ["a", "b", "c"])
        XCTAssertEqual(list.map(\.current), [true, false, false])
        XCTAssertNil(list[0].name)
    }

    func testTheServerOrderIsKept() {
        let ids = ["z", "m", "a"]
        XCTAssertEqual(WorkspacePolicy.normalized(ids.map { PhoneWorkspace(id: $0) }).map(\.id), ids)
    }

    func testNoPickerForOneWorkspaceOrNone() {
        XCTAssertFalse(WorkspacePolicy.showsPicker([]))
        XCTAssertFalse(WorkspacePolicy.showsPicker([PhoneWorkspace(id: "a", current: true)]))
        XCTAssertFalse(WorkspacePolicy.showsPicker([PhoneWorkspace(id: "a"), PhoneWorkspace(id: "a")]))
        XCTAssertTrue(WorkspacePolicy.showsPicker([PhoneWorkspace(id: "a"), PhoneWorkspace(id: "b")]))
    }

    func testAnUnnamedWorkspaceIsThisComputer() {
        XCTAssertEqual(WorkspacePolicy.displayName(PhoneWorkspace(id: "a")), "This computer")
        XCTAssertEqual(WorkspacePolicy.displayName(PhoneWorkspace(id: "a", name: "  ")), "This computer")
        XCTAssertEqual(WorkspacePolicy.displayName(PhoneWorkspace(id: "a", name: " Trading ")), "Trading")
    }

    func testStatusLinesFollowTheDesktop() {
        XCTAssertEqual(WorkspacePolicy.statusLine(PhoneWorkspace(id: "a", current: true)), "Open")
        XCTAssertNil(WorkspacePolicy.statusLine(PhoneWorkspace(id: "a")))
        XCTAssertEqual(
            WorkspacePolicy.statusLine(PhoneWorkspace(id: "a", state: "waiting", waitingFor: "Nadia")),
            "Waiting for Nadia to finish"
        )
        XCTAssertEqual(
            WorkspacePolicy.statusLine(PhoneWorkspace(id: "a", state: "waiting", waitingFor: " ")),
            "Waiting for work to finish"
        )
        XCTAssertEqual(
            WorkspacePolicy.statusLine(PhoneWorkspace(id: "a", state: "unavailable")),
            "Not available"
        )
    }

    func testOnlyAnotherAvailableWorkspaceCanBeOpened() {
        XCTAssertFalse(WorkspacePolicy.canSwitch(PhoneWorkspace(id: "a", current: true)))
        XCTAssertFalse(WorkspacePolicy.canSwitch(PhoneWorkspace(id: "a", state: "unavailable")))
        XCTAssertTrue(WorkspacePolicy.canSwitch(PhoneWorkspace(id: "a")))
        XCTAssertTrue(WorkspacePolicy.canSwitch(PhoneWorkspace(id: "a", state: "waiting")))
    }

    // MARK: - Client

    func testListsTheWorkspacesWithThePairedToken() async throws {
        WorkspacesStub.responseBody = Data(#"{"workspaces":[{"id":"a","current":true},{"id":"b","name":"Pentest"}]}"#.utf8)
        let loaded = try await client.workspaces()
        let list = try XCTUnwrap(loaded)
        let request = try XCTUnwrap(WorkspacesStub.capturedRequest)
        XCTAssertEqual(request.httpMethod, "GET")
        XCTAssertEqual(request.url?.path, "/api/phones/self/workspaces")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer paired-token")
        XCTAssertEqual(list.map(\.id), ["a", "b"])
    }

    func testAComputerWithoutTheRouteGivesNoList() async throws {
        WorkspacesStub.statusCode = 404
        WorkspacesStub.responseBody = Data(#"{"error":"Not found"}"#.utf8)
        let loaded = try await client.workspaces()
        XCTAssertNil(loaded)
    }

    func testAPairingThatMayNotReadItGivesNoList() async throws {
        WorkspacesStub.statusCode = 403
        WorkspacesStub.responseBody = Data(#"{"error":"Forbidden"}"#.utf8)
        let loaded = try await client.workspaces()
        XCTAssertNil(loaded)
    }

    func testAServerErrorIsNotReadAsNoWorkspaces() async {
        WorkspacesStub.statusCode = 500
        WorkspacesStub.responseBody = Data(#"{"error":"boom"}"#.utf8)
        do {
            _ = try await client.workspaces()
            XCTFail("a 500 must not read as no workspaces")
        } catch let APIError.status(code, _) {
            XCTAssertEqual(code, 500)
        } catch {
            XCTFail("unexpected error \(error)")
        }
    }

    func testSwitchingSendsOnlyTheIDAndReadsTheListBack() async throws {
        WorkspacesStub.responseBody = Data(
            #"{"workspaces":[{"id":"a","current":true,"state":"waiting","waitingFor":"Nadia"},{"id":"b"}]}"#.utf8
        )
        let list = try await client.switchWorkspace("b")
        let request = try XCTUnwrap(WorkspacesStub.capturedRequest)
        XCTAssertEqual(request.httpMethod, "POST")
        XCTAssertEqual(request.url?.path, "/api/phones/self/workspaces/switch")
        let sent = try JSONSerialization.jsonObject(with: try XCTUnwrap(WorkspacesStub.capturedBody)) as? [String: String]
        XCTAssertEqual(sent, ["id": "b"])
        XCTAssertEqual(list[0].availability, .waiting)
    }

    func testARefusedSwitchSurfacesTheServersWords() async {
        WorkspacesStub.statusCode = 404
        WorkspacesStub.responseBody = Data(#"{"error":"No such workspace"}"#.utf8)
        do {
            _ = try await client.switchWorkspace("gone")
            XCTFail("a refused switch must throw")
        } catch let APIError.status(code, message) {
            XCTAssertEqual(code, 404)
            XCTAssertEqual(message, "No such workspace")
        } catch {
            XCTFail("unexpected error \(error)")
        }
    }

    func testOpenNowSendsTheHurryFlagAndAPlainOpenDoesNot() async throws {
        WorkspacesStub.responseBody = Data(#"{"workspaces":[{"id":"a"},{"id":"b","current":true}]}"#.utf8)

        _ = try await client.openWorkspace("b", now: true)
        let hurried = try JSONSerialization.jsonObject(with: try XCTUnwrap(WorkspacesStub.capturedBody)) as? [String: AnyHashable]
        XCTAssertEqual(hurried, ["id": "b", "now": true])

        _ = try await client.openWorkspace("a")
        let plain = try JSONSerialization.jsonObject(with: try XCTUnwrap(WorkspacesStub.capturedBody)) as? [String: AnyHashable]
        XCTAssertEqual(plain, ["id": "a"])
        XCTAssertEqual(WorkspacesStub.capturedRequest?.url?.path, "/api/phones/self/workspaces/switch")
    }

    func testASwitchTheOwnerTurnedOffGives403() async {
        WorkspacesStub.statusCode = 403
        WorkspacesStub.responseBody = Data(#"{"error":"This phone cannot open other workspaces."}"#.utf8)
        do {
            _ = try await client.openWorkspace("b")
            XCTFail("a switch the owner turned off must throw")
        } catch let APIError.status(code, _) {
            XCTAssertEqual(code, 403)
        } catch {
            XCTFail("unexpected error \(error)")
        }
    }

    // MARK: - Responses captured from the server

    private func fixture(_ name: String) throws -> Data {
        guard let url = Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "Fixtures")
            ?? Bundle.module.url(forResource: name, withExtension: "json")
        else { throw CocoaError(.fileNoSuchFile) }
        return try Data(contentsOf: url)
    }

    func testReadsTheListTheServerSends() async throws {
        WorkspacesStub.responseBody = try fixture("phone-workspaces")
        let loaded = try await client.workspaces()
        let list = try XCTUnwrap(loaded)
        XCTAssertEqual(list.map(\.id), ["local", "ws-a", "ws-b", "ws-c", "ws-d"])
        XCTAssertEqual(list.map(\.current), [true, false, false, false, false])
        XCTAssertEqual(list.map(\.availability), [.ready, .ready, .waiting, .unavailable, .ready])
        XCTAssertEqual(list[2].waitingFor, "Bot Sintetis")
        XCTAssertEqual(list[0].color, "#112233")
        XCTAssertNil(list[4].name)
    }

    func testAComputerThatAnswersNoRouteGivesNoList() async throws {
        WorkspacesStub.statusCode = 404
        WorkspacesStub.responseBody = try fixture("phone-workspaces-no-route")
        let loaded = try await client.workspaces()
        XCTAssertNil(loaded)
    }

    func testTheServersRefusalsReachTheCaller() async throws {
        WorkspacesStub.statusCode = 404
        WorkspacesStub.responseBody = try fixture("phone-workspaces-not-found")
        do {
            _ = try await client.switchWorkspace("nope")
            XCTFail("an unknown workspace must throw")
        } catch let APIError.status(code, message) {
            XCTAssertEqual(code, 404)
            XCTAssertEqual(message, "workspace_not_found")
        }

        WorkspacesStub.statusCode = 409
        WorkspacesStub.responseBody = try fixture("phone-workspaces-unavailable")
        do {
            _ = try await client.switchWorkspace("ws-c")
            XCTFail("an unavailable workspace must throw")
        } catch let APIError.status(code, message) {
            XCTAssertEqual(code, 409)
            XCTAssertEqual(message, "workspace_unavailable")
        }
    }
}
