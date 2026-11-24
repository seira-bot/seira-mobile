import CompanionCore
import SwiftUI

enum ChangeLookCategory: CaseIterable, Identifiable {
    case hair, face, extras, colour

    var id: Self { self }

    var title: LocalizedStringKey {
        switch self {
        case .hair: return "Hair"
        case .face: return "Face"
        case .extras: return "Extras"
        case .colour: return "Colour"
        }
    }
}

@MainActor
final class ChangeLookModel: ObservableObject {
    let original: SeiraLook
    let cast: [SeiraCastMember]

    @Published var look: SeiraLook {
        didSet { if saveFailed { saveFailed = false } }
    }
    @Published var category = ChangeLookCategory.hair
    @Published private(set) var celebrating = false
    @Published var saving = false
    @Published var saveFailed = false

    private var celebrateTask: Task<Void, Never>?

    init(original: SeiraLook) {
        self.original = original
        self.look = original
        self.cast = ChangeLookModel.loadCast()
    }

    var changed: Bool { look != original }

    var mood: CharacterMood {
        if celebrating { return .happy }
        return CharacterMood.normalize(look.expression) ?? .idle
    }

    func isExpressionSelected(_ id: String) -> Bool {
        id == "idle" ? (look.expression == nil || look.expression == "idle") : look.expression == id
    }

    var colorHex: String? { SeiraLookCatalog.colorHex[look.color] }

    private static func loadCast() -> [SeiraCastMember] {
        guard let url = SeiraKit.url("cast.json"),
              let data = try? Data(contentsOf: url),
              let members = try? SeiraCast.decode(data)
        else { return [] }
        return members.filter { $0.id == "seira" } + members.filter { $0.id != "seira" }
    }

    func setHair(_ id: String) {
        look.hair = id
    }

    func setColor(_ name: String) {
        look.color = name
    }

    func setExpression(_ id: String) {
        if id == "idle" {
            look.expression = original.expression == "idle" ? "idle" : nil
        } else {
            look.expression = id
        }
    }

    func toggleExtra(_ id: String) {
        look.accessories = SeiraLookCatalog.toggle(id, in: look.accessories)
    }

    func surprise() {
        var generator = SystemRandomNumberGenerator()
        look = SeiraCast.surprise(from: cast, current: look, using: &generator)
    }

    func celebrate() {
        celebrateTask?.cancel()
        celebrating = true
        celebrateTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 1_200_000_000)
            guard !Task.isCancelled else { return }
            self?.celebrating = false
        }
    }
}
