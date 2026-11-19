import SwiftUI
import UIKit

/// A rounded rectangle with one radius per corner. iOS 16 has no
/// `UnevenRoundedRectangle`.
struct UnevenRoundedShape: InsettableShape {
    var topLeading: CGFloat = 22
    var topTrailing: CGFloat = 22
    var bottomTrailing: CGFloat = 6
    var bottomLeading: CGFloat = 22
    var inset: CGFloat = 0

    func inset(by amount: CGFloat) -> UnevenRoundedShape {
        var copy = self
        copy.inset += amount
        return copy
    }

    func path(in rect: CGRect) -> Path {
        let r = rect.insetBy(dx: inset, dy: inset)
        let limit = min(r.width, r.height) / 2
        let tl = min(topLeading, limit)
        let tr = min(topTrailing, limit)
        let br = min(bottomTrailing, limit)
        let bl = min(bottomLeading, limit)
        var path = Path()
        path.move(to: CGPoint(x: r.minX + tl, y: r.minY))
        path.addLine(to: CGPoint(x: r.maxX - tr, y: r.minY))
        path.addArc(
            center: CGPoint(x: r.maxX - tr, y: r.minY + tr), radius: tr,
            startAngle: .degrees(-90), endAngle: .degrees(0), clockwise: false
        )
        path.addLine(to: CGPoint(x: r.maxX, y: r.maxY - br))
        path.addArc(
            center: CGPoint(x: r.maxX - br, y: r.maxY - br), radius: br,
            startAngle: .degrees(0), endAngle: .degrees(90), clockwise: false
        )
        path.addLine(to: CGPoint(x: r.minX + bl, y: r.maxY))
        path.addArc(
            center: CGPoint(x: r.minX + bl, y: r.maxY - bl), radius: bl,
            startAngle: .degrees(90), endAngle: .degrees(180), clockwise: false
        )
        path.addLine(to: CGPoint(x: r.minX, y: r.minY + tl))
        path.addArc(
            center: CGPoint(x: r.minX + tl, y: r.minY + tl), radius: tl,
            startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false
        )
        path.closeSubpath()
        return path
    }
}

/// Geist for the one place SwiftUI cannot reach: measuring text in UIKit.
enum ChatFont {
    static func ui(_ size: CGFloat, medium: Bool = false, style: UIFont.TextStyle = .body) -> UIFont {
        let name = medium ? "Geist-Medium" : "Geist-Regular"
        let base = UIFont(name: name, size: size)
            ?? UIFont.systemFont(ofSize: size, weight: medium ? .medium : .regular)
        return UIFontMetrics(forTextStyle: style).scaledFont(for: base)
    }
}
