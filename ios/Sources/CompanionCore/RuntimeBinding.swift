import Foundation

/// The paired runtime a request was sent to.
///
/// A request that outlives a computer switch, an unpair or a workspace switch
/// answers for a runtime that is gone. Its result must not reach the state,
/// and its error must not reach the screen, of the runtime that replaced it.
/// `generation` moves on whenever the app drops one runtime's state;
/// `connectionID` is the pairing the request was sent through.
public struct RuntimeBinding: Equatable, Sendable {
    public let generation: Int
    public let connectionID: String?

    public init(generation: Int, connectionID: String?) {
        self.generation = generation
        self.connectionID = connectionID
    }

    /// True while the request's runtime is still the active one. A request
    /// made with no pairing has no runtime to return to, so it is never current.
    public func isCurrent(generation currentGeneration: Int, connectionID currentConnectionID: String?) -> Bool {
        connectionID != nil && generation == currentGeneration && connectionID == currentConnectionID
    }

    /// True while this binding names the same runtime as `current`: the same
    /// pairing and the same generation. A view that was shown for one runtime
    /// uses this to refuse to act once the app serves another, even when the
    /// pairing id is the same (re-pair, restore, workspace switch).
    public func isCurrent(against current: RuntimeBinding) -> Bool {
        isCurrent(generation: current.generation, connectionID: current.connectionID)
    }
}

/// A batch of requests, one for each item, that belongs to one runtime.
///
/// The batch checks the runtime before every request and once more at the end.
/// When the runtime retires, the batch sends no further request and reports
/// `.retired`, so no request reaches the bot of the computer that replaced it.
public enum RuntimeBatch {
    public enum Outcome: Equatable, Sendable {
        case retired
        case finished(failures: Int)
    }

    @MainActor
    public static func run<Item>(
        _ items: [Item],
        isCurrent: () -> Bool,
        request: (Item) async throws -> Void
    ) async -> Outcome {
        var failures = 0
        for item in items {
            guard isCurrent() else { return .retired }
            do {
                try await request(item)
            } catch {
                failures += 1
            }
        }
        return isCurrent() ? .finished(failures: failures) : .retired
    }
}
