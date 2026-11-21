import XCTest
@testable import CompanionCore

final class CredentialCardTests: XCTestCase {
    func testAServerPairingWithoutAKeyPointsToSeiraForThatServer() {
        for transportProtected in [false, true] {
            XCTAssertEqual(
                CredentialEntryRoute(pairedForSecrets: false, pairedWithServer: true, transportProtected: transportProtected),
                .server
            )
        }
    }

    func testAnOlderComputerPairingStillAsksToPairAgain() {
        for transportProtected in [false, true] {
            XCTAssertEqual(
                CredentialEntryRoute(pairedForSecrets: false, pairedWithServer: false, transportProtected: transportProtected),
                .pairAgain
            )
        }
    }

    func testAPairingWithTheKeyNeedsAProtectedRoute() {
        for pairedWithServer in [false, true] {
            XCTAssertEqual(
                CredentialEntryRoute(pairedForSecrets: true, pairedWithServer: pairedWithServer, transportProtected: false),
                .secureConnection
            )
            XCTAssertEqual(
                CredentialEntryRoute(pairedForSecrets: true, pairedWithServer: pairedWithServer, transportProtected: true),
                .phone
            )
        }
    }

    func testTheStatusFollowsTheSendThenTheComputer() {
        func status(
            provided: Bool = false,
            resumed: Bool = false,
            dismissed: Bool = false,
            submitted: Bool = false,
            submitting: Bool = false
        ) -> CredentialCardStatus? {
            CredentialCardStatus(
                provided: provided,
                resumed: resumed,
                dismissed: dismissed,
                submitted: submitted,
                submitting: submitting
            )
        }

        XCTAssertNil(status())
        XCTAssertEqual(status(submitting: true), .saving)
        XCTAssertEqual(status(submitted: true), .sent)
        XCTAssertEqual(status(provided: true, submitted: true), .saved)
        XCTAssertEqual(status(provided: true, resumed: true, submitted: true), .resumed)
        XCTAssertEqual(status(provided: true, submitting: true), .saved)
        XCTAssertEqual(status(dismissed: true), .notProvided)
    }
}
