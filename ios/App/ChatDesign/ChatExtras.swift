import CompanionCore
import SwiftUI

/// The message a reply quotes.
struct ReplyTarget: Equatable {
    let id: String
    let text: String
    let who: String
}

/// The quote above the composer, with a way to drop it.
struct ReplyStrip: View {
    let target: ReplyTarget
    let onClear: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Rectangle().fill(Color.seiraAcc).frame(width: 3)
            VStack(alignment: .leading, spacing: 2) {
                Text("Replying to \(target.who)")
                    .font(.seira(12, .semibold, relativeTo: .caption))
                    .foregroundStyle(Color.seiraMut)
                Text(verbatim: ReplyQuote.firstLine(target.text))
                    .font(.seira(14, relativeTo: .subheadline))
                    .foregroundStyle(Color.seiraFg)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
            Button(action: onClear) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 20))
                    .foregroundStyle(Color.seiraMut)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel("Cancel reply")
        }
        .padding(.leading, 16)
        .frame(maxWidth: CompanionLayout.chatWidth)
        .frame(maxWidth: .infinity)
        .accessibilityIdentifier("reply-strip")
    }
}

/// A centred day label between messages.
struct DaySeparator: View {
    let label: DayLabel

    private var text: String {
        switch label.kind {
        case .today: return String(localized: "Today")
        case .yesterday: return String(localized: "Yesterday")
        case .date: return label.date.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated))
        }
    }

    var body: some View {
        Text(verbatim: text)
            .font(.seira(12, .semibold, relativeTo: .caption))
            .foregroundStyle(Color.seiraMut)
            .padding(.horizontal, 12)
            .padding(.vertical, 4)
            .background(Color.seiraS2, in: Capsule())
            .frame(maxWidth: .infinity)
            .padding(.top, 10)
            .accessibilityAddTraits(.isHeader)
    }
}

/// The bots in a group chat.
struct RoomMembersSheet: View {
    let room: Room
    @EnvironmentObject private var session: Session
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List(room.memberIds.compactMap { session.state.bot($0) }) { bot in
                HStack(spacing: 12) {
                    SeiraAvatar(bot: bot, size: 36)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(verbatim: bot.name).font(.headline)
                        if !bot.title.isEmpty {
                            Text(verbatim: bot.title).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("Members")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
        }
    }
}
