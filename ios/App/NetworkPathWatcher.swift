import Foundation
import Network

/// Calls `onChange` one second after the phone's network path changes (Wi-Fi,
/// cellular, VPN). The first report, which describes the current path, is
/// ignored.
@MainActor
final class NetworkPathWatcher {
    private let monitor = NWPathMonitor()
    private var lastSignature: String?
    private var pending: Task<Void, Never>?
    private var started = false

    func start(onChange: @escaping @MainActor () -> Void) {
        guard !started else { return }
        started = true
        monitor.pathUpdateHandler = { [weak self] path in
            let signature = "\(path.status)|" + path.availableInterfaces.map { "\($0.type)\($0.name)" }.sorted().joined(separator: ",")
            Task { @MainActor [weak self] in
                guard let self else { return }
                defer { self.lastSignature = signature }
                guard let last = self.lastSignature, last != signature, path.status == .satisfied else { return }
                self.pending?.cancel()
                self.pending = Task { [weak self] in
                    try? await Task.sleep(for: .seconds(1))
                    guard !Task.isCancelled, self != nil else { return }
                    onChange()
                }
            }
        }
        monitor.start(queue: DispatchQueue(label: "seira.network-path"))
    }
}
