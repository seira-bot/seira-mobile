import SwiftUI
import UIKit

enum SeiraWeight {
    case regular, medium, semibold

    fileprivate var fontName: String {
        switch self {
        case .regular: return "Geist-Regular"
        case .medium: return "Geist-Medium"
        case .semibold: return "Geist-SemiBold"
        }
    }

    fileprivate var systemWeight: Font.Weight {
        switch self {
        case .regular: return .regular
        case .medium: return .medium
        case .semibold: return .semibold
        }
    }
}

private let seiraFontsRegistered = UIFont(name: "Geist-Regular", size: 12) != nil

extension Font {
    /// Geist at `size`, scaled with Dynamic Type relative to `style`.
    /// Falls back to the system font when Geist is not registered.
    static func seira(
        _ size: CGFloat,
        _ weight: SeiraWeight = .regular,
        relativeTo style: Font.TextStyle = .body
    ) -> Font {
        guard seiraFontsRegistered else {
            return .system(size: size, weight: weight.systemWeight)
        }
        return .custom(weight.fontName, size: size, relativeTo: style)
    }
}
