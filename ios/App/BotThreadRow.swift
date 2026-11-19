import CompanionCore
import SwiftUI

/// The compact thread label shared by the roster and the thread picker.
struct BotThreadRow: View {
    let task: BotTask
    var selected = false
    /// The thread is holding a queued send, from the client's queue state.
    /// The harness reports this out-of-band; the activity string never says
    /// it, so the row derives it here rather than parsing activity.
    var queued = false

    private var runtime: (title: LocalizedStringKey, icon: String, color: Color)? {
        // Ordered as the desktop orders its row: the person first, then a
        // teammate wait as a quiet clock (never a spinner), then work.
        if task.activity == "waiting-on-you" { return ("Waiting on you", "hand.raised.fill", .seiraWarningText) }
        if task.isWaitingOnTeammate { return ("Waiting on teammate", "clock", .seiraInk3) }
        if task.isWorking { return ("Working", "arrow.triangle.2.circlepath", .seiraSuccess) }
        // The queued flag is client state the harness reports out-of-band.
        if task.activity == "queued" || queued { return ("Queued", "clock", .seiraInk3) }
        return nil
    }

    /// A closed or archived thread with nothing live in it reads quieter,
    /// like the desktop's dimmed row; a live status or unread outranks both.
    private var dimmed: Bool {
        (task.isClosed || task.isArchived) && runtime == nil && task.unread != true
    }

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 5) {
                Text(verbatim: task.displayTitle)
                    .font(.seira(15, task.unread == true ? .semibold : .regular, relativeTo: .body))
                    .foregroundStyle(dimmed ? Color.seiraInk3 : Color.seiraInk)
                    .lineLimit(2)

                if runtime != nil || task.unread == true {
                    HStack(spacing: 10) {
                        if let runtime {
                            Label(runtime.title, systemImage: runtime.icon)
                                .foregroundStyle(runtime.color)
                        }
                        if task.unread == true {
                            Label("Unread", systemImage: "circle.fill")
                                .foregroundStyle(Color.seiraAccentText)
                        }
                    }
                    .font(.seira(12, .medium, relativeTo: .caption))
                }

                HStack(spacing: 5) {
                    if task.listStamp > 0 {
                        Text(ThreadStamp.updated(task.listStamp))
                    }
                    if task.pinned == true {
                        if task.listStamp > 0 { Text("·") }
                        Image(systemName: "pin.fill")
                            .accessibilityLabel("Pinned")
                    }
                    if let byline = task.bylineLabel {
                        if task.listStamp > 0 || task.pinned == true { Text("·") }
                        Text(verbatim: byline)
                    }
                }
                .font(.seira(12, relativeTo: .caption))
                .foregroundStyle(Color.seiraInk3)
                .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if selected {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(Color.seiraInk)
                    .accessibilityHidden(true)
            }
        }
        .padding(.vertical, 3)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityValue(task.isArchived ? "Archived" : dimmed ? "Closed" : "")
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
