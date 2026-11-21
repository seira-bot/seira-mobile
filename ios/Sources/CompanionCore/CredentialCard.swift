import Foundation

/// What an open credential request card offers on this phone.
public enum CredentialEntryRoute: Equatable, Sendable {
    /// The phone encrypts the value for the computer.
    case phone
    /// The pairing has no secret key. A new QR pairing adds one.
    case pairAgain
    /// A phone paired with a server never gets a secret key, so the value
    /// goes in through Seira for that server, in a browser.
    case server
    /// The pairing has the key, but the route does not protect credentials.
    case secureConnection

    public init(pairedForSecrets: Bool, pairedWithServer: Bool, transportProtected: Bool) {
        switch (pairedForSecrets, transportProtected) {
        case (false, _): self = pairedWithServer ? .server : .pairAgain
        case (true, false): self = .secureConnection
        case (true, true): self = .phone
        }
    }
}

/// The status line of a credential card. VoiceOver announces each change.
public enum CredentialCardStatus: Equatable, Sendable {
    case saving
    case sent
    case saved
    case resumed
    case notProvided

    public init?(provided: Bool, resumed: Bool, dismissed: Bool, submitted: Bool, submitting: Bool) {
        if provided {
            self = resumed ? .resumed : .saved
        } else if dismissed {
            self = .notProvided
        } else if submitted {
            self = .sent
        } else if submitting {
            self = .saving
        } else {
            return nil
        }
    }
}
