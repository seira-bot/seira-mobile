import CompanionCore
import SwiftUI
import UIKit

private enum LadderMetrics {
    static let inset: CGFloat = 28
    static let trackHeight: CGFloat = 56
    static let labelHeight: CGFloat = 44
    static let labelWidth: CGFloat = 56
    static let thumb: CGFloat = 40
    static let ring: CGFloat = 48
    static let hintSeconds: UInt64 = 2_200_000_000
}

private struct LadderShake: GeometryEffect {
    var travel: Double

    var animatableData: Double {
        get { travel }
        set { travel = newValue }
    }

    func effectValue(size: CGSize) -> ProjectionTransform {
        let offset = 7 * sin(travel * .pi * 4)
        return ProjectionTransform(CGAffineTransform(translationX: CGFloat(offset), y: 0))
    }
}

struct ApprovalLadder: View {
    let shown: ApprovalMode?
    let working: Bool
    let onChoose: (ApprovalMode) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var dragFraction: Double?
    @State private var hovered: ApprovalMode?
    @State private var pending: ApprovalMode?
    @State private var holdProgress = 0.0
    @State private var holdTask: Task<Void, Never>?
    @State private var holdFired = false
    @State private var stepTicks = 0
    @State private var shakes = 0
    @State private var hintVisible = false
    @State private var hintGeneration = 0
    @State private var iconAngle = 0.0

    private var stepShown: ApprovalMode? {
        ModeLadder.index(of: shown) == nil ? nil : shown
    }

    private var thumbMode: ApprovalMode? { hovered ?? pending ?? stepShown }

    private var thumbFraction: Double? {
        dragFraction ?? ModeLadder.fraction(of: pending ?? shown)
    }

    private var moveAnimation: Animation {
        reduceMotion
            ? Animation.easeOut(duration: SeiraMotion.fast)
            : Animation.spring(response: 0.32, dampingFraction: 0.67)
    }

    private var holdSeconds: Double { Double(ModeLadder.holdMilliseconds) / 1000 }

    var body: some View {
        ladder
            .modifier(LadderShake(travel: Double(shakes)))
            .animation(reduceMotion ? nil : Animation.linear(duration: 0.34), value: shakes)
            .allowsHitTesting(!working)
            .opacity(working ? 0.6 : 1)
            .feedback(.selection, trigger: stepTicks)
            .onValueChange(of: working) { isWorking in
                if !isWorking { pending = nil }
            }
            .onValueChange(of: thumbMode) { _ in spinIcon() }
            .modifier(LadderAccessibility(
                value: accessibilityValue,
                adjust: adjust
            ))
    }

    private var ladder: some View {
        VStack(spacing: 0) {
            GeometryReader { proxy in trackArea(proxy.size.width) }
                .frame(height: LadderMetrics.trackHeight)
            GeometryReader { proxy in labelsRow(proxy.size.width) }
                .frame(height: LadderMetrics.labelHeight)
            detail
        }
    }

    // MARK: Track

    private func trackArea(_ width: CGFloat) -> some View {
        let usable = width - 2 * LadderMetrics.inset
        return ZStack(alignment: .leading) {
            trackBar(usable)
            ForEach(0..<ModeLadder.steps.count, id: \.self) { index in
                detent(index, usable: usable)
            }
            thumb(usable)
        }
        .frame(width: width, height: LadderMetrics.trackHeight)
        .contentShape(Rectangle())
        .gesture(dragGesture(width))
    }

    private func trackBar(_ usable: CGFloat) -> some View {
        let heat = LinearGradient(
            colors: [.seiraSuccess, .seiraWarning, .seiraDangerFill],
            startPoint: .leading,
            endPoint: .trailing
        )
        return Capsule()
            .fill(heat)
            .opacity(0.85)
            .frame(width: usable, height: 8)
            .offset(x: LadderMetrics.inset)
    }

    private func detent(_ index: Int, usable: CGFloat) -> some View {
        let fraction = CGFloat(index) / CGFloat(ModeLadder.steps.count - 1)
        return Circle()
            .fill(Color.seiraSheet)
            .frame(width: 6, height: 6)
            .offset(x: LadderMetrics.inset + usable * fraction - 3)
    }

    private func thumb(_ usable: CGFloat) -> some View {
        let fraction = CGFloat(thumbFraction ?? 0)
        let x = LadderMetrics.inset + usable * fraction - LadderMetrics.thumb / 2
        let following = dragFraction != nil
        return thumbBody
            .offset(x: x)
            .opacity(thumbFraction == nil ? 0 : 1)
            .animation(following ? nil : moveAnimation, value: x)
    }

    private var thumbBody: some View {
        let mode = thumbMode ?? .ask
        let heat = heatColour(mode)
        return ZStack {
            Circle()
                .fill(Color.seiraTint)
                .shadow(color: .black.opacity(0.16), radius: 3, x: 0, y: 1)
            Circle().stroke(heat, lineWidth: 2)
            Image(systemName: mode.symbol)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(heat)
                .rotationEffect(.degrees(iconAngle))
            Circle()
                .trim(from: 0, to: holdProgress)
                .stroke(Color.seiraDangerFill, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .frame(width: LadderMetrics.ring, height: LadderMetrics.ring)
        }
        .frame(width: LadderMetrics.thumb, height: LadderMetrics.thumb)
    }

    private func heatColour(_ mode: ApprovalMode) -> Color {
        switch mode {
        case .ask, .edits: return .seiraSuccess
        case .auto: return .seiraWarning
        case .full, .custom: return .seiraDangerFill
        }
    }

    // MARK: Labels and detail

    private func labelsRow(_ width: CGFloat) -> some View {
        let usable = width - 2 * LadderMetrics.inset
        let last = CGFloat(ModeLadder.steps.count - 1)
        return ZStack(alignment: .topLeading) {
            ForEach(0..<ModeLadder.steps.count, id: \.self) { index in
                stepLabel(ModeLadder.steps[index])
                    .position(
                        x: LadderMetrics.inset + usable * CGFloat(index) / last,
                        y: LadderMetrics.labelHeight / 2
                    )
            }
        }
        .frame(width: width, height: LadderMetrics.labelHeight)
    }

    private func stepLabel(_ mode: ApprovalMode) -> some View {
        let ink: Color = mode == thumbMode ? .seiraInk : .seiraInk2
        return labelGestures(mode) {
            Text(mode.title)
                .font(.seira(13, .medium, relativeTo: .footnote))
                .foregroundStyle(ink)
                .frame(width: LadderMetrics.labelWidth, height: LadderMetrics.labelHeight)
                .contentShape(Rectangle())
        }
        .accessibilityIdentifier("approval-mode.\(mode.rawValue)")
        .accessibilityHidden(true)
    }

    @ViewBuilder private func labelGestures<Label: View>(
        _ mode: ApprovalMode,
        @ViewBuilder label: () -> Label
    ) -> some View {
        if ModeLadder.needsHold(mode) {
            label().onLongPressGesture(
                minimumDuration: holdSeconds,
                maximumDistance: 30,
                perform: { completeHold() },
                onPressingChanged: { pressing in fullLabelPressing(pressing) }
            )
        } else {
            label().onTapGesture { tap(mode) }
        }
    }

    private var detail: some View {
        ZStack(alignment: .topLeading) {
            if hintVisible {
                Text("Hold to turn on Full")
                    .font(.seira(13, .medium))
                    .foregroundStyle(Color.seiraWarningText)
                    .transition(detailTransition)
            } else if let mode = thumbMode {
                Text(mode.subtitle)
                    .font(.seira(13, .regular))
                    .foregroundStyle(Color.seiraInk2)
                    .fixedSize(horizontal: false, vertical: true)
                    .id(mode.rawValue)
                    .transition(detailTransition)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 40, alignment: .topLeading)
        .animation(moveAnimation, value: detailKey)
    }

    private var detailKey: String {
        hintVisible ? "hint" : (thumbMode?.rawValue ?? "")
    }

    private var detailTransition: AnyTransition {
        if reduceMotion { return .opacity }
        return .asymmetric(
            insertion: AnyTransition.offset(x: 0, y: 6).combined(with: .opacity),
            removal: .opacity
        )
    }

    // MARK: Input

    private func dragGesture(_ width: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in dragChanged(x: value.location.x, width: width) }
            .onEnded { _ in dragEnded() }
    }

    private func dragChanged(x: CGFloat, width: CGFloat) {
        let usable = max(width - 2 * LadderMetrics.inset, 1)
        let raw = Double((x - LadderMetrics.inset) / usable)
        let fraction = min(max(raw, 0), 1)
        dragFraction = fraction
        let step = ModeLadder.step(atFraction: fraction)
        guard step != hovered else { return }
        if hovered != nil { stepTicks += 1 }
        hovered = step
        if ModeLadder.needsHold(step), shown != step {
            startHold()
        } else {
            cancelHold()
        }
    }

    private func dragEnded() {
        let target = hovered
        let fired = holdFired
        cancelHold()
        holdFired = false
        dragFraction = nil
        hovered = nil
        guard !fired, !working, let target, target != shown else { return }
        if ModeLadder.needsHold(target) {
            rejectFull()
        } else {
            request(target)
        }
    }

    private func tap(_ mode: ApprovalMode) {
        guard mode != shown else { return }
        stepTicks += 1
        request(mode)
    }

    private func request(_ mode: ApprovalMode) {
        pending = mode
        onChoose(mode)
    }

    private func fullLabelPressing(_ pressing: Bool) {
        guard shown != .full else { return }
        if pressing {
            startRing()
        } else if holdFired {
            holdFired = false
        } else {
            cancelHold()
            rejectFull()
        }
    }

    // MARK: Hold

    private func startHold() {
        guard holdTask == nil, !holdFired else { return }
        startRing()
        let nanoseconds = UInt64(ModeLadder.holdMilliseconds) * 1_000_000
        holdTask = Task {
            try? await Task.sleep(nanoseconds: nanoseconds)
            guard !Task.isCancelled else { return }
            completeHold()
        }
    }

    private func startRing() {
        withAnimation(.linear(duration: holdSeconds)) { holdProgress = 1 }
    }

    private func cancelHold() {
        holdTask?.cancel()
        holdTask = nil
        withAnimation(.easeOut(duration: SeiraMotion.fast)) { holdProgress = 0 }
    }

    private func completeHold() {
        holdTask?.cancel()
        holdTask = nil
        holdFired = true
        withAnimation(.easeOut(duration: SeiraMotion.base)) { holdProgress = 0 }
        Haptics.impact(.heavy)
        request(.full)
    }

    private func rejectFull() {
        if !reduceMotion { shakes += 1 }
        hintVisible = true
        hintGeneration += 1
        let generation = hintGeneration
        UIAccessibility.post(
            notification: .announcement,
            argument: String(localized: "Hold to turn on Full")
        )
        Task {
            try? await Task.sleep(nanoseconds: LadderMetrics.hintSeconds)
            if hintGeneration == generation { hintVisible = false }
        }
    }

    private func spinIcon() {
        guard !reduceMotion else { return }
        var reset = Transaction()
        reset.disablesAnimations = true
        withTransaction(reset) { iconAngle = -55 }
        DispatchQueue.main.async {
            withAnimation(moveAnimation) { iconAngle = 0 }
        }
    }

    // MARK: Accessibility

    private var accessibilityValue: Text {
        let mode = shown ?? .ask
        return Text(mode.title) + Text(verbatim: ", ") + Text(mode.subtitle)
    }

    private func adjust(_ direction: AccessibilityAdjustmentDirection) {
        guard !working else { return }
        guard let current = ModeLadder.index(of: shown) else {
            if shown != .ask { request(.ask) }
            return
        }
        let delta: Int
        switch direction {
        case .increment: delta = 1
        case .decrement: delta = -1
        @unknown default: return
        }
        let next = min(max(current + delta, 0), ModeLadder.steps.count - 1)
        if next != current { request(ModeLadder.steps[next]) }
    }
}

private struct LadderAccessibility: ViewModifier {
    let value: Text
    let adjust: (AccessibilityAdjustmentDirection) -> Void

    func body(content: Content) -> some View {
        content
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Approvals")
            .accessibilityValue(value)
            .accessibilityIdentifier("approval-mode-ladder")
            .accessibilityAdjustableAction(adjust)
    }
}
