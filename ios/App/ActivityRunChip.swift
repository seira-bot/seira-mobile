// A folded run of activity: one line standing in for several chips.
//
// What "Reduced" buys the reader. A bot that ran four tools in a row leaves
// four receipts, and past the second one they stop carrying information —
// so the run becomes a single line that says how many there were, and opens
// back into the real chips on a tap.
//
// Failures never arrive here: `transcriptRows` breaks them out before a run
// is formed, so anything folded is either finished or still going.
import SwiftUI
import CompanionCore

struct ActivityRunChip: View {
    let items: [Message]
    /// Where an "Opened thread" chip inside the run goes once unfolded.
    var openThread: ((ThreadRef) -> Void)? = nil
    /// The bot's profile, in a bot chat, for the capsule's avatar.
    var faceBot: Bot? = nil
    @State private var expanded = false

    private var running: Bool { items.contains { $0.tool?.ok == nil } }

    private var summary: String {
        running ? "Running \(items.count) steps" : "Ran \(items.count) steps"
    }

    private var capsuleText: String {
        guard !running, let first = items.map(\.at).min(), let last = items.map(\.at).max(),
              let took = ActivityDuration.text(milliseconds: last - first) else { return summary }
        return "\(summary) · \(took)"
    }

    private var speakers: [String] {
        var seen: [String] = []
        for name in items.compactMap({ $0.from?.name }) where !seen.contains(name) { seen.append(name) }
        return seen
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            capsule
            if expanded {
                unfoldedSteps
            }
        }
        .padding(.leading, 2)
    }

    private var capsule: some View {
        ActivityCapsule(
            text: capsuleText, names: speakers, faceBot: faceBot, expanded: expanded,
            label: summary, hint: expanded ? "Hides the steps" : "Shows the steps"
        ) {
            withSeiraAnimation(SeiraMotion.base) { expanded.toggle() }
            Haptics.selection()
        }
    }

    private var unfoldedSteps: some View {
        ActivityStepList {
            ForEach(items, id: \.id) { item in
                ActivityChip(
                    tool: item.tool, threadRef: item.threadRef, openThread: openThread,
                    outputIsProse: item.isTeammateReport
                )
            }
        }
        .transition(.opacity)
    }
}
