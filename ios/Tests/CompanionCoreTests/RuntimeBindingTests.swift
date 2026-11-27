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
}
