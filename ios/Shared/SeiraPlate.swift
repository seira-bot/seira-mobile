// The identity colours of a bot and the plate its character stands on.
// Compiled into the app and the widget extension, so both draw the same plate.
import CompanionCore
import SwiftUI
import UIKit

enum SeiraPalette {
    private static let fallback = "#8E8E93"

    static func color(_ name: String) -> Color {
        Color(hex: SeiraLookCatalog.colorHex[name] ?? fallback)
    }

    /// The hex value of a palette name, or nil for a name the palette lacks.
    static func hexString(_ name: String) -> String? { SeiraLookCatalog.colorHex[name] }
}

extension Color {
    init(hex: String) {
        var value: UInt64 = 0
        Scanner(string: hex.replacingOccurrences(of: "#", with: "")).scanHexInt64(&value)
        self.init(
            .sRGB,
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255,
            opacity: 1
        )
    }
}

enum SeiraPlate {
    private static let fallbackHex: UInt32 = 0x8E8E93

    /// A bot's `color` is a palette name ("green", "teal"); a hex value also works.
    static func rgb(_ hex: String?) -> UInt32 {
        guard let hex else { return fallbackHex }
        let resolved = SeiraPalette.hexString(hex.lowercased()) ?? hex
        let digits = resolved.hasPrefix("#") ? String(resolved.dropFirst()) : resolved
        guard digits.count == 6, let value = UInt32(digits, radix: 16) else { return fallbackHex }
        return value
    }

    private static func channels(_ hex: UInt32) -> (CGFloat, CGFloat, CGFloat) {
        (CGFloat((hex >> 16) & 0xFF) / 255, CGFloat((hex >> 8) & 0xFF) / 255, CGFloat(hex & 0xFF) / 255)
    }

    /// The identity colour mixed into the surface, so the plate stays quiet next to text.
    static func uiColor(_ hex: String?, dark: Bool) -> UIColor {
        let identity = channels(rgb(hex))
        let base = channels(dark ? 0x1C1C21 : 0xF1F1F4)
        let share: CGFloat = dark ? 0.34 : 0.26
        let rest = 1 - share
        return UIColor(
            red: identity.0 * share + base.0 * rest,
            green: identity.1 * share + base.1 * rest,
            blue: identity.2 * share + base.2 * rest,
            alpha: 1
        )
    }

    static func dynamicColor(_ hex: String?) -> UIColor {
        UIColor { traits in uiColor(hex, dark: traits.userInterfaceStyle == .dark) }
    }

    /// The character head on its plate as a bitmap, for places that take only a picture of a person.
    nonisolated static func plateImage(look: SeiraLook, mood: CharacterMood = .idle, size: CGFloat, scale: CGFloat) -> UIImage? {
        guard let head = SeiraAvatarHead.image(look: look, mood: mood, size: size, scale: scale) else { return nil }
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        let side = CGSize(width: size, height: size)
        return UIGraphicsImageRenderer(size: side, format: format).image { context in
            let rect = CGRect(origin: .zero, size: side)
            uiColor(look.color, dark: false).setFill()
            context.cgContext.fillEllipse(in: rect)
            head.draw(in: rect)
        }
    }
}

extension Chat {
    /// The look a chat wears where only one head fits: a bot's own, a room's colour on the Seira look.
    var seiraLook: SeiraLook {
        switch self {
        case let .bot(bot):
            return SeiraLook(bot: bot)
        case .room:
            return SeiraLook(hair: "seira", color: color)
        }
    }
}

/// The character head on its plate, drawn from a bitmap. For widgets and Live Activities.
struct SeiraPlateHead: View {
    let look: SeiraLook
    var mood: CharacterMood = .idle
    var size: CGFloat

    var body: some View {
        ZStack {
            Circle().fill(Color(SeiraPlate.dynamicColor(look.color)))
            Image(uiImage: SeiraAvatarHead.image(look: look, mood: mood, size: size, scale: 2) ?? UIImage())
                .resizable()
                .frame(width: size, height: size)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

extension SeiraPlateHead {
    init(chat: Chat, face: String, size: CGFloat) {
        self.init(look: chat.seiraLook, mood: CharacterMood(rawValue: face) ?? .idle, size: size)
    }
}
