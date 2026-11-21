import Foundation

/// The non-secret index of computers this iPhone knows about.
///
/// Device tokens remain in Keychain, one per connection id. This value is
/// safe to keep in UserDefaults and makes changing computers an ordinary
/// selection rather than a destructive unpair-and-repair cycle.
public struct CompanionConnectionRegistry: Codable, Equatable, Sendable {
    public private(set) var connections: [Connection]
    public private(set) var activeConnectionID: String?

    public init(connections: [Connection] = [], activeConnectionID: String? = nil) {
        var seen = Set<String>()
        self.connections = connections.filter { seen.insert($0.id).inserted }
        if let activeConnectionID,
           self.connections.contains(where: { $0.id == activeConnectionID }) {
            self.activeConnectionID = activeConnectionID
        } else {
            self.activeConnectionID = self.connections.first?.id
        }
    }

    public var activeConnection: Connection? {
        guard let activeConnectionID else { return nil }
        return connections.first { $0.id == activeConnectionID }
    }

    public func connection(id: String) -> Connection? {
        connections.first { $0.id == id }
    }

    /// The saved pairing a new pairing replaces: one that shares a real route
    /// and carries no identity that differs. Two servers behind one address, or
    /// two computers paired only through the relay, stay two computers. A
    /// claimed identity alone never matches: a QR or a server can claim any.
    public func matchingConnection(for candidate: Connection) -> Connection? {
        let candidateRoutes = candidate.matchableRoutes
        guard !candidateRoutes.isEmpty else { return nil }
        return connections.first { saved in
            !candidateRoutes.isDisjoint(with: saved.matchableRoutes) && !saved.identityDiffers(from: candidate)
        }
    }

    /// Insert or refresh one computer and optionally make it the live one.
    public mutating func upsert(_ connection: Connection, makeActive: Bool = true) {
        if let index = connections.firstIndex(where: { $0.id == connection.id }) {
            connections[index] = connection
        } else {
            connections.append(connection)
        }
        if makeActive || activeConnectionID == nil {
            activeConnectionID = connection.id
        }
    }

    @discardableResult
    public mutating func select(id: String) -> Bool {
        guard connections.contains(where: { $0.id == id }) else { return false }
        activeConnectionID = id
        return true
    }

    /// Remove one computer. When it was active, the oldest remaining saved
    /// computer becomes active so the app never lands in a false unpaired
    /// state while another valid pairing still exists.
    @discardableResult
    public mutating func remove(id: String) -> Connection? {
        guard let index = connections.firstIndex(where: { $0.id == id }) else { return nil }
        let removed = connections.remove(at: index)
        if activeConnectionID == id {
            activeConnectionID = connections.first?.id
        }
        return removed
    }

    private enum CodingKeys: String, CodingKey {
        case connections, activeConnectionID
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.init(
            connections: try container.decode([Connection].self, forKey: .connections),
            activeConnectionID: try container.decodeIfPresent(String.self, forKey: .activeConnectionID)
        )
    }
}

public struct CompanionConnectionRegistryRestore: Equatable, Sendable {
    public let registry: CompanionConnectionRegistry
    public let migratedLegacyConnection: Bool

    public init(registry: CompanionConnectionRegistry, migratedLegacyConnection: Bool) {
        self.registry = registry
        self.migratedLegacyConnection = migratedLegacyConnection
    }
}

/// Decode the new registry or lift the previous single saved connection into
/// it. Kept pure so upgrades can be tested without touching UserDefaults.
public enum CompanionConnectionRegistryMigration {
    public static func restore(
        registryData: Data?,
        legacyConnectionData: Data?
    ) -> CompanionConnectionRegistryRestore {
        let decoder = JSONDecoder()
        if let registryData,
           let registry = try? decoder.decode(CompanionConnectionRegistry.self, from: registryData) {
            return CompanionConnectionRegistryRestore(
                registry: registry,
                migratedLegacyConnection: false
            )
        }
        if let legacyConnectionData,
           let connection = try? decoder.decode(Connection.self, from: legacyConnectionData) {
            return CompanionConnectionRegistryRestore(
                registry: CompanionConnectionRegistry(
                    connections: [connection],
                    activeConnectionID: connection.id
                ),
                migratedLegacyConnection: true
            )
        }
        return CompanionConnectionRegistryRestore(
            registry: CompanionConnectionRegistry(),
            migratedLegacyConnection: false
        )
    }
}

private extension Connection {
    /// RFC 6761 `.invalid` names never resolve: a relay-only QR names one, so it is no address.
    var matchableRoutes: Set<String> {
        Set(orderedEndpoints.filter { !$0.host.lowercased().hasSuffix(".invalid") }.map(\.url))
    }

    func identityDiffers(from other: Connection) -> Bool {
        Self.differs(serverEnvironmentId, other.serverEnvironmentId)
            || Self.differs(secretPublicKey, other.secretPublicKey)
    }

    static func differs(_ left: String?, _ right: String?) -> Bool {
        guard let left, let right else { return false }
        return left != right
    }
}
