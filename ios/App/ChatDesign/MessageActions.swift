import CompanionCore
import SwiftUI

/// What a long press on a message offers. A nil closure leaves its row out.
struct MessageActionsSheet: View {
    let reactions: [String]
    let onReact: ((String) -> Void)?
    let onReply: (() -> Void)?
    let onCopy: (() -> Void)?
    let onSelectText: (() -> Void)?
    let onEdit: (() -> Void)?
    let editEnabled: Bool
    let dismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let onReact {
                HStack {
                    ForEach(reactions, id: \.self) { emoji in
                        Button {
                            dismiss()
                            onReact(emoji)
                        } label: {
                            Text(verbatim: emoji)
                                .font(.system(size: 24))
                                .frame(maxWidth: .infinity, minHeight: 48)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("React with \(emoji)")
                        .accessibilityIdentifier("message-react.\(emoji)")
                    }
                }
                .padding(.horizontal, 6)
                .background(Color.seiraControl, in: Capsule())
                .padding(.horizontal, 20)
                .padding(.vertical, 6)
            }
            if let onReply {
                row("arrowshape.turn.up.left", "Reply", id: "message-sheet.reply", action: onReply)
            }
            if let onCopy {
                row("doc.on.doc", "Copy", id: "message-sheet.copy", action: onCopy)
            }
            if let onSelectText {
                row("selection.pin.in.out", "Select Text", id: "message-sheet.select", action: onSelectText)
            }
            if let onEdit {
                row("pencil", "Edit and retry", id: "message-sheet.edit", enabled: editEnabled, action: onEdit)
            }
        }
        .padding(.top, 20)
        .padding(.bottom, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.seiraApp)
        .accessibilityIdentifier("message-actions-sheet")
    }

    private func row(
        _ symbol: String, _ title: LocalizedStringKey, id: String, enabled: Bool = true,
        action: @escaping () -> Void
    ) -> some View {
        Button {
            dismiss()
            action()
        } label: {
            HStack(spacing: 14) {
                Image(systemName: symbol)
                    .font(.system(size: 17, weight: .regular))
                    .frame(width: 24)
                Text(title)
                    .font(.seira(16, .medium))
                Spacer(minLength: 0)
            }
            .foregroundStyle(enabled ? Color.seiraInk : Color.seiraInk3)
            .padding(.horizontal, 24)
            .frame(minHeight: 52)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityIdentifier(id)
    }
}

/// Copy, Reply and the full menu under a finished bot reply.
struct MessageActionRow: View {
    let onCopy: () -> Void
    let onReply: () -> Void
    let onMore: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            glyph("doc.on.doc", "Copy", id: "message-action-copy", action: onCopy)
            glyph("arrowshape.turn.up.left", "Reply", id: "message-action-reply", action: onReply)
            glyph("ellipsis", "More", id: "message-action-more", action: onMore)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("message-action-row")
    }

    private func glyph(
        _ symbol: String, _ label: LocalizedStringKey, id: String, action: @escaping () -> Void
    ) -> some View {
        Button {
            Haptics.selection()
            action()
        } label: {
            Image(systemName: symbol)
                .font(.system(size: 14, weight: .regular))
                .foregroundStyle(Color.seiraInk2)
                .frame(width: 36, height: 36)
                .background(Circle().fill(Color.seiraControl))
                .frame(width: 44, height: 44)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityIdentifier(id)
    }
}
