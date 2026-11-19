import Foundation

public enum SeiraLookSlot: String, CaseIterable, Sendable {
    case face, ears, hair, top, neck
}

public enum SeiraExtraToggle: Equatable, Sendable {
    case updated([String])
    case full
}

/// The values a Seira look may take. They must match the desktop catalog:
/// the server reads an id it does not know as the default look.
public enum SeiraLookCatalog {
    public static let hairIds: [String] = [
        "seira", "seira-long", "bob", "twin-tails", "ponytail", "long-straight",
        "bun", "long-wavy", "short-neat", "center-part", "spiky", "slick-back",
        "long-tied", "long-loose", "buzz", "messy", "none",
    ]

    public static let defaultHair = "seira"

    /// An unknown or retired body id reads as `defaultHair`, as on the desktop.
    public static func hair(_ id: String?) -> String {
        guard let id, hairIds.contains(id) else { return defaultHair }
        return id
    }

    private static let accessorySlots: [(id: String, slot: SeiraLookSlot)] = [
        ("glasses", .face), ("sunglasses", .face),
        ("earring", .ears), ("headphones", .ears), ("headset-mic", .ears),
        ("ribbon", .hair), ("hairpin", .hair), ("tiara", .hair),
        ("cap", .top), ("antenna", .top),
        ("scarf", .neck), ("bowtie", .neck),
    ]

    public static let accessoryIds: [String] = accessorySlots.map { $0.id }

    public static func slot(of accessory: String) -> SeiraLookSlot? {
        accessorySlots.first { $0.id == accessory }?.slot
    }

    public static let maxAccessories = 3

    /// Keeps known ids, the first one in each slot, at most `maxAccessories`, in input order.
    public static func accessories(_ ids: [String]?) -> [String] {
        var usedSlots = Set<SeiraLookSlot>()
        var result: [String] = []
        for id in ids ?? [] {
            guard result.count < maxAccessories,
                  let itemSlot = slot(of: id),
                  !usedSlots.contains(itemSlot)
            else { continue }
            usedSlots.insert(itemSlot)
            result.append(id)
        }
        return result
    }

    /// An id that is on comes off. An id whose slot is taken swaps with the one
    /// worn there. A new slot while the limit is reached gives `.full`.
    public static func toggle(_ id: String, in current: [String]) -> SeiraExtraToggle {
        let worn = accessories(current)
        if worn.contains(id) { return .updated(worn.filter { $0 != id }) }
        guard let newSlot = slot(of: id) else { return .updated(worn) }
        if let index = worn.firstIndex(where: { slot(of: $0) == newSlot }) {
            var swapped = worn
            swapped[index] = id
            return .updated(swapped)
        }
        if worn.count >= maxAccessories { return .full }
        return .updated(worn + [id])
    }

    public static let starterColors: [String] = [
        "green", "blue", "red", "orange", "purple", "cyan", "pink", "yellow", "teal", "coral",
    ]

    public static let colorHex: [String: String] = [
        "green": "#009957",
        "blue": "#377FE6",
        "red": "#D94B52",
        "orange": "#E78531",
        "purple": "#8057C8",
        "cyan": "#0EA5C6",
        "pink": "#D84F8B",
        "yellow": "#D8A729",
        "teal": "#01A492",
        "coral": "#E5634E",
        "silver": "#A8ADB4",
        "chrome": "#B3BAC4",
    ]

    public static let defaultColor = "green"

    /// A known colour name, else the colour of a new or reset bot.
    public static func color(_ name: String?) -> String {
        guard let name, colorHex[name] != nil else { return defaultColor }
        return name
    }

    public static let faceInks: [String] = ["white", "black"]

    public static let expressions: [String] = [
        "idle", "happy", "curious", "drowsy", "working",
        "thinking", "listening", "sleeping", "suspicious", "proud",
    ]
}

public struct SeiraLook: Equatable, Hashable, Sendable {
    public var hair: String
    public var color: String
    public var faceInk: String?
    public var expression: String?
    public var accessories: [String]

    public init(
        hair: String,
        color: String,
        faceInk: String? = nil,
        expression: String? = nil,
        accessories: [String] = []
    ) {
        self.hair = SeiraLookCatalog.hair(hair)
        self.color = SeiraLookCatalog.color(color)
        self.faceInk = faceInk.flatMap { SeiraLookCatalog.faceInks.contains($0) ? $0 : nil }
        self.expression = expression.flatMap { SeiraLookCatalog.expressions.contains($0) ? $0 : nil }
        self.accessories = SeiraLookCatalog.accessories(accessories)
    }

    public init(bot: Bot) {
        self.init(
            hair: SeiraLookCatalog.hair(bot.mascotBody),
            color: SeiraLookCatalog.color(bot.color),
            faceInk: bot.mascotFaceColor,
            expression: bot.mascotExpression,
            accessories: bot.mascotAccessories ?? []
        )
    }

    /// Red is the colour of the app palette nearest to Seira pink (#FF2A57).
    public static let seira = SeiraLook(hair: "seira", color: "red")
}

public struct SeiraCastMember: Decodable, Equatable, Identifiable, Sendable {
    public var id: String
    public var name: String
    public var mascotBody: String
    public var mascotAccessories: [String]
    public var color: String

    public init(id: String, name: String, mascotBody: String, mascotAccessories: [String], color: String) {
        self.id = id
        self.name = name
        self.mascotBody = mascotBody
        self.mascotAccessories = mascotAccessories
        self.color = color
    }

    private enum CodingKeys: String, CodingKey {
        case id, name, mascotBody, mascotAccessories, color
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        name = try container.decode(String.self, forKey: .name)
        mascotBody = try container.decode(String.self, forKey: .mascotBody)
        mascotAccessories = try container.decodeIfPresent([String].self, forKey: .mascotAccessories) ?? []
        color = try container.decode(String.self, forKey: .color)
    }

    public var look: SeiraLook {
        SeiraLook(hair: mascotBody, color: color, faceInk: nil, expression: nil, accessories: mascotAccessories)
    }
}

public enum SeiraCast {
    public static func decode(_ data: Data) throws -> [SeiraCastMember] {
        try JSONDecoder().decode([SeiraCastMember].self, from: data)
    }

    /// The look of a random member that differs from `current`. With no other
    /// member it gives the first member's look, or `current` for an empty list.
    public static func surprise<G: RandomNumberGenerator>(
        from members: [SeiraCastMember],
        current: SeiraLook,
        using generator: inout G
    ) -> SeiraLook {
        let others = members.map { $0.look }.filter { $0 != current }
        if let pick = others.randomElement(using: &generator) { return pick }
        return members.first?.look ?? current
    }
}

/// The body of `PATCH /api/bots/:id` for a look change. Only fields that
/// differ are sent. A cleared face ink or expression goes out as JSON null.
public struct BotLookPatch: Encodable, Equatable, Sendable {
    public enum Change<Value: Equatable & Sendable>: Equatable, Sendable {
        case set(Value)
        case clear
    }

    public var color: String?
    public var mascotBody: String?
    public var mascotAccessories: [String]?
    public var mascotFaceColor: Change<String>?
    public var mascotExpression: Change<String>?

    public init(
        color: String? = nil,
        mascotBody: String? = nil,
        mascotAccessories: [String]? = nil,
        mascotFaceColor: Change<String>? = nil,
        mascotExpression: Change<String>? = nil
    ) {
        self.color = color
        self.mascotBody = mascotBody
        self.mascotAccessories = mascotAccessories
        self.mascotFaceColor = mascotFaceColor
        self.mascotExpression = mascotExpression
    }

    public init(from old: SeiraLook, to new: SeiraLook) {
        self.init(
            color: new.color != old.color ? new.color : nil,
            mascotBody: new.hair != old.hair ? new.hair : nil,
            mascotAccessories: new.accessories != old.accessories ? new.accessories : nil,
            mascotFaceColor: Self.change(from: old.faceInk, to: new.faceInk),
            mascotExpression: Self.change(from: old.expression, to: new.expression)
        )
    }

    private static func change(from old: String?, to new: String?) -> Change<String>? {
        guard old != new else { return nil }
        if let new { return .set(new) }
        return .clear
    }

    public var isEmpty: Bool {
        color == nil && mascotBody == nil && mascotAccessories == nil
            && mascotFaceColor == nil && mascotExpression == nil
    }

    private enum CodingKeys: String, CodingKey {
        case color, mascotBody, mascotAccessories, mascotFaceColor, mascotExpression
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(color, forKey: .color)
        try container.encodeIfPresent(mascotBody, forKey: .mascotBody)
        try container.encodeIfPresent(mascotAccessories, forKey: .mascotAccessories)
        try Self.encode(mascotFaceColor, forKey: .mascotFaceColor, in: &container)
        try Self.encode(mascotExpression, forKey: .mascotExpression, in: &container)
    }

    private static func encode(
        _ change: Change<String>?,
        forKey key: CodingKeys,
        in container: inout KeyedEncodingContainer<CodingKeys>
    ) throws {
        guard let change else { return }
        switch change {
        case .set(let value): try container.encode(value, forKey: key)
        case .clear: try container.encodeNil(forKey: key)
        }
    }
}
