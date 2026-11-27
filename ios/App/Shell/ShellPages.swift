import CompanionCore
import SwiftUI
import UIKit

private struct ReturnToChatsKey: EnvironmentKey {
    static var defaultValue: () -> Void { {} }
}

extension EnvironmentValues {
    /// Goes back to the Chats tab, past every page pushed on top of it.
    var returnToChats: () -> Void {
        get { self[ReturnToChatsKey.self] }
        set { self[ReturnToChatsKey.self] = newValue }
    }
}

struct TeamMapPage: View {
    @EnvironmentObject private var session: Session
    @Environment(\.returnToChats) private var returnToChats
    @State private var map: TeamMap?

    var body: some View {
        let layout = TeamMapRules.layout(session.state)
        List {
            if layout.isEmpty {
                Text("No one on the team yet.").foregroundStyle(Color.seiraMut)
            }
            if let chief = layout.chief {
                Section { botRow(chief) } header: { Text("Seira") }
            }
            ForEach(layout.groups) { group in
                Section {
                    ForEach(group.bots) { botRow($0) }
                } header: {
                    if let name = group.name { Text(verbatim: name) } else { Text("Bots") }
                }
            }
        }
        .navigationTitle("Team map")
        .navigationBarTitleDisplayMode(.inline)
        .task { map = await session.teamMap() }
        .refreshable { map = await session.teamMap() }
    }

    private func botRow(_ bot: Bot) -> some View {
        NavigationLink {
            BotProfilePage(botId: bot.id, onDeleted: returnToChats)
        } label: {
            HStack(spacing: 12) {
                SeiraAvatar(bot: bot, size: 36)
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: bot.name).font(.headline)
                    if !bot.title.isEmpty {
                        Text(verbatim: bot.title).font(.caption).foregroundStyle(.secondary)
                    }
                }
                Spacer(minLength: 0)
                statusLabel(TeamMapRules.status(map, botId: bot.id))
            }
        }
    }

    @ViewBuilder
    private func statusLabel(_ status: TeamStatus?) -> some View {
        switch status {
        case .working: Text("Working").font(.caption).foregroundStyle(Color.seiraOk)
        case .waiting: Text("Waiting").font(.caption).foregroundStyle(Color.seiraWarn)
        case nil: EmptyView()
        }
    }
}

struct DepartmentMemoryPage: View {
    @EnvironmentObject private var session: Session

    var body: some View {
        List {
            NavigationLink { TeamMemoryView(section: "") } label: {
                Label("General", systemImage: "tray.full")
            }
            ForEach(session.state.sidebarSections) { section in
                NavigationLink { TeamMemoryView(section: section.name) } label: {
                    Label { Text(verbatim: section.name) } icon: { Image(systemName: "folder") }
                }
            }
        }
        .navigationTitle("Department memory")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct ComputersPage: View {
    @EnvironmentObject private var session: Session
    @State private var copied = false

    var body: some View {
        List {
            Section("Paired computers") {
                ForEach(session.connections) { computer in
                    computerRow(computer)
                }
            }
            if let current = session.connection {
                Section("Address") {
                    HStack {
                        Text(verbatim: "\(current.host):\(current.port)")
                            .font(.system(.body, design: .monospaced))
                            .lineLimit(1)
                        Spacer()
                        Button {
                            UIPasteboard.general.string = "\(current.host):\(current.port)"
                            copied = true
                        } label: {
                            Text(copied ? LocalizedStringKey("Copied") : LocalizedStringKey("Copy"))
                        }
                    }
                    Button("Try reconnecting") { session.connect() }
                }
            }
            if session.canAdminister {
                Section {
                    NavigationLink { UsageView() } label: {
                        Label("AI usage", systemImage: "chart.bar")
                    }
                    NavigationLink { ConnectedAppsView() } label: {
                        Label("Connected apps", systemImage: "link")
                    }
                }
            }
            let bots = session.state.bots.filter { $0.hidden != true }
            if !bots.isEmpty {
                Section("Bot screens") {
                    ForEach(bots) { bot in
                        NavigationLink { ComputerView(bot: bot) } label: {
                            HStack(spacing: 12) {
                                SeiraAvatar(bot: bot, size: 32)
                                Text(verbatim: bot.name)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("Computers")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func computerRow(_ computer: Connection) -> some View {
        let isCurrent = computer.id == session.connection?.id
        return Button {
            if !isCurrent { session.switchComputer(to: computer.id) }
        } label: {
            HStack {
                Label { Text(verbatim: computer.name) } icon: { Image(systemName: "desktopcomputer") }
                Spacer()
                if isCurrent { Image(systemName: "checkmark").foregroundStyle(Color.seiraOk) }
            }
        }
        .foregroundStyle(Color.seiraInk)
    }
}

struct BotProfilesPage: View {
    @EnvironmentObject private var session: Session
    @Environment(\.returnToChats) private var returnToChats

    var body: some View {
        List(session.state.bots.filter { $0.hidden != true }) { bot in
            NavigationLink { BotProfilePage(botId: bot.id, onDeleted: returnToChats) } label: {
                HStack(spacing: 12) {
                    SeiraAvatar(bot: bot, size: 40)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(verbatim: bot.name).font(.headline)
                        if !bot.title.isEmpty {
                            Text(verbatim: bot.title).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
        .navigationTitle("Bot profiles")
        .navigationBarTitleDisplayMode(.inline)
    }
}
