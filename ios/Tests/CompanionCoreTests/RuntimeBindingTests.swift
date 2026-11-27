import XCTest
@testable import CompanionCore

final class RuntimeBindingTests: XCTestCase {
    func testStaysCurrentWhileTheSameRuntimeIsPaired() {
        let binding = RuntimeBinding(generation: 3, connectionID: "air")
        XCTAssertTrue(binding.isCurrent(generation: 3, connectionID: "air"))
    }

    func testRetiresWhenTheAppSwitchesToAnotherComputer() {
        let binding = RuntimeBinding(generation: 3, connectionID: "air")
        XCTAssertFalse(binding.isCurrent(generation: 3, connectionID: "pro"))
    }

    func testRetiresWhenTheSameComputerStartsANewRuntime() {
        // Workspace switch or re-pair: same pairing id, new generation.
        let binding = RuntimeBinding(generation: 3, connectionID: "air")
        XCTAssertFalse(binding.isCurrent(generation: 4, connectionID: "air"))
    }

    func testRetiresWhenThePairingIsRemoved() {
        let binding = RuntimeBinding(generation: 3, connectionID: "air")
        XCTAssertFalse(binding.isCurrent(generation: 3, connectionID: nil))
    }

    func testARequestMadeWithoutAPairingIsNeverCurrent() {
        let binding = RuntimeBinding(generation: 0, connectionID: nil)
        XCTAssertFalse(binding.isCurrent(generation: 0, connectionID: nil))
    }

    func testARetiredRuntimeDoesNotComeBackWhenTheOldPairingIsRestored() {
        let binding = RuntimeBinding(generation: 3, connectionID: "air")
        // air -> pro -> air again: the pairing id matches, the generation does not.
        XCTAssertFalse(binding.isCurrent(generation: 5, connectionID: "air"))
    }

    // MARK: - A view bound to the runtime it was shown for

    func testAViewBindingStaysCurrentWhileNothingChanges() {
        let shown = RuntimeBinding(generation: 2, connectionID: "air")
        XCTAssertTrue(shown.isCurrent(against: RuntimeBinding(generation: 2, connectionID: "air")))
    }

    func testAViewBindingRetiresWhenTheSamePairingIsReplaced() {
        // Re-pair or restore: same pairing id, new runtime.
        let shown = RuntimeBinding(generation: 2, connectionID: "air")
        XCTAssertFalse(shown.isCurrent(against: RuntimeBinding(generation: 3, connectionID: "air")))
    }

    func testAViewBindingRetiresOnAWorkspaceSwitch() {
        // A workspace switch keeps the pairing and moves the generation on.
        let shown = RuntimeBinding(generation: 2, connectionID: "air")
        let afterSwitch = RuntimeBinding(generation: shown.generation + 1, connectionID: "air")
        XCTAssertFalse(shown.isCurrent(against: afterSwitch))
    }

    func testAViewBindingRetiresWhenTheComputerChanges() {
        let shown = RuntimeBinding(generation: 2, connectionID: "air")
        XCTAssertFalse(shown.isCurrent(against: RuntimeBinding(generation: 2, connectionID: "pro")))
    }

    func testAViewBindingTakenWithNoPairingNeverActs() {
        let shown = RuntimeBinding(generation: 2, connectionID: nil)
        XCTAssertFalse(shown.isCurrent(against: RuntimeBinding(generation: 2, connectionID: nil)))
    }

    func testAnOldGenerationDoesNotComeBackForTheSamePairing() {
        // air(2) -> air(3) -> air(2) cannot happen: the generation only moves on.
        // A binding for 2 stays retired against every later generation of the same pairing.
        let shown = RuntimeBinding(generation: 2, connectionID: "air")
        for generation in 3...6 {
            XCTAssertFalse(shown.isCurrent(against: RuntimeBinding(generation: generation, connectionID: "air")))
        }
    }
}

/// A batch with one request for each item belongs to the runtime that started it.
@MainActor
final class RuntimeBatchTests: XCTestCase {
    private struct Refused: Error {}

    func testEveryRequestIsSentWhileTheRuntimeStaysCurrent() async {
        var sent: [String] = []
        let outcome = await RuntimeBatch.run(["a", "b", "c"], isCurrent: { true }) { sent.append($0) }
        XCTAssertEqual(outcome, .finished(failures: 0))
        XCTAssertEqual(sent, ["a", "b", "c"])
    }

    func testNoFurtherRequestIsSentOnceTheRuntimeRetires() async {
        var current = true
        var sent: [String] = []
        let outcome = await RuntimeBatch.run(["a", "b", "c"], isCurrent: { current }) { item in
            sent.append(item)
            current = false  // the computer changes while the first request is out
        }
        XCTAssertEqual(outcome, .retired)
        XCTAssertEqual(sent, ["a"], "the second and third request must not reach the next computer's bot")
    }

    func testARuntimeThatRetiresDuringTheLastRequestStillReportsRetired() async {
        var current = true
        let outcome = await RuntimeBatch.run(["a"], isCurrent: { current }) { _ in current = false }
        XCTAssertEqual(outcome, .retired)
    }

    func testAFailedRequestIsCountedAndTheRestStillRun() async {
        var sent: [String] = []
        let outcome = await RuntimeBatch.run(["a", "b", "c"], isCurrent: { true }) { item in
            sent.append(item)
            if item == "a" { throw Refused() }
        }
        XCTAssertEqual(outcome, .finished(failures: 1))
        XCTAssertEqual(sent, ["a", "b", "c"])
    }

    func testAnEmptyBatchSendsNothing() async {
        var sent = 0
        let outcome = await RuntimeBatch.run([String](), isCurrent: { true }) { _ in sent += 1 }
        XCTAssertEqual(outcome, .finished(failures: 0))
        XCTAssertEqual(sent, 0)
    }
}
