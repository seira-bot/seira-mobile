import CompanionCore
import SwiftUI

enum ChangeLookLabels {
    static func hair(_ id: String) -> LocalizedStringKey {
        LocalizedStringKey(SeiraKit.styles[id]?.label ?? id)
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
        case "idle": return "Idle"
        case "happy": return "Happy"
        case "curious": return "Curious"
        case "drowsy": return "Drowsy"
        case "working": return "Working"
        case "thinking": return "Thinking"
        case "listening": return "Listening"
        case "sleeping": return "Sleeping"
        case "suspicious": return "Suspicious"
        case "proud": return "Proud"
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

struct ChangeLookShake: GeometryEffect {
    var travel: CGFloat

    var animatableData: CGFloat {
        get { travel }
        set { travel = newValue }
    }

    func effectValue(size: CGSize) -> ProjectionTransform {
        ProjectionTransform(CGAffineTransform(translationX: 8 * sin(travel * .pi * 4), y: 0))
    }
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

struct ChangeLookSegmented: View {
    let blackSelected: Bool
    let choose: (Bool) -> Void

    var body: some View {
        HStack(spacing: 0) {
            segment(Text("White"), isBlack: false)
            segment(Text("Black"), isBlack: true)
        }
        .padding(.horizontal, 4)
        .frame(height: 44)
        .background(Color.seiraSegment, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .feedback(.selection, trigger: blackSelected)
        .accessibilityElement(children: .contain)
    }

    private func segment(_ title: Text, isBlack: Bool) -> some View {
        let isSelected = isBlack == blackSelected
        let ink: Color = isSelected ? .seiraInk : .seiraInk2
        return Button { choose(isBlack) } label: {
            title
                .font(.seira(14, .medium, relativeTo: .subheadline))
                .foregroundStyle(ink)
                .frame(maxWidth: .infinity, minHeight: 44)
                .background { thumb(isSelected) }
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    @ViewBuilder private func thumb(_ isSelected: Bool) -> some View {
        if isSelected {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color.seiraThumb)
                .shadow(color: .black.opacity(0.12), radius: 1, x: 0, y: 1)
                .frame(height: 36)
        }
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
