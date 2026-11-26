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
    static let seiraBg = seiraDynamic(0xF4F4F6, 0x0B0C0E)
    static let seiraS1 = seiraDynamic(0xFFFFFF, 0x15171B)
    static let seiraS2 = seiraDynamic(0xE9EAEE, 0x1F2228)
    static let seiraS3 = seiraDynamic(0xD9DBE1, 0x2C3038)
    static let seiraLine = seiraDynamic(
        SeiraRGBA(hex: 0x0A0B0D, alpha: 0.10), SeiraRGBA(hex: 0xFFFFFF, alpha: 0.08))
    static let seiraLine2 = seiraDynamic(
        SeiraRGBA(hex: 0x0A0B0D, alpha: 0.20), SeiraRGBA(hex: 0xFFFFFF, alpha: 0.17))
    static let seiraFg = seiraDynamic(0x121317, 0xEDEEF0)
    static let seiraMut = seiraDynamic(0x454A53, 0xA8ADB5)
    static let seiraDim = seiraDynamic(0x5C626D, 0x8B919B)
    static let seiraAcc = seiraDynamic(0xC42A4D, 0xFF3D67)
    static let seiraAccFg = seiraDynamic(0xA81E3D, 0xFF7D98)
    static let seiraAccSoft = seiraDynamic(
        SeiraRGBA(hex: 0xC42A4D, alpha: 0.10), SeiraRGBA(hex: 0xFF3D67, alpha: 0.16))
    static let seiraOnAcc = seiraDynamic(0xFFFFFF, 0x1B0409)
    static let seiraOk = seiraDynamic(0x0A6A44, 0x3DDC97)
    static let seiraWarn = seiraDynamic(0x7D4E00, 0xFFB547)
    static let seiraBad = seiraDynamic(0xB3261E, 0xFF8A80)
    static let seiraInv = seiraDynamic(0x121317, 0xF2F3F5)
    static let seiraOnInv = seiraDynamic(0xFFFFFF, 0x0B0C0E)

    static let seiraApp = seiraBg
    static let seiraTint = seiraS1
    static let seiraInk = seiraFg
    static let seiraInk2 = seiraMut
    static let seiraInk3 = seiraDim
    static let seiraHairline = seiraLine
    static let seiraChatHairline = seiraLine

    static let seiraPrimary = seiraInv
    static let seiraOnPrimary = seiraOnInv

    static let seiraAccent = seiraAcc
    static let seiraAccentText = seiraAccFg
    static let seiraSuccess = seiraOk
    static let seiraWarning = seiraDynamic(0xB8740A, 0xFFB547)
    static let seiraWarningText = seiraWarn
    static let seiraDangerText = seiraBad
    static let seiraName = seiraAccFg

    static let seiraGlass = seiraDynamic(
        SeiraRGBA(hex: 0xFFFFFF, alpha: 0.78), SeiraRGBA(hex: 0x15171B, alpha: 0.72))
    static let seiraControl = seiraS2
    static let seiraComposer = seiraS1
    static let seiraSheet = seiraS1
    static let seiraBubble = seiraS2
    static let seiraGrabber = seiraDynamic(
        SeiraRGBA(hex: 0x101016, alpha: 0.16), SeiraRGBA(hex: 0xFFFFFF, alpha: 0.18))
    static let seiraSuccessSoft = seiraDynamic(
        SeiraRGBA(hex: 0x0A6A44, alpha: 0.10), SeiraRGBA(hex: 0x3DDC97, alpha: 0.14))
    static let seiraWarningSoft = seiraDynamic(
        SeiraRGBA(hex: 0x7D4E00, alpha: 0.12), SeiraRGBA(hex: 0xFFB547, alpha: 0.14))
    static let seiraScrim = seiraDynamic(
        SeiraRGBA(hex: 0x000000, alpha: 0.62), SeiraRGBA(hex: 0x000000, alpha: 0.62))

    static let seiraCallApp = seiraDynamic(0xF4F4F6, 0x070809)
    static let seiraCallControl = seiraDynamic(0xE9EAEE, 0x232327)
    static let seiraInverse = seiraInv
    static let seiraOnInverse = seiraOnInv
    static let seiraDangerFill = seiraDynamic(0xE5383B, 0xFF4D5E)
    static let seiraLiveDot = seiraOk
    static let seiraRing1 = seiraDynamic(
        SeiraRGBA(hex: 0xFF3D64, alpha: 0.07), SeiraRGBA(hex: 0xFF3D64, alpha: 0.10))
    static let seiraRing2 = seiraDynamic(
        SeiraRGBA(hex: 0xFF3D64, alpha: 0.18), SeiraRGBA(hex: 0xFF3D64, alpha: 0.22))
    static let seiraRing3 = seiraDynamic(
        SeiraRGBA(hex: 0xFF3D64, alpha: 0.10), SeiraRGBA(hex: 0xFF3D64, alpha: 0.12))

    static let seiraSegment = seiraS2
    static let seiraThumb = seiraS3
    static let seiraTrackOff = seiraDynamic(
        SeiraRGBA(hex: 0x121317, alpha: 0.14), SeiraRGBA(hex: 0xEDEEF0, alpha: 0.18))
    static let seiraKnobOn = seiraDynamic(0xFFFFFF, 0x0B0C0E)
    static let seiraKnobOff = seiraDynamic(0xFFFFFF, 0xF2F3F5)

    static let seiraPairSuccessSoft = seiraDynamic(
        SeiraRGBA(hex: 0x0A6A44, alpha: 0.12), SeiraRGBA(hex: 0x3DDC97, alpha: 0.16))

    private static let seiraPostTilesLight: [UInt32] = [0xF3E8EE, 0xE3F1F0, 0xF4ECDF]
    private static let seiraPostTilesDark: [UInt32] = [0x2A2430, 0x20302F, 0x30291F]

    static func seiraPostTile(_ index: Int) -> Color {
        let count = seiraPostTilesLight.count
        let slot = ((index % count) + count) % count
        return seiraDynamic(seiraPostTilesLight[slot], seiraPostTilesDark[slot])
    }
}

enum SeiraIdentity {
    static func color(hex: String?) -> Color {
        Color(SeiraRGBA(hex: SeiraPlate.rgb(hex)).uiColor)
    }

    static func plate(hex: String?) -> Color {
        Color(SeiraPlate.dynamicColor(hex))
    }
}
