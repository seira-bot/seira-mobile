import SwiftUI
import CompanionCore

/// The open workspace of the computer, under the greeting. It shows only when the computer lists
/// more than one workspace.
struct WorkspaceChip: View {
    let name: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Text(verbatim: name)
                    .font(.seira(15, .medium, relativeTo: .callout))
                    .foregroundStyle(Color.seiraInk2)
                    .lineLimit(1)
                Image(systemName: "chevron.down")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color.seiraInk3)
            }
            .padding(.horizontal, 12)
            .frame(minHeight: 36)
            .background(Color.seiraTint, in: Capsule())
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .frame(maxWidth: 240, alignment: .leading)
        .accessibilityLabel("Workspaces. Now: \(name)")
        .accessibilityIdentifier("home-workspace")
    }
}

/// The chip under the greeting, the sheet it opens, and the reads that keep both current: the list is
/// read again when the computer comes back, and every few seconds while a switch waits or the sheet is open.
struct WorkspaceStrip: View {
    @ObservedObject var model: WorkspacePickerModel
    let computerID: String?
    let live: Bool
    @State private var showingSheet = false

    private struct Reading: Hashable {
        var computerID: String?
        var live: Bool
        var sheet: Bool
        var polling: Bool
    }

    private static let pollSeconds = 3

    var body: some View {
        VStack(spacing: 0) {
            if model.state.visible, let current = model.state.current {
                HStack(spacing: 0) {
                    WorkspaceChip(name: WorkspacePicker.name(current) ?? String(localized: "Unnamed workspace")) {
                        Haptics.selection()
                        showingSheet = true
                    }
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, HomeRowMetrics.side)
                .padding(.bottom, 6)
            }
        }
        .background {
            Color.clear
                .frame(width: 0, height: 0)
                .task(id: Reading(computerID: computerID, live: live, sheet: showingSheet, polling: model.state.polling)) {
                    // A new pairing empties the picker, even when the computer is not live yet.
                    guard live else {
                        await model.refresh(pairingID: nil)
                        return
                    }
                    await model.refresh(pairingID: computerID)
                    while showingSheet || model.state.polling {
                        try? await Task.sleep(for: .seconds(Self.pollSeconds))
                        if Task.isCancelled { return }
                        await model.refresh(pairingID: computerID)
                    }
                }
                .onValueChange(of: model.state.visible) { visible in
                    if !visible { showingSheet = false }
                }
                .sheet(isPresented: $showingSheet) {
                    WorkspaceSheet(model: model) { showingSheet = false }
                }
        }
    }
}

/// The workspaces of the computer, read-only: a row shows a workspace and what it waits for, and nothing in the
/// sheet starts, hurries or cancels a switch.
struct WorkspaceSheet: View {
    @ObservedObject var model: WorkspacePickerModel
    let dismiss: () -> Void

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(model.state.rows, id: \.workspace.id) { row in
                        WorkspaceRowView(row: row)
                    }
                }
            }
            .navigationTitle("Workspaces")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done", action: dismiss)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

private struct WorkspaceRowView: View {
    let row: WorkspaceRow

    var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(dotColor)
                .frame(width: 12, height: 12)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                name
                    .font(.seira(16, .medium))
                    .foregroundStyle(row.status == .unavailable ? Color.seiraInk3 : Color.seiraInk)
                    .lineLimit(1)
                status
                    .font(.seira(13, relativeTo: .subheadline))
                    .foregroundStyle(Color.seiraInk3)
            }
            Spacer(minLength: 8)
            if row.workspace.current {
                Image(systemName: "checkmark")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color.seiraInk2)
                    .accessibilityHidden(true)
            }
        }
        .frame(minHeight: 44)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("workspace-row.\(row.workspace.id)")
        .accessibilityAddTraits(row.workspace.current ? .isSelected : [])
    }

    private var dotColor: Color {
        WorkspacePicker.colorValue(row.workspace.color).map { value in
            Color(
                red: Double((value >> 16) & 0xFF) / 255,
                green: Double((value >> 8) & 0xFF) / 255,
                blue: Double(value & 0xFF) / 255
            )
        } ?? Color.seiraControl
    }

    private var name: Text {
        if let name = WorkspacePicker.name(row.workspace) { return Text(verbatim: name) }
        return Text("Unnamed workspace")
    }

    private var status: Text {
        switch row.status {
        case .none: return Text(verbatim: "")
        case .open: return Text("Open")
        case .waiting:
            if let who = WorkspacePicker.waitingFor(row.workspace) { return Text("Waiting for \(who) to finish") }
            return Text("Waiting for work to finish")
        case .unavailable: return Text("Not available")
        }
    }
}
