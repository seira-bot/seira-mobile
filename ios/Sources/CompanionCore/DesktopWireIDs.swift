import Foundation

/// Desktop 0.1.181 and earlier know only the OpenMausBot ids. Desktop 0.1.182 sends them and
/// accepts the Seira ids too. The phone reads both and sends the OpenMausBot ids.
public enum DesktopWireIDs {
    public static let pairingSchemes: Set<String> = ["openmausbot", "seira"]
    public static let pairingTokenPrefixes = ["omb_pair_", "seira_pair_"]
    public static let serverAppIDs: Set<String> = ["openmausbot", "seira"]
    public static let environmentPaths = [
        "/.well-known/openmausbot/environment",
        "/.well-known/seira/environment",
    ]
    public static let mdnsServiceTypes = ["_openmausbot._tcp", "_seira._tcp"]
}
