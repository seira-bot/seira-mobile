import CompanionCore
import SwiftUI

enum ChangeLookCategory: CaseIterable, Identifiable {
    case characters, hair, colour, face, extras

    var id: Self { self }

    var title: LocalizedStringKey {
        switch self {
        case .characters: return "Characters"
        case .hair: return "Hair"
        case .colour: return "Colour"
        case .face: return "Face"
        case .extras: return "Extras"
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
    @Published var category = ChangeLookCategory.characters
    @Published private(set) var celebrating = false
    @Published private(set) var shakes = 0
    @Published private(set) var toastShown = false
    @Published var saving = false
    @Published var saveFailed = false

    private var celebrateTask: Task<Void, Never>?
    private var toastTask: Task<Void, Never>?

    init(original: SeiraLook) {
        self.original = original
        self.look = original
        self.cast = Self.loadCast()
    }

    var changed: Bool { look != original }

    var mood: CharacterMood {
        if celebrating { return .happy }
        return CharacterMood.normalize(look.expression) ?? .idle
    }

    var colorHex: String? { SeiraLookCatalog.colorHex[look.color] }

    var screenIsBlack: Bool { look.faceInk == "black" }

    private static func loadCast() -> [SeiraCastMember] {
        guard let url = SeiraKit.url("cast.json"),
              let data = try? Data(contentsOf: url),
              let members = try? SeiraCast.decode(data)
        else { return [] }
        return members.filter { $0.id == "seira" } + members.filter { $0.id != "seira" }
    }

    func pick(_ member: SeiraCastMember) {
        look = member.look
    }

    func setHair(_ id: String) {
        look.hair = id
    }

    func setColor(_ name: String) {
        look.color = name
    }

    func setScreen(black: Bool) {
        if black {
            look.faceInk = "black"
        } else {
            look.faceInk = original.faceInk == "white" ? "white" : nil
        }
    }

    func setExpression(_ id: String?) {
        look.expression = id
    }

    func toggleExtra(_ id: String, shake: Bool) {
        switch SeiraLookCatalog.toggle(id, in: look.accessories) {
        case .updated(let list):
            look.accessories = list
        case .full:
            Haptics.notification(.warning)
            if shake { withAnimation(.easeOut(duration: 0.4)) { shakes += 1 } }
            showToast()
        }
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

    private func showToast() {
        toastTask?.cancel()
        toastShown = true
        UIAccessibility.post(
            notification: .announcement,
            argument: String(localized: "Up to 3 extras. Remove one first.")
        )
        toastTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 2_400_000_000)
            guard !Task.isCancelled else { return }
            self?.toastShown = false
        }
    }
}
