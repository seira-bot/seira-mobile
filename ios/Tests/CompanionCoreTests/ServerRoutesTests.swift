import Foundation
import XCTest
@testable import CompanionCore

private final class RouteStub: URLProtocol {
    enum Answer {
        case reply(Int, Data)
        case fail(URLError.Code)
    }

    static let lock = NSLock()
    nonisolated(unsafe) static var handler: (URLRequest) -> Answer = { _ in .reply(500, Data()) }
    nonisolated(unsafe) static var requests: [URLRequest] = []

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.lock.lock()
        Self.requests.append(request)
        let answer = Self.handler(request)
        Self.lock.unlock()
        switch answer {
        case let .fail(code):
            client?.urlProtocol(self, didFailWithError: URLError(code))
        case let .reply(status, body):
            let response = HTTPURLResponse(
                url: request.url!, statusCode: status, httpVersion: "HTTP/1.1",
                headerFields: ["Content-Type": "application/json"]
            )!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: body)
            client?.urlProtocolDidFinishLoading(self)
        }
    }

    override func stopLoading() {}

    static func reset(_ handler: @escaping (URLRequest) -> Answer) {
        lock.lock()
        requests = []
        self.handler = handler
        lock.unlock()
    }

    static func hosts(path: String, method: String? = nil) -> [String] {
        lock.lock()
        defer { lock.unlock() }
        return requests
            .filter { $0.url?.path == path && (method == nil || $0.httpMethod == method) }
            .compactMap { $0.url?.host }
    }
}

private struct FakeVPN: VPNDetecting {
    let active: Bool
    func isVPNActive() -> Bool { active }
}

final class ServerRoutesTests: XCTestCase {
    private var session: URLSession!
    private let code = "ABCDEFGHJKLM"
    private let tailnet = "server-x.tail1234.ts.net"
    private let tunnel = "c-7f3a9c.seira.run"
    private let environment = Data(#"{"environmentId":"env_7f3a9c","label":"cab mini"}"#.utf8)

    override func setUp() {
        super.setUp()
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [RouteStub.self]
        session = URLSession(configuration: configuration)
    }

    override func tearDown() {
        session.invalidateAndCancel()
        super.tearDown()
    }

    private func pairBody() throws -> Data {
        let url = try XCTUnwrap(
            Bundle.module.url(forResource: "auth-pair-response", withExtension: "json", subdirectory: "Fixtures")
                ?? Bundle.module.url(forResource: "auth-pair-response", withExtension: "json")
        )
        return try Data(contentsOf: url)
    }

    private func encoded(_ json: String) -> String {
        Data(json.utf8).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    private func link(primary: String, endpoints: String? = nil) throws -> URL {
        let tail = endpoints.map { "&endpoints=\($0)" } ?? ""
        return try XCTUnwrap(URL(string: "https://\(primary)/pair#code=ABCD-EFGH-JKLM\(tail)"))
    }

    private func route(_ host: String, priority: Int) -> String {
        #"{"url":"https://\#(host)","kind":"hosted","priority":\#(priority)}"#
    }

    private func routesOf(_ url: URL) throws -> [String] {
        let invite = try XCTUnwrap(PairingInvite.parse(url))
        return invite.connection.orderedEndpoints.map(\.url)
    }

    private func invite(routes: [(String, Int)], primary: String) throws -> Connection {
        let list = "[" + routes.map { route($0.0, priority: $0.1) }.joined(separator: ",") + "]"
        let parsed = try XCTUnwrap(PairingInvite.parse(link(primary: primary, endpoints: encoded(list))))
        var connection = parsed.connection
        connection.establishRoutePolicyFromInvite()
        return connection
    }

    // MARK: - Link

    func testALinkWithEndpointsCarriesEveryRouteInPriorityOrder() throws {
        let list = "[" + [route(tunnel, priority: 2), route(tailnet, priority: 1), route("home.example.com", priority: 3)].joined(separator: ",") + "]"
        let routes = try routesOf(link(primary: tailnet, endpoints: encoded(list)))
        XCTAssertEqual(routes, ["https://\(tailnet)", "https://\(tunnel)", "https://home.example.com"])
    }

    func testAPrimaryHostMissingFromTheListLeadsAtPriorityZero() throws {
        let list = "[" + [route(tunnel, priority: 4), route("home.example.com", priority: 1)].joined(separator: ",") + "]"
        let routes = try routesOf(link(primary: tailnet, endpoints: encoded(list)))
        XCTAssertEqual(routes, ["https://\(tailnet)", "https://home.example.com", "https://\(tunnel)"])
    }

    func testADuplicateOriginIsListedOnce() throws {
        let list = "[" + [route(tunnel, priority: 1), route(tunnel.uppercased(), priority: 2), route(tailnet, priority: 0)].joined(separator: ",") + "]"
        let routes = try routesOf(link(primary: tailnet, endpoints: encoded(list)))
        XCTAssertEqual(routes, ["https://\(tailnet)", "https://\(tunnel)"])
    }

    func testBadEntriesAreDroppedAndTheLinkStaysUsable() throws {
        let list = "[" + [
            route(tunnel, priority: 1),
            #"{"url":"http://plain.example.com","kind":"lan","priority":2}"#,
            #"{"url":"https://odd.example.com","kind":"warp","priority":3}"#,
            #"{"url":"https://path.example.com/x","kind":"hosted","priority":4}"#,
            "42",
            #"{"kind":"hosted"}"#,
        ].joined(separator: ",") + "]"
        let invite = try XCTUnwrap(PairingInvite.parse(link(primary: tailnet, endpoints: encoded(list))))
        XCTAssertEqual(invite.credential, code)
        XCTAssertEqual(invite.connection.orderedEndpoints.map(\.url), ["https://\(tailnet)", "https://\(tunnel)"])
    }

    func testAnEndpointsValueThatDoesNotDecodeIsIgnored() throws {
        for bad in ["!!!", encoded("not json"), encoded(#"{"url":"https://x.example.com"}"#), String(repeating: "A", count: 9_000)] {
            let invite = try XCTUnwrap(PairingInvite.parse(link(primary: tailnet, endpoints: bad)))
            XCTAssertEqual(invite.credential, code)
            XCTAssertEqual(invite.connection.orderedEndpoints.map(\.url), ["https://\(tailnet)"], bad)
        }
    }

    func testAtMostEightRoutesAreKept() throws {
        let list = "[" + (1...12).map { route("r\($0).example.com", priority: $0) }.joined(separator: ",") + "]"
        let routes = try routesOf(link(primary: tailnet, endpoints: encoded(list)))
        XCTAssertEqual(routes.count, 8)
        XCTAssertEqual(routes.first, "https://\(tailnet)")
    }

    // MARK: - Pairing walk

    func testAnUnresolvedTailnetNameFallsBackToTheTunnel() async throws {
        let body = try pairBody()
        let env = environment
        RouteStub.reset { [tailnet] request in
            if request.url?.host == tailnet { return .fail(.cannotFindHost) }
            return request.url?.path == "/api/auth/pair" ? .reply(200, body) : .reply(200, env)
        }
        let connection = try invite(routes: [(tailnet, 0), (tunnel, 1)], primary: tailnet)
        let outcome = try await CompanionClient.pairWithServerFirstReachable(
            connection: connection, code: code, label: "phone", session: session
        )
        XCTAssertEqual(outcome.connection.activeEndpoint?.url, "https://\(tunnel)")
        XCTAssertEqual(RouteStub.hosts(path: "/api/auth/pair", method: "POST"), [tunnel])
        XCTAssertEqual(outcome.response.environment.environmentId, "env_7f3a9c")
    }

    func testADesktopThatKnowsOnlyTheOldDescriptorPathStillPairs() async throws {
        let body = try pairBody()
        let env = environment
        RouteStub.reset { request in
            switch request.url?.path {
            case "/.well-known/openmausbot/environment": return .reply(200, env)
            case "/api/auth/pair": return .reply(200, body)
            default: return .reply(404, Data())
            }
        }
        let connection = try invite(routes: [(tailnet, 0), (tunnel, 1)], primary: tailnet)
        let outcome = try await CompanionClient.pairWithServerFirstReachable(
            connection: connection, code: code, label: "phone", session: session
        )
        XCTAssertEqual(outcome.connection.activeEndpoint?.url, "https://\(tailnet)")
    }

    func testTheRouteWithTheLowestPriorityWinsWhenBothAnswer() async throws {
        let body = try pairBody()
        let env = environment
        RouteStub.reset { request in
            request.url?.path == "/api/auth/pair" ? .reply(200, body) : .reply(200, env)
        }
        let connection = try invite(routes: [(tunnel, 1), (tailnet, 0)], primary: tunnel)
        let outcome = try await CompanionClient.pairWithServerFirstReachable(
            connection: connection, code: code, label: "phone", session: session
        )
        XCTAssertEqual(outcome.connection.activeEndpoint?.url, "https://\(tailnet)")
        XCTAssertEqual(RouteStub.hosts(path: "/api/auth/pair", method: "POST"), [tailnet])
    }

    func testAGatewayFailureOnPairingMovesToTheNextRoute() async throws {
        let body = try pairBody()
        let env = environment
        RouteStub.reset { [tailnet] request in
            guard request.url?.path == "/api/auth/pair" else { return .reply(200, env) }
            return request.url?.host == tailnet ? .reply(530, Data()) : .reply(200, body)
        }
        let connection = try invite(routes: [(tailnet, 0), (tunnel, 1)], primary: tailnet)
        let outcome = try await CompanionClient.pairWithServerFirstReachable(
            connection: connection, code: code, label: "phone", session: session
        )
        XCTAssertEqual(outcome.connection.activeEndpoint?.url, "https://\(tunnel)")
        XCTAssertEqual(RouteStub.hosts(path: "/api/auth/pair", method: "POST"), [tailnet, tunnel])
    }

    func testARefusedCodeIsNotSentToAnotherRoute() async throws {
        let env = environment
        RouteStub.reset { request in
            request.url?.path == "/api/auth/pair"
                ? .reply(401, Data(#"{"error":"that code is not valid or has expired"}"#.utf8))
                : .reply(200, env)
        }
        let connection = try invite(routes: [(tailnet, 0), (tunnel, 1)], primary: tailnet)
        do {
            _ = try await CompanionClient.pairWithServerFirstReachable(
                connection: connection, code: code, label: "phone", session: session
            )
            XCTFail("expected a refusal")
        } catch let error as APIError {
            guard case let .status(status, _) = error else { return XCTFail("\(error)") }
            XCTAssertEqual(status, 401)
        }
        XCTAssertEqual(RouteStub.hosts(path: "/api/auth/pair", method: "POST").count, 1)
    }

    func testEveryRouteFailingKeepsTheReasonForEach() async throws {
        RouteStub.reset { [tailnet] request in
            request.url?.host == tailnet ? .fail(.cannotFindHost) : .fail(.timedOut)
        }
        let connection = try invite(routes: [(tailnet, 0), (tunnel, 1)], primary: tailnet)
        do {
            _ = try await CompanionClient.pairWithServerFirstReachable(
                connection: connection, code: code, label: "phone", session: session
            )
            XCTFail("expected a route error")
        } catch let error as PairingRouteError {
            XCTAssertEqual(error.failures["https://\(tailnet)"], .unresolved)
            XCTAssertEqual(error.failures["https://\(tunnel)"], .timedOut)
            XCTAssertEqual(error.attemptedHosts, ["https://\(tailnet)", "https://\(tunnel)"])
        }
        XCTAssertTrue(RouteStub.hosts(path: "/api/auth/pair").isEmpty, "no code goes to a route that never answered")
    }

    func testTheWalkStopsAtItsTimeLimit() async throws {
        RouteStub.reset { _ in .fail(.timedOut) }
        let connection = try invite(routes: [(tailnet, 0), (tunnel, 1)], primary: tailnet)
        let started = Date()
        do {
            _ = try await CompanionClient.pairWithServerFirstReachable(
                connection: connection, code: code, label: "phone", limit: 0, session: session
            )
            XCTFail("expected a route error")
        } catch is PairingRouteError {
            XCTAssertLessThan(Date().timeIntervalSince(started), 3)
        }
    }

    func testFirstAnsweringRouteKeepsPriorityOrderOverSpeed() async throws {
        RouteStub.reset { _ in .reply(200, Data(#"{"app":"seira"}"#.utf8)) }
        let connection = try invite(routes: [(tailnet, 0), (tunnel, 1)], primary: tailnet)
        let selection = await CompanionClient.firstAnsweringRoute(
            among: connection.orderedEndpoints, of: connection, session: session
        )
        XCTAssertEqual(selection.winner?.url, "https://\(tailnet)")
    }

    func testFirstAnsweringRouteSkipsAnUnresolvedName() async throws {
        RouteStub.reset { [tailnet] request in
            request.url?.host == tailnet ? .fail(.cannotFindHost) : .reply(200, Data())
        }
        let connection = try invite(routes: [(tailnet, 0), (tunnel, 1)], primary: tailnet)
        let selection = await CompanionClient.firstAnsweringRoute(
            among: connection.orderedEndpoints, of: connection, session: session
        )
        XCTAssertEqual(selection.winner?.url, "https://\(tunnel)")
        XCTAssertEqual(selection.failures["https://\(tailnet)"], .unresolved)
    }

    // MARK: - Route refresh

    func testAServerConnectionReadsItsRoutesWithTheBearer() async throws {
        let json = #"{"serverName":"cab mini","endpoints":[{"url":"https://\#(tunnel)","kind":"hosted","priority":1},{"url":"https://\#(tailnet)","kind":"hosted","priority":0}],"version":"1"}"#
        RouteStub.reset { _ in .reply(200, Data(json.utf8)) }
        var connection = try invite(routes: [(tailnet, 0)], primary: tailnet)
        connection.serverEnvironmentId = "env_7f3a9c"
        let client = CompanionClient(connection: connection, token: "seira_sess_x", session: session)
        let metadata = try await client.connectionMetadata()
        let request = try XCTUnwrap(RouteStub.requests.first)
        XCTAssertEqual(request.url?.path, "/api/companion/endpoints")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer seira_sess_x")

        connection.reconcile(metadata)
        XCTAssertEqual(connection.orderedEndpoints.map(\.url), ["https://\(tailnet)", "https://\(tunnel)"])
        XCTAssertEqual(connection.serverEnvironmentId, "env_7f3a9c")
        XCTAssertTrue(connection.pairedWithServer)
    }

    func testAnOlderServerAnswering404LeavesTheStoredRoutes() async throws {
        RouteStub.reset { _ in .reply(404, Data()) }
        let connection = try invite(routes: [(tailnet, 0), (tunnel, 1)], primary: tailnet)
        let client = CompanionClient(connection: connection, token: "seira_sess_x", session: session)
        do {
            _ = try await client.connectionMetadata()
            XCTFail("expected a 404")
        } catch let error as APIError {
            guard case let .status(status, _) = error else { return XCTFail("\(error)") }
            XCTAssertEqual(status, 404)
        }
        XCTAssertEqual(connection.orderedEndpoints.count, 2)
    }

    // MARK: - Advice

    private func failed(_ hosts: [String]) -> PairingRouteError {
        PairingRouteError(attemptedHosts: hosts.map { "https://\($0)" }, computerName: "cab mini")
    }

    func testTailscaleOffGetsAPlainMessageAndAStoreLink() {
        let advice = failed([tailnet]).advice(vpn: FakeVPN(active: false))
        XCTAssertEqual(advice.message, "Tailscale is off on this phone. Turn it on, then try again.")
        XCTAssertEqual(advice.tailscaleLink?.absoluteString, "https://apps.apple.com/app/tailscale/id1470499037")
    }

    func testAVPNThatIsOnKeepsTheResolveHintAndNamesAnotherTailnet() {
        let advice = failed([tailnet]).advice(vpn: FakeVPN(active: true))
        XCTAssertTrue(advice.message.contains("didn't resolve"))
        XCTAssertTrue(advice.message.contains("This phone may be signed in to a different tailnet."))
        XCTAssertNil(advice.tailscaleLink)
    }

    func testAFailedInternetRouteIsNamedToo() {
        let advice = failed([tailnet, tunnel]).advice(vpn: FakeVPN(active: false))
        XCTAssertTrue(advice.message.hasPrefix("Tailscale is off on this phone."))
        XCTAssertTrue(advice.message.hasSuffix("The internet address did not answer either. Check that the computer is on."))
    }

    func testWithoutATailnetNameTheOldAdviceStands() {
        let error = failed([tunnel])
        let advice = error.advice(vpn: FakeVPN(active: false))
        XCTAssertEqual(advice.message, error.errorDescription)
        XCTAssertNil(advice.tailscaleLink)
    }

    func testTunnelInterfacesAreRecognisedByPrefix() {
        XCTAssertTrue(SystemVPNDetector.hasTunnelInterface(["en0", "utun4"]))
        XCTAssertTrue(SystemVPNDetector.hasTunnelInterface(["ipsec0"]))
        XCTAssertTrue(SystemVPNDetector.hasTunnelInterface(["ppp0"]))
        XCTAssertTrue(SystemVPNDetector.hasTunnelInterface(["tap0"]))
        XCTAssertTrue(SystemVPNDetector.hasTunnelInterface(["tun1"]))
        XCTAssertFalse(SystemVPNDetector.hasTunnelInterface(["en0", "pdp_ip0"]))
    }
}
