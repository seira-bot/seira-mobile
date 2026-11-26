import CompanionCore
import SwiftUI

struct AutomationsTab: View {
    private enum Mode: Hashable { case list, calendar }

    @EnvironmentObject private var session: Session
    @State private var mode = Mode.list
    @State private var routines: [Routine] = []
    @State private var loading = true
    @State private var editing: Routine?
    @State private var creating = false
    @State private var deleting: Routine?

    var body: some View {
        VStack(spacing: 0) {
            header
            Picker("View", selection: $mode) {
                Text("List").tag(Mode.list)
                Text("Calendar").tag(Mode.calendar)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.bottom, 8)
            switch mode {
            case .list: list
            case .calendar: RoutineCalendarView(embedded: true)
            }
        }
        .sheet(isPresented: $creating) {
            RoutineEditorView(routine: nil) { await reload() }
        }
        .sheet(item: $editing) { routine in
            RoutineEditorView(routine: routine) { await reload() }
        }
        .confirmationDialog(
            "Delete \(deleting?.name ?? "this routine")?",
            isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }),
            titleVisibility: .visible
        ) {
            Button("Delete routine", role: .destructive) {
                guard let routine = deleting else { return }
                Task {
                    if await session.deleteRoutine(routine) { await reload() }
                    deleting = nil
                }
            }
        } message: {
            Text("Past run receipts remain available.")
        }
        .task { await reload() }
    }

    private var header: some View {
        HStack {
            Text("Automations")
                .font(.seira(28, .semibold, relativeTo: .largeTitle))
                .foregroundStyle(Color.seiraInk)
                .accessibilityAddTraits(.isHeader)
            Spacer()
            Button { creating = true } label: {
                Image(systemName: "plus")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(Color.seiraInk)
            }
            .buttonStyle(SeiraCircleButtonStyle(size: 48, fill: .seiraS1))
            .accessibilityLabel("New routine")
            .accessibilityIdentifier("new-routine")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }

    @ViewBuilder private var list: some View {
        if routines.isEmpty && !loading {
            EmptyStateView(
                "No routines",
                systemImage: "calendar.badge.plus",
                description: Text("Schedule recurring or one-time agent work.")
            )
        } else {
            List {
                ForEach(routines) { routine in
                    AutomationRow(
                        routine: routine,
                        bot: session.state.bot(routine.botId),
                        toggle: { enabled in Task { await toggle(routine, enabled: enabled) } },
                        edit: { editing = routine },
                        runNow: { Task { await runNow(routine) } },
                        delete: { deleting = routine }
                    )
                    .listRowBackground(Color.seiraS1)
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .refreshable { await reload() }
        }
    }

    private func reload() async {
        loading = true
        let loaded = await session.loadRoutines()
        routines = loaded.routines.sorted {
            ($0.nextRunAt ?? .greatestFiniteMagnitude) < ($1.nextRunAt ?? .greatestFiniteMagnitude)
        }
        loading = false
    }

    private func toggle(_ routine: Routine, enabled: Bool) async {
        guard routine.canToggle() else { return }
        _ = await session.setRoutineEnabled(routine, enabled: enabled)
        await reload()
    }

    private func runNow(_ routine: Routine) async {
        _ = await session.runRoutine(routine)
        await reload()
    }
}

private struct AutomationRow: View {
    let routine: Routine
    let bot: Bot?
    let toggle: (Bool) -> Void
    let edit: () -> Void
    let runNow: () -> Void
    let delete: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button(action: edit) { summary }
                .buttonStyle(.plain)
            control
        }
        .contextMenu {
            Button(action: runNow) { Label("Run now", systemImage: "play.fill") }
            Button(action: edit) { Label("Edit", systemImage: "pencil") }
            Button(role: .destructive, action: delete) { Label("Delete", systemImage: "trash") }
        }
    }

    private var summary: some View {
        HStack(spacing: 12) {
            if let bot {
                SeiraAvatar(bot: bot, size: 40)
            } else {
                Image(systemName: "calendar.badge.exclamationmark").frame(width: 40, height: 40)
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(verbatim: routine.name)
                    .font(.seira(16, .medium, relativeTo: .body))
                    .foregroundStyle(Color.seiraInk)
                    .lineLimit(2)
                Text(verbatim: routine.schedule.summary)
                    .font(.seira(13, relativeTo: .caption))
                    .foregroundStyle(Color.seiraInk2)
                    .lineLimit(2)
            }
            Spacer(minLength: 0)
        }
        .contentShape(Rectangle())
    }

    @ViewBuilder private var control: some View {
        if routine.canToggle() {
            Toggle("Active", isOn: Binding(get: { routine.enabled }, set: toggle))
                .labelsHidden()
        } else {
            Text("Finished")
                .font(.seira(12, relativeTo: .caption))
                .foregroundStyle(Color.seiraInk3)
        }
    }
}
