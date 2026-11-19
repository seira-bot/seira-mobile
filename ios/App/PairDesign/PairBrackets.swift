import SwiftUI

private struct CornerBracketShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let inset: CGFloat = 1.5
        path.move(to: CGPoint(x: inset, y: rect.maxY))
        path.addArc(
            tangent1End: CGPoint(x: inset, y: inset),
            tangent2End: CGPoint(x: rect.maxX, y: inset),
            radius: 18
        )
        path.addLine(to: CGPoint(x: rect.maxX, y: inset))
        return path
    }
}

struct PairBrackets: View {
    let color: Color
    let scaled: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var scale: CGFloat { scaled && !reduceMotion ? 0.9 : 1 }
    private var animation: Animation? { reduceMotion ? nil : Animation.seira(0.3) }

    var body: some View {
        ZStack {
            bracket(rotation: 0, alignment: .topLeading)
            bracket(rotation: 90, alignment: .topTrailing)
            bracket(rotation: 180, alignment: .bottomTrailing)
            bracket(rotation: 270, alignment: .bottomLeading)
        }
        .padding(18)
        .scaleEffect(scale)
        .animation(animation, value: scaled)
        .accessibilityHidden(true)
    }

    private func bracket(rotation: Double, alignment: Alignment) -> some View {
        CornerBracketShape()
            .stroke(color, style: StrokeStyle(lineWidth: 3))
            .frame(width: 40, height: 40)
            .rotationEffect(.degrees(rotation))
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: alignment)
            .animation(Animation.seira(SeiraMotion.slow), value: color)
    }
}
