import Foundation

/// `work()`, timed under the busy-fleet fixture in a debug build.
@MainActor
@inline(__always)
func busyFleetMeasured<T>(_ name: String, _ work: () -> T) -> T {
#if DEBUG
    return BusyFleetProbe.measure(name, work)
#else
    return work()
#endif
}

/// Counts one body pass under the busy-fleet fixture in a debug build:
/// `let _ = busyFleetPass("name")`.
@MainActor
@inline(__always)
func busyFleetPass(_ name: String) {
#if DEBUG
    BusyFleetProbe.pass(name)
#endif
}

#if DEBUG
/// Main-thread time under the `-busy-fleet-preview` fixture, for
/// ResponsivenessUITests. The fixture badge shows `summary`; the test reads it
/// before and after an action. Inert without the fixture.
@MainActor
enum BusyFleetProbe {
    static let enabled = ProcessInfo.processInfo.arguments.contains("-busy-fleet-preview")

    /// Seconds the main run loop was awake: everything the app did on its
    /// main thread, accessibility queries from XCUITest included.
    private static var awake: CFAbsoluteTime = 0
    private static var wokeAt: CFAbsoluteTime?
    private static var observers: [CFRunLoopObserver] = []
    private static var timed: [String: (count: Int, seconds: CFAbsoluteTime)] = [:]
    private static var passes: [String: Int] = [:]

    static func start() {
        guard enabled, observers.isEmpty else { return }
        let woke = CFRunLoopObserverCreateWithHandler(
            nil, CFRunLoopActivity.afterWaiting.rawValue, true, CFIndex.min
        ) { _, _ in
            MainActor.assumeIsolated { BusyFleetProbe.wokeAt = CFAbsoluteTimeGetCurrent() }
        }
        let sleeps = CFRunLoopObserverCreateWithHandler(
            nil, CFRunLoopActivity.beforeWaiting.rawValue, true, CFIndex.max
        ) { _, _ in
            MainActor.assumeIsolated {
                guard let since = BusyFleetProbe.wokeAt else { return }
                BusyFleetProbe.awake += CFAbsoluteTimeGetCurrent() - since
                BusyFleetProbe.wokeAt = nil
            }
        }
        for observer in [woke, sleeps].compactMap({ $0 }) {
            CFRunLoopAddObserver(CFRunLoopGetMain(), observer, .commonModes)
            observers.append(observer)
        }
    }

    static func measure<T>(_ name: String, _ work: () -> T) -> T {
        guard enabled else { return work() }
        let started = CFAbsoluteTimeGetCurrent()
        let result = work()
        let entry = timed[name] ?? (0, 0)
        timed[name] = (entry.count + 1, entry.seconds + CFAbsoluteTimeGetCurrent() - started)
        return result
    }

    static func pass(_ name: String) {
        guard enabled else { return }
        passes[name, default: 0] += 1
    }

    /// `awake=<s>;t.<name>=<count>/<s>;n.<name>=<count>`, sorted by name.
    static var summary: String {
        var parts = [String(format: "awake=%.3f", awake)]
        parts += timed.keys.sorted().map { name in
            let entry = timed[name] ?? (0, 0)
            return "t.\(name)=\(entry.count)/" + String(format: "%.3f", entry.seconds)
        }
        parts += passes.keys.sorted().map { "n.\($0)=\(passes[$0] ?? 0)" }
        return parts.joined(separator: ";")
    }
}
#endif
