import CompanionCore
import SwiftUI

enum ChangeLookLabels {
    static func hair(_ id: String) -> LocalizedStringKey {
        switch id {
        case "seira": return "Seira"
        case "seira-long": return "Seira long"
        case "bob": return "Bob"
        case "twin-tails": return "Twin tails"
        case "ponytail": return "Ponytail"
        case "long-straight": return "Long straight"
        case "bun": return "Bun"
        case "long-wavy": return "Long wavy"
        case "short-neat": return "Short neat"
        case "center-part": return "Centre part"
        case "spiky": return "Spiky"
        case "slick-back": return "Slick back"
        case "long-tied": return "Long tied"
        case "long-loose": return "Long loose"
        case "buzz": return "Buzz"
        case "messy": return "Messy"
        case "none": return "No hair"
        default: return LocalizedStringKey(id)
        }
    }

    static func color(_ name: String) -> LocalizedStringKey {
        switch name {
        case "green": return "Green"
        case "blue": return "Blue"
        case "red": return "Red"
        case "orange": return "Orange"
        case "purple": return "Purple"
        case "cyan": return "Cyan"
        case "pink": return "Pink"
        case "yellow": return "Yellow"
        case "teal": return "Teal"
        case "coral": return "Coral"
        default: return LocalizedStringKey(name)
        }
    }

    static func expression(_ id: String) -> LocalizedStringKey {
        switch id {
        case "idle": return "Calm"
        case "happy": return "Happy"
        case "surprised": return "Surprised"
        case "drowsy": return "Sleepy"
        case "thinking": return "Thinking"
        default: return LocalizedStringKey(id)
        }
    }

    static func accessory(_ id: String) -> LocalizedStringKey {
        switch id {
        case "glasses": return "Glasses"
        case "sunglasses": return "Sunglasses"
        case "earring": return "Earring"
        case "headphones": return "Headphones"
        case "headset-mic": return "Headset"
        case "ribbon": return "Ribbon"
        case "hairpin": return "Hairpin"
        case "tiara": return "Tiara"
        case "cap": return "Cap"
        case "antenna": return "Antenna"
        case "scarf": return "Scarf"
        case "bowtie": return "Bow tie"
        default: return LocalizedStringKey(id)
        }
    }

    static func slot(_ slot: SeiraLookSlot) -> LocalizedStringKey {
        switch slot {
        case .face: return "Face"
        case .ears: return "Ears"
        case .hair: return "Hair"
        case .top: return "Top"
        case .neck: return "Neck"
        }
    }
}

enum ChangeLookMetrics {
    static let animation = Animation.timingCurve(0.22, 1, 0.36, 1, duration: 0.18)
    static let threeColumns = Array(repeating: GridItem(.flexible(), spacing: 12), count: 3)
}

/// A tile with a caption. The tile carries the selection ring and the spoken label.
struct ChangeLookTile<Art: View>: View {
    let title: Text
    let selected: Bool
    var height: CGFloat = 104
    var radius: CGFloat = 22
    var captionSize: CGFloat = 13
    let action: () -> Void
    @ViewBuilder let art: () -> Art

    var body: some View {
        VStack(spacing: 6) {
            Button(action: action) { tile }
                .buttonStyle(.plain)
            title
                .font(.seira(captionSize, relativeTo: .caption))
                .foregroundStyle(Color.seiraInk2)
                .lineLimit(1)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(title)
        .accessibilityAddTraits(traits)
        .accessibilityAction(.default, action)
        .feedback(.selection, trigger: selected)
    }

    private var traits: AccessibilityTraits {
        var traits: AccessibilityTraits = .isButton
        if selected { traits.insert(.isSelected) }
        return traits
    }

    private var tile: some View {
        art()
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .background(Color.seiraTint, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay(ring)
            .contentShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
    }

    private var ring: some View {
        let stroke: Color = selected ? .seiraInk : .clear
        return RoundedRectangle(cornerRadius: radius, style: .continuous)
            .strokeBorder(stroke, lineWidth: 2)
    }
}

struct ChangeLookChip: View {
    let title: Text
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            label
                .frame(minHeight: 44)
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
        .feedback(.selection, trigger: selected)
    }

    private var label: some View {
        let ink: Color = selected ? .seiraOnPrimary : .seiraInk
        let fill: Color = selected ? .seiraPrimary : .seiraControl
        return title
            .font(.seira(14, .medium, relativeTo: .subheadline))
            .foregroundStyle(ink)
            .padding(.horizontal, 16)
            .frame(minWidth: 44)
            .frame(height: 36)
            .background(fill, in: Capsule())
    }
}

struct ChangeLookSheetChrome: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 16.4, *) {
            content.presentationBackground(.clear)
        } else {
            content
        }
    }
}
