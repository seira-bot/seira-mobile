import CompanionCore
import SwiftUI

enum CompactPhase: Equatable {
    case idle, working, done, failed
}

/// The Advanced mode chips above the field. Scrolls sideways when they do not fit.
struct ComposerToolChips: View {
    let tools: [ComposerTool]
    let contextPercent: Int?
    let onTool: (ComposerTool) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(tools, id: \.self) { tool in
                    Button {
                        Haptics.selection()
                        onTool(tool)
                    } label: {
                        Text(title(tool))
                            .font(.seira(14, .medium, relativeTo: .subheadline))
                            .foregroundStyle(Color.seiraInk)
                            .padding(.horizontal, 14)
                            .frame(minHeight: 44)
                            .background(Color.seiraControl, in: Capsule())
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(accessibilityLabel(tool))
                    .accessibilityIdentifier("composer-tool-\(tool.rawValue)")
                }
            }
            .padding(.horizontal, 4)
        }
    }

    private func title(_ tool: ComposerTool) -> String {
        switch tool {
        case .teach: return "Teach"
        case .goal: return "Goal"
        case .context: return "Context \(contextPercent ?? 0)%"
        }
    }

    private func accessibilityLabel(_ tool: ComposerTool) -> String {
        switch tool {
        case .teach: return "Teach a new skill"
        case .goal: return "Set a goal"
        case .context: return "Context, \(contextPercent ?? 0) percent used. Tap for details."
        }
    }
}

/// How full the conversation is, with the one action that frees room.
struct ContextSheet: View {
    let meter: ContextMeter
    let phase: CompactPhase
    let onCompact: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Context")
                .font(.seira(20, .semibold, relativeTo: .title3))
                .foregroundStyle(Color.seiraInk)
            bar
            Text("\(ChatAdvanced.compactCount(meter.tokens)) of \(ChatAdvanced.compactCount(meter.window)) tokens used")
                .font(.seira(15, .regular, relativeTo: .subheadline))
                .foregroundStyle(Color.seiraInk2)
            Button(action: onCompact) {
                HStack(spacing: 8) {
                    if phase == .working { ProgressView() }
                    Text(buttonTitle)
                        .font(.seira(15, .semibold))
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(SeiraControlButtonStyle(height: 48))
            .disabled(phase == .working || phase == .done)
            .accessibilityIdentifier("context-compact")
            if phase == .failed {
                Text("Could not compact this conversation. Try again.")
                    .font(.seira(13, .regular, relativeTo: .footnote))
                    .foregroundStyle(Color.seiraWarningText)
            }
            Spacer(minLength: 0)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.seiraBg)
        .presentationDetents([.height(300), .medium])
        .accessibilityIdentifier("context-sheet")
    }

    private var buttonTitle: LocalizedStringKey {
        switch phase {
        case .working: return "Compacting…"
        case .done: return "Context compacted"
        default: return "Compact context"
        }
    }

    private var bar: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(Color.seiraControl)
                Capsule()
                    .fill(Color.seiraPrimary)
                    .frame(width: proxy.size.width * CGFloat(meter.percent) / 100)
                    .animation(reduceMotion ? nil : .easeOut(duration: 0.25), value: meter.percent)
            }
        }
        .frame(height: 10)
        .accessibilityElement()
        .accessibilityLabel("\(meter.percent) percent used")
    }
}
