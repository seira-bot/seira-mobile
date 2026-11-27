import CompanionCore
import Foundation

/// One line of the Chats list. The list shows a flat run of these, so
/// `LazyVStack` builds only the lines on screen, never a whole group.
enum HomeItem: Identifiable {
    case search
    case header(HomeGroup, open: Bool, foldable: Bool, waiting: Int)
    case tiles(group: String, bots: [Bot])
    case advancedBot(Bot, group: String, highlighted: Bool)
    case threads(Bot)
    case room(Room, group: String)
    case emptyDepartment(String, advanced: Bool)

    /// Stable across edits, so a row keeps its identity when the list changes.
    var id: String {
        switch self {
        case .search: return "search"
        case let .header(group, _, _, _): return "header:\(group.key)"
        case let .tiles(group, bots): return "tiles:\(group):\(bots.first?.id ?? "")"
        case let .advancedBot(bot, group, _): return "bot:\(group):\(bot.id)"
        case let .threads(bot): return "threads:\(bot.id)"
        case let .room(room, group): return "room:\(group):\(room.id)"
        case let .emptyDepartment(name, _): return "empty:\(name)"
        }
    }
}

enum HomeItems {
    /// The layout as a flat list. A folded group keeps only its header.
    static func build(
        layout: HomeLayout, folded: Set<String>, advanced: Bool, expandedBots: Set<String>,
        waiting: Set<String>
    ) -> [HomeItem] {
        var items: [HomeItem] = []
        func advancedRow(_ bot: Bot, group: String, highlighted: Bool) {
            items.append(.advancedBot(bot, group: group, highlighted: highlighted))
            if expandedBots.contains(bot.id) { items.append(.threads(bot)) }
        }
        if advanced, layout.department == nil {
            items.append(.search)
            if let chief = layout.chief { advancedRow(chief, group: "chief", highlighted: true) }
        }
        for group in layout.groups {
            let foldable = group.kind == .department || advanced
            let open = !foldable || FoldRules.isOpen(folded, group.key)
            let count = group.kind == .department ? HomeDotRules.waitingCount(group.bots, waiting: waiting) : 0
            items.append(.header(group, open: open, foldable: foldable, waiting: count))
            guard open else { continue }
            switch group.kind {
            case .groups:
                items += group.rooms.map { .room($0, group: group.key) }
            case .bots, .department:
                if group.bots.isEmpty && group.rooms.isEmpty && group.kind == .department {
                    items.append(.emptyDepartment(group.name ?? "", advanced: advanced))
                } else if advanced {
                    for bot in group.bots { advancedRow(bot, group: group.key, highlighted: false) }
                } else {
                    items += HomeLayoutRules.tileRows(group.bots).map { .tiles(group: group.key, bots: $0) }
                }
                items += group.rooms.map { .room($0, group: group.key) }
            }
        }
        return items
    }
}
