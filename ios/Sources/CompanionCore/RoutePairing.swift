import Foundation
#if canImport(CFNetwork)
import CFNetwork
#endif

/// Why one route of a computer did not answer.
public enum RouteFailureReason: Equatable, Sendable {
    case unresolved
    case refused
    case timedOut
    case secureConnection
    case offline
    case gateway(Int)
    case notSeira
    case other

    init(_ error: Error) {
        if let urlError = error as? URLError {
            switch urlError.code {
            case .cannotFindHost, .dnsLookupFailed: self = .unresolved
            case .cannotConnectToHost: self = .refused
            case .timedOut: self = .timedOut
            case .notConnectedToInternet: self = .offline
            case .secureConnectionFailed, .serverCertificateHasBadDate, .serverCertificateUntrusted,
                 .serverCertificateHasUnknownRoot, .serverCertificateNotYetValid:
                self = .secureConnection
            default: self = .other
            }
        } else if let apiError = error as? APIError, case let .status(code, _) = apiError {
            if code == 404 { self = .notSeira }
            else if ConnectionAdvice.shouldTryAnotherRoute(after: apiError) { self = .gateway(code) }
            else { self = .other }
        } else {
            self = .other
        }
    }
}

/// Whether a VPN, Tailscale included, is up on this phone.
public protocol VPNDetecting: Sendable {
    func isVPNActive() -> Bool
}

public struct SystemVPNDetector: VPNDetecting {
    public init() {}

    /// The scoped interfaces the system lists beside the proxy settings. A
    /// VPN adds a tunnel interface there; no public API names Tailscale itself.
    public func isVPNActive() -> Bool {
        #if canImport(CFNetwork)
        guard let settings = CFNetworkCopySystemProxySettings()?.takeRetainedValue() as? [String: Any],
              let scoped = settings["__SCOPED__"] as? [String: Any]
        else { return false }
        return Self.hasTunnelInterface(scoped.keys)
        #else
        return false
        #endif
    }

    static func hasTunnelInterface<Names: Sequence>(_ names: Names) -> Bool where Names.Element == String {
        names.contains { name in
            ["utun", "ipsec", "ppp", "tap", "tun"].contains { name.hasPrefix($0) }
        }
    }
}

/// What to tell the person when no route of a computer answered.
public struct RouteAdvice: Equatable, Sendable {
    public let message: String
    /// Set when the fix is to install or open Tailscale.
    public let tailscaleLink: URL?

    public static let tailscaleStore = URL(string: "https://apps.apple.com/app/tailscale/id1470499037")!
}

extension PairingRouteError {
    /// The advice for a failure where every route failed. A failed Tailscale
    /// name gets Tailscale advice; anything else keeps `errorDescription`.
    public func advice(vpn: VPNDetecting = SystemVPNDetector()) -> RouteAdvice {
        let hosts = attemptedHosts.map { (URLComponents(string: $0)?.host ?? "").lowercased() }
        let tailnetHosts = hosts.filter { $0.hasSuffix(".ts.net") || $0.hasSuffix(".ts.net.") }
        guard let tailnetHost = tailnetHosts.first else {
            return RouteAdvice(message: errorDescription ?? "", tailscaleLink: nil)
        }
        let otherRouteFailed = hosts.count > tailnetHosts.count
        var message: String
        var link: URL?
        if vpn.isVPNActive() {
            message = "\u{201C}\(tailnetHost)\u{201D} didn't resolve. If that's a Tailscale name, " +
                "this phone may not be on the tailnet. This phone may be signed in to a different tailnet."
        } else {
            message = "Tailscale is off on this phone. Turn it on, then try again."
            link = RouteAdvice.tailscaleStore
        }
        if otherRouteFailed {
            message += " The internet address did not answer either. Check that the computer is on."
        }
        return RouteAdvice(message: message, tailscaleLink: link)
    }
}

/// The route a phone should use, from every route it knows.
public struct RouteSelection: Sendable {
    public let winner: CompanionEndpoint?
    public let failures: [String: RouteFailureReason]
}

public struct ServerPairingOutcome: Sendable {
    public let response: ServerPairResponse
    public let connection: Connection
}

extension CompanionClient {
    /// Probe every route together. The answer is the first route in the order
    /// given that answered, once every route before it has failed.
    static func firstAnswering(
        count: Int,
        check: @escaping @Sendable (Int) async -> RouteFailureReason?
    ) async -> (winner: Int?, failures: [Int: RouteFailureReason]) {
        await withTaskGroup(of: (Int, RouteFailureReason?).self) { group in
            for offset in 0..<count {
                group.addTask { (offset, await check(offset)) }
            }
            var results = [RouteFailureReason??](repeating: .none, count: count)
            var failures: [Int: RouteFailureReason] = [:]
            for await (offset, failure) in group {
                results[offset] = .some(failure)
                if let failure { failures[offset] = failure }
                for index in 0..<count {
                    guard let resolved = results[index] else { break }
                    if resolved == nil {
                        group.cancelAll()
                        return (index, failures)
                    }
                }
            }
            return (nil, failures)
        }
    }

    /// Probe the stored routes of a paired computer. Any answer that is not a
    /// gateway failure shows the route works, as `probeRoute` does.
    public static func firstAnsweringRoute(
        among routes: [CompanionEndpoint],
        of connection: Connection,
        timeout: TimeInterval = 4,
        session: URLSession = .shared
    ) async -> RouteSelection {
        let candidates = routes.map(connection.dialing)
        let probe = await firstAnswering(count: candidates.count) { offset in
            do {
                let client = CompanionClient(connection: candidates[offset], token: nil, session: session)
                try await client.probeRoute(timeout: timeout, session: session)
                return nil
            } catch {
                return RouteFailureReason(error)
            }
        }
        var failures: [String: RouteFailureReason] = [:]
        for (offset, reason) in probe.failures { failures[routes[offset].url] = reason }
        return RouteSelection(winner: probe.winner.map { routes[$0] }, failures: failures)
    }

    private static func serverRouteFailure(
        _ connection: Connection,
        timeout: TimeInterval,
        session: URLSession
    ) async -> RouteFailureReason? {
        do {
            let client = CompanionClient(connection: connection, token: nil, session: session)
            var request = try client.makeRequest("GET", "/.well-known/seira/environment")
            request.timeoutInterval = timeout
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else { return .other }
            if http.statusCode == 404 { return .notSeira }
            if ConnectionAdvice.shouldTryAnotherRoute(after: APIError.status(code: http.statusCode, message: nil)) {
                return .gateway(http.statusCode)
            }
            if (200...299).contains(http.statusCode),
               (try? JSONDecoder().decode(ServerEnvironment.self, from: data)) == nil {
                return .notSeira
            }
            return nil
        } catch {
            return RouteFailureReason(error)
        }
    }

    /// Pair with a server over the first route that answers.
    ///
    /// Routes are probed together on the server's public descriptor. The code
    /// goes only to the first answering route in priority order. A transport
    /// or gateway failure moves on to the next answering route; the server
    /// replays the same attempt id, so a lost response costs no second code.
    /// The whole walk stops at `limit` seconds.
    public static func pairWithServerFirstReachable(
        connection: Connection,
        code: String,
        label: String,
        attemptId: String = UUID().uuidString,
        limit: TimeInterval = 15,
        session: URLSession = .shared
    ) async throws -> ServerPairingOutcome {
        let deadline = Date().addingTimeInterval(limit)
        let routes = connection.pairingEndpoints
        var remaining = routes.map { (endpoint: $0, connection: connection.dialing($0)) }
        var failures: [String: RouteFailureReason] = [:]

        while !remaining.isEmpty, deadline.timeIntervalSinceNow > 0.5 {
            let pending = remaining
            let probeTimeout = min(4, deadline.timeIntervalSinceNow)
            let probe = await firstAnswering(count: pending.count) { offset in
                await serverRouteFailure(pending[offset].connection, timeout: probeTimeout, session: session)
            }
            for (offset, reason) in probe.failures { failures[pending[offset].endpoint.url] = reason }
            guard let offset = probe.winner else { break }
            let winner = remaining.remove(at: offset)
            do {
                let response = try await pairWithServer(
                    connection: winner.connection,
                    code: code,
                    label: label,
                    attemptId: attemptId,
                    timeout: min(8, max(1, deadline.timeIntervalSinceNow)),
                    session: session
                )
                return ServerPairingOutcome(response: response, connection: winner.connection)
            } catch let error as APIError {
                if case .transport = error {
                    failures[winner.endpoint.url] = .other
                    continue
                }
                if ConnectionAdvice.shouldTryAnotherRoute(after: error) {
                    failures[winner.endpoint.url] = RouteFailureReason(error)
                    continue
                }
                throw error
            } catch {
                failures[winner.endpoint.url] = RouteFailureReason(error)
            }
        }
        if routes.count == 1, failures[routes[0].url] == .notSeira {
            throw APIError.transport("\(connection.displayAddress) isn't a Seira server. Check the address and try again.")
        }
        throw PairingRouteError(
            attemptedHosts: routes.map(\.url),
            computerName: connection.name,
            failures: failures
        )
    }
}
