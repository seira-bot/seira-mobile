import Foundation

/// The row of a bot's page, as the server sends it in a message of kind `app`.
public struct ChatApp: Codable, Hashable, Sendable {
    public struct Mcp: Codable, Hashable, Sendable {
        public var server: String?
        public var serverTitle: String?
        public var tool: String?
    }

    public var source: String
    public var title: String
    public var doc: String
    public var rev: Int
    public var bytes: Int
    public var height: Double?
    public var border: Bool
    public var blocked: String?
    public var live: Bool?
    public var mcp: Mcp?

    private enum CodingKeys: String, CodingKey {
        case source, title, doc, rev, bytes, height, border, blocked, live, mcp
    }

    public init(
        source: String = "",
        title: String = "",
        doc: String = "",
        rev: Int = 0,
        bytes: Int = 0,
        height: Double? = nil,
        border: Bool = true,
        blocked: String? = nil,
        live: Bool? = nil,
        mcp: Mcp? = nil
    ) {
        self.source = source
        self.title = title
        self.doc = doc
        self.rev = rev
        self.bytes = bytes
        self.height = height
        self.border = border
        self.blocked = blocked
        self.live = live
        self.mcp = mcp
    }

    /// A payload this build cannot read is an app with no document: the
    /// message keeps its text and the rest of the page still decodes.
    public init(from decoder: any Decoder) throws {
        guard let values = try? decoder.container(keyedBy: CodingKeys.self) else {
            self.init()
            return
        }
        self.init(
            source: (try? values.decodeIfPresent(String.self, forKey: .source)) ?? "",
            title: (try? values.decodeIfPresent(String.self, forKey: .title)) ?? "",
            doc: (try? values.decodeIfPresent(String.self, forKey: .doc)) ?? "",
            rev: (try? values.decodeIfPresent(Int.self, forKey: .rev)) ?? 0,
            bytes: (try? values.decodeIfPresent(Int.self, forKey: .bytes)) ?? 0,
            height: try? values.decodeIfPresent(Double.self, forKey: .height),
            border: (try? values.decodeIfPresent(Bool.self, forKey: .border)) ?? true,
            blocked: try? values.decodeIfPresent(String.self, forKey: .blocked),
            live: try? values.decodeIfPresent(Bool.self, forKey: .live),
            mcp: try? values.decodeIfPresent(Mcp.self, forKey: .mcp)
        )
    }

    /// One HTML page whose document a phone can show: stored, not blocked,
    /// not a folder that reloads, not an MCP app.
    public var isOpenablePage: Bool {
        source == "html" && !doc.isEmpty && blocked == nil && mcp == nil && live != true
    }
}

/// The AppDocument the server returns for one app message, kept as the JSON
/// it came in so a host page gets it unchanged. It holds the page's HTML, so
/// it never goes in a log or on disk.
public struct ChatAppDocument: Sendable {
    public let data: Data

    public init(data: Data) {
        self.data = data
    }

    private var object: [String: Any]? {
        (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
    }

    public var isMcpApp: Bool {
        object?["tool"] is [String: Any]
    }

    /// A folder page loads its files from the computer, which a phone does not serve.
    public var isFolderPage: Bool {
        guard let csp = object?["csp"] as? String else { return false }
        let directives = csp.split(separator: ";").map { $0.trimmingCharacters(in: .whitespaces) }
        guard let baseURI = directives.first(where: { $0.hasPrefix("base-uri ") }) else { return false }
        return baseURI.dropFirst("base-uri ".count).trimmingCharacters(in: .whitespaces) != "'none'"
    }

    public var canOpenOnPhone: Bool {
        guard let srcdoc = object?["srcdoc"] as? String else { return false }
        return !srcdoc.isEmpty && !isMcpApp && !isFolderPage
    }

    /// The origins the page may reach: `network.connect` and `network.resources`.
    public var allowedOrigins: Set<String> {
        guard let network = object?["network"] as? [String: Any] else { return [] }
        var origins = Set<String>()
        for key in ["connect", "resources"] {
            for case let origin as String in (network[key] as? [Any]) ?? [] { origins.insert(origin) }
        }
        return origins
    }

    /// The page may only open links: it cannot send messages, change what
    /// the bot knows, or call a server.
    public func viewOnly() throws -> ChatAppDocument {
        guard var json = object else { throw APIError.transport("The computer sent something this app couldn't read.") }
        json["can"] = [
            "serverTools": false,
            "serverResources": false,
            "message": false,
            "modelContext": false,
            "openLinks": true,
        ]
        return ChatAppDocument(data: try JSONSerialization.data(withJSONObject: json))
    }

    /// The `can` object of the document, as booleans.
    public var capabilities: [String: Bool] {
        (object?["can"] as? [String: Bool]) ?? [:]
    }
}
