import SwiftUI
import UIKit

private struct SeiraRGBA {
    let hex: UInt32
    var alpha: CGFloat = 1

    var uiColor: UIColor {
        UIColor(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: alpha
        )
    }
}

private func seiraDynamic(_ light: SeiraRGBA, _ dark: SeiraRGBA) -> Color {
    Color(UIColor { traits in
        traits.userInterfaceStyle == .dark ? dark.uiColor : light.uiColor
    })
}

private func seiraDynamic(_ light: UInt32, _ dark: UInt32) -> Color {
    seiraDynamic(SeiraRGBA(hex: light), SeiraRGBA(hex: dark))
}

extension Color {
    static let seiraApp = seiraDynamic(0xFFFFFF, 0x111113)
    static let seiraTint = seiraDynamic(0xF4F4F6, 0x1B1B1E)
    static let seiraInk = seiraDynamic(0x141417, 0xF2F2F3)
    static let seiraInk2 = seiraDynamic(
        SeiraRGBA(hex: 0x141417, alpha: 0.68), SeiraRGBA(hex: 0xF2F2F3, alpha: 0.68))
    static let seiraInk3 = seiraDynamic(
        SeiraRGBA(hex: 0x141417, alpha: 0.60), SeiraRGBA(hex: 0xF2F2F3, alpha: 0.50))
    static let seiraHairline = seiraDynamic(
        SeiraRGBA(hex: 0x141417, alpha: 0.08), SeiraRGBA(hex: 0xF2F2F3, alpha: 0.08))
    static let seiraChatHairline = seiraDynamic(
        SeiraRGBA(hex: 0x141417, alpha: 0.09), SeiraRGBA(hex: 0xF2F2F3, alpha: 0.09))

    static let seiraPrimary = seiraDynamic(0x141417, 0xF2F2F3)
    static let seiraOnPrimary = seiraDynamic(0xFFFFFF, 0x111113)

    static let seiraAccent = seiraDynamic(0x2563EB, 0x4F8DFF)
    static let seiraAccentText = seiraDynamic(0x1D4FD0, 0x8CB5FF)
    static let seiraSuccess = seiraDynamic(0x13834F, 0x3CCF8E)
    static let seiraWarning = seiraDynamic(0xD98A00, 0xF5A524)
    static let seiraWarningText = seiraDynamic(0xA35F00, 0xF5B04A)
    static let seiraDangerText = seiraDynamic(0xC4232D, 0xFF6B70)
    static let seiraName = seiraDynamic(0xC81E4A, 0xFF7A95)

    static let seiraGlass = seiraDynamic(
        SeiraRGBA(hex: 0xFFFFFF, alpha: 0.78), SeiraRGBA(hex: 0x1E1E22, alpha: 0.72))
    static let seiraControl = seiraDynamic(0xEBEBEE, 0x26262A)
    static let seiraComposer = seiraDynamic(0xFFFFFF, 0x1C1C1F)
    static let seiraSheet = seiraDynamic(0xFFFFFF, 0x1C1C1F)
    static let seiraBubble = seiraDynamic(0xF0F0F2, 0x26262B)
    static let seiraGrabber = seiraDynamic(
        SeiraRGBA(hex: 0x101016, alpha: 0.16), SeiraRGBA(hex: 0xFFFFFF, alpha: 0.18))
    static let seiraSuccessSoft = seiraDynamic(
        SeiraRGBA(hex: 0x13834F, alpha: 0.10), SeiraRGBA(hex: 0x3CCF8E, alpha: 0.14))
    static let seiraWarningSoft = seiraDynamic(
        SeiraRGBA(hex: 0xD98A00, alpha: 0.12), SeiraRGBA(hex: 0xF5A524, alpha: 0.14))
    static let seiraScrim = seiraDynamic(
        SeiraRGBA(hex: 0x101016, alpha: 0.24), SeiraRGBA(hex: 0x000000, alpha: 0.55))

    static let seiraCallApp = seiraDynamic(0xFFFFFF, 0x0D0D0F)
    static let seiraCallControl = seiraDynamic(0xEFEFF1, 0x232327)
    static let seiraInverse = seiraDynamic(0x141417, 0xF2F2F3)
    static let seiraOnInverse = seiraDynamic(0xFFFFFF, 0x111113)
    static let seiraDangerFill = seiraDynamic(0xE5383B, 0xFF4D5E)
    static let seiraLiveDot = seiraDynamic(0x13834F, 0x3CCF8E)
    static let seiraRing1 = seiraDynamic(
        SeiraRGBA(hex: 0xFF3D64, alpha: 0.07), SeiraRGBA(hex: 0xFF3D64, alpha: 0.10))
    static let seiraRing2 = seiraDynamic(
        SeiraRGBA(hex: 0xFF3D64, alpha: 0.18), SeiraRGBA(hex: 0xFF3D64, alpha: 0.22))
    static let seiraRing3 = seiraDynamic(
        SeiraRGBA(hex: 0xFF3D64, alpha: 0.10), SeiraRGBA(hex: 0xFF3D64, alpha: 0.12))

    static let seiraSegment = seiraDynamic(0xE8E8EB, 0x26262A)
    static let seiraThumb = seiraDynamic(0xFFFFFF, 0x3A3A40)
    static let seiraTrackOff = seiraDynamic(
        SeiraRGBA(hex: 0x141417, alpha: 0.14), SeiraRGBA(hex: 0xF2F2F3, alpha: 0.18))
    static let seiraKnobOn = seiraDynamic(0xFFFFFF, 0x111113)
    static let seiraKnobOff = seiraDynamic(0xFFFFFF, 0xF2F2F3)

    static let seiraPairSuccessSoft = seiraDynamic(
        SeiraRGBA(hex: 0x13834F, alpha: 0.12), SeiraRGBA(hex: 0x3CCF8E, alpha: 0.16))

    private static let seiraPostTilesLight: [UInt32] = [0xF3E8EE, 0xE3F1F0, 0xF4ECDF]
    private static let seiraPostTilesDark: [UInt32] = [0x2A2430, 0x20302F, 0x30291F]

    static func seiraPostTile(_ index: Int) -> Color {
        let count = seiraPostTilesLight.count
        let slot = ((index % count) + count) % count
        return seiraDynamic(seiraPostTilesLight[slot], seiraPostTilesDark[slot])
    }
}

enum SeiraIdentity {
    private static let fallbackHex: UInt32 = 0x8E8E93

    /// A bot's `color` is a palette name ("green", "teal"); a hex value also works.
    private static func parse(_ hex: String?) -> UInt32 {
        guard let hex else { return fallbackHex }
        let resolved = MausPalette.hexString(hex.lowercased()) ?? hex
        let digits = resolved.hasPrefix("#") ? String(resolved.dropFirst()) : resolved
        guard digits.count == 6, let value = UInt32(digits, radix: 16) else { return fallbackHex }
        return value
    }

    static func color(hex: String?) -> Color {
        Color(SeiraRGBA(hex: parse(hex)).uiColor)
    }

    static func plate(hex: String?) -> Color {
        let identity = SeiraRGBA(hex: parse(hex)).uiColor
        return Color(UIColor { traits in
            let dark = traits.userInterfaceStyle == .dark
            let base = SeiraRGBA(hex: dark ? 0x1C1C21 : 0xF1F1F4).uiColor
            return mix(identity, base, identityShare: dark ? 0.34 : 0.26)
        })
    }

    private static func mix(_ identity: UIColor, _ base: UIColor, identityShare: CGFloat) -> UIColor {
        var ir: CGFloat = 0, ig: CGFloat = 0, ib: CGFloat = 0, ia: CGFloat = 0
        var br: CGFloat = 0, bg: CGFloat = 0, bb: CGFloat = 0, ba: CGFloat = 0
        identity.getRed(&ir, green: &ig, blue: &ib, alpha: &ia)
        base.getRed(&br, green: &bg, blue: &bb, alpha: &ba)
        let rest = 1 - identityShare
        return UIColor(
            red: ir * identityShare + br * rest,
            green: ig * identityShare + bg * rest,
            blue: ib * identityShare + bb * rest,
            alpha: 1
        )
    }
}
