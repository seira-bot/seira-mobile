import CompanionCore
import SwiftUI

/// The folded-activity capsule: who did the work, what it was, a chevron.
struct ActivityCapsule: View {
    let text: String
    let names: [String]
    let faceBot: Bot?
    let expanded: Bool
    let label: String
    let hint: LocalizedStringKey
    var identifier: String? = nil
    let action: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                avatars
                Text(verbatim: text)
                    .font(.seira(13, .regular, relativeTo: .subheadline))
                    .foregroundStyle(Color.seiraInk2)
                    .lineLimit(1)
                chevron
            }
            .padding(.horizontal, 12)
            .frame(height: 32)
            .background(Color.seiraTint, in: Capsule())
            .padding(.vertical, 6)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityHint(hint)
        .accessibilityIdentifier(identifier ?? "")
        .padding(.vertical, -6)
    }

    private var chevron: some View {
        Image(systemName: "chevron.down")
            .font(.system(size: 10, weight: .semibold))
            .foregroundStyle(Color.seiraInk3)
            .rotationEffect(.degrees(expanded ? 180 : 0))
            .animation(reduceMotion ? nil : Animation.seira(SeiraMotion.base), value: expanded)
    }

    @ViewBuilder private var avatars: some View {
        let shown = Array(names.prefix(3))
        if let faceBot, shown.count <= 1 {
            SeiraAvatar(bot: faceBot, size: 18)
                .overlay(Circle().strokeBorder(Color.seiraApp, lineWidth: 1.5))
        } else if !shown.isEmpty {
            HStack(spacing: -5) {
                ForEach(Array(shown.enumerated()), id: \.offset) { _, name in
                    SeiraInitials(text: String(name.prefix(1)).uppercased(), size: 18, style: .ink)
                        .overlay(Circle().strokeBorder(Color.seiraApp, lineWidth: 1.5))
                }
            }
        }
    }
}

/// The list a capsule opens onto, set off by a hairline on its leading edge.
struct ActivityStepList<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            content()
        }
        .padding(.leading, 14)
        .overlay(alignment: .leading) {
            Rectangle()
                .fill(Color.seiraChatHairline)
                .frame(width: 1)
        }
        .padding(.leading, 12)
    }
}

enum ActivityDuration {
    /// "18s", "1m 5s"; nil under one second.
    static func text(milliseconds: Double) -> String? {
        let seconds = Int((milliseconds / 1_000).rounded())
        guard seconds >= 1 else { return nil }
        return Duration.seconds(seconds).formatted(.units(allowed: [.minutes, .seconds], width: .narrow))
    }
}
