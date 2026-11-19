import SwiftUI

/// One thing the "Add to chat" sheet can do, shown as a row.
struct AddToChatAction: Identifiable {
    let id: String
    let systemImage: String
    let title: LocalizedStringKey
    let subtitle: LocalizedStringKey
    var destructive = false
    var disabled = false
    let run: () -> Void
}

/// One of the three square tiles at the top of the sheet.
struct AddToChatTile: Identifiable {
    let id: String
    let systemImage: String
    let title: LocalizedStringKey
    var disabled = false
    let run: () -> Void
}

/// What the composer's + opens: a scrim and a sheet that rises from the
/// bottom. A tap on the scrim, the close button or a swipe down puts it away.
struct AddToChatSheet: View {
    let tiles: [AddToChatTile]
    let featured: [AddToChatAction]
    let more: [AddToChatAction]
    let onClose: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @GestureState private var drag: CGFloat = 0

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .bottom) {
                Color.seiraScrim
                    .ignoresSafeArea()
                    .onTapGesture(perform: onClose)
                    .accessibilityHidden(true)
                    .transition(.opacity)
                sheet(maxHeight: proxy.size.height * 0.88)
                    .transition(reduceMotion ? .opacity : .move(edge: .bottom).combined(with: .opacity))
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        }
    }

    private func sheet(maxHeight: CGFloat) -> some View {
        VStack(spacing: 14) {
            header
            ScrollView {
                VStack(spacing: 14) {
                    tileRow
                    rowCard(featured)
                    rowCard(more)
                }
            }
            .frame(maxHeight: maxHeight - 90)
        }
        .padding(.top, 8)
        .padding(.horizontal, 16)
        .padding(.bottom, 20)
        .frame(maxWidth: CompanionLayout.chatWidth)
        .background(Color.seiraSheet, in: RoundedRectangle(cornerRadius: 34, style: .continuous))
        .padding(8)
        .offset(y: reduceMotion ? 0 : max(0, drag))
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityAction(.escape, onClose)
    }

    private var header: some View {
        VStack(spacing: 6) {
            Capsule()
                .fill(Color.seiraGrabber)
                .frame(width: 36, height: 5)
            HStack {
                Text("Add to chat")
                    .font(.seira(17, .semibold, relativeTo: .headline))
                    .foregroundStyle(Color.seiraInk)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 8)
                closeButton
            }
        }
        .contentShape(Rectangle())
        .gesture(dismissDrag)
    }

    private var dismissDrag: some Gesture {
        DragGesture()
            .updating($drag) { value, state, _ in state = value.translation.height }
            .onEnded { value in
                if value.translation.height > 60 || value.predictedEndTranslation.height > 160 { onClose() }
            }
    }

    private var closeButton: some View {
        Button(action: onClose) {
            Image(systemName: "xmark")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Color.seiraInk2)
                .frame(width: 32, height: 32)
                .background(Circle().fill(Color.seiraControl))
                .frame(width: 44, height: 44)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .padding(.trailing, -6)
        .accessibilityLabel("Close")
    }

    private var tileRow: some View {
        HStack(spacing: 10) {
            ForEach(tiles) { tile in
                SheetTile(tile: tile, onClose: onClose)
            }
        }
    }

    @ViewBuilder private func rowCard(_ actions: [AddToChatAction]) -> some View {
        if !actions.isEmpty {
            VStack(spacing: 0) {
                ForEach(Array(actions.enumerated()), id: \.element.id) { index, action in
                    if index > 0 { Color.seiraChatHairline.frame(height: 1).padding(.leading, 52) }
                    SheetRow(action: action, onClose: onClose)
                }
            }
            .seiraCard(radius: 22)
        }
    }
}

private struct SheetTile: View {
    let tile: AddToChatTile
    let onClose: () -> Void
    @State private var taps = 0

    var body: some View {
        Button {
            taps += 1
            onClose()
            tile.run()
        } label: {
            VStack(spacing: 8) {
                Image(systemName: tile.systemImage)
                    .font(.system(size: 22, weight: .regular))
                Text(tile.title)
                    .font(.seira(13, .medium, relativeTo: .subheadline))
            }
            .foregroundStyle(Color.seiraInk)
            .frame(maxWidth: .infinity)
            .frame(height: 88)
            .seiraCard(radius: 20)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(tile.disabled)
        .opacity(tile.disabled ? 0.45 : 1)
        .feedback(.selection, trigger: taps)
    }
}

private struct SheetRow: View {
    let action: AddToChatAction
    let onClose: () -> Void
    @State private var taps = 0

    private var ink: Color { action.destructive ? Color.seiraDangerText : Color.seiraInk }

    var body: some View {
        Button {
            taps += 1
            onClose()
            action.run()
        } label: {
            HStack(spacing: 14) {
                Image(systemName: action.systemImage)
                    .font(.system(size: 20, weight: .regular))
                    .foregroundStyle(ink)
                    .frame(width: 24)
                VStack(alignment: .leading, spacing: 1) {
                    Text(action.title)
                        .font(.seira(15, .medium))
                        .foregroundStyle(ink)
                    Text(action.subtitle)
                        .font(.seira(12, .regular, relativeTo: .caption))
                        .foregroundStyle(Color.seiraInk3)
                        .lineLimit(2)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
            .frame(minHeight: 56)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(action.disabled)
        .opacity(action.disabled ? 0.45 : 1)
        .feedback(.selection, trigger: taps)
    }
}
