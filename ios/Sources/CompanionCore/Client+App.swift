import Foundation

extension CompanionClient {
    /// The document of one bot page, with the paired token the other routes use.
    public func appDocument(threadId: String, messageId: String) async throws -> ChatAppDocument {
        guard Self.isAppRouteID(threadId), Self.isAppRouteID(messageId) else { throw APIError.badURL }
        let request = try makeRequest("GET", "/api/threads/\(threadId)/messages/\(messageId)/app")
        let (data, response) = try await perform(request)
        try Self.check(response, data)
        return ChatAppDocument(data: data)
    }

    private static func isAppRouteID(_ value: String) -> Bool {
        !value.isEmpty && value.unicodeScalars.allSatisfy {
            ($0.value < 128) && (CharacterSet.alphanumerics.contains($0) || $0 == "_" || $0 == "-")
        }
    }
}
