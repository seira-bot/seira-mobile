import SwiftUI
import CompanionCore

/// The same reversible narration fold as desktop. Activity preferences apply
/// to tool receipts independently, so Hidden still offers this compact row.
struct AssistantTurnChip: View {
    let turn: AssistantTurnFold
    let context: TranscriptRowContext
    let actions: TranscriptActions
    /// A search hit inside this fold: open it and bring the reply into view.
    var revealedMessageId: String? = nil
    @State private var expanded = false

    private var speakers: [String] {
        var seen: [String] = []
        for name in turn.messages.compactMap({ $0.from?.name }) where !seen.contains(name) { seen.append(name) }
        return seen
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ActivityCapsule(
                text: turn.label, names: speakers, faceBot: context.faceBot, expanded: expanded,
                label: turn.label,
                hint: expanded ? "Hides intermediate replies" : "Shows intermediate replies",
                identifier: "assistant-turn.\(turn.turnId)"
            ) {
                withSeiraAnimation(SeiraMotion.base) { expanded.toggle() }
                Haptics.selection()
            }

            if expanded {
                ForEach(Array(turn.messages.enumerated()), id: \.element.id) { index, message in
                    MessageRow(
                        message: message, endsRun: index == turn.messages.count - 1,
                        context: context, actions: actions
                    )
                    .equatable()
                    .id(message.id)
                    .onAppear {
                        if revealedMessageId == message.id { actions.reveal(messageId: message.id) }
                    }
                }
            }
        }
        .onValueChange(of: revealedMessageId, initial: true) { id in
            if turn.messages.contains(where: { $0.id == id }) { expanded = true }
        }
    }
}
