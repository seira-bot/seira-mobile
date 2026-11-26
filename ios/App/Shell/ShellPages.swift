import CompanionCore
import SwiftUI
import UIKit

struct TeamMapPage: View {
    @EnvironmentObject private var session: Session
    @State private var map: TeamMap?

    private var state: CompanionState { session.state }

    var body: some View {
        List {
            if let chief = state.unsectionedChief {
                Section("Seira") { botRow(chief) }
            }
            ForEach(state.sidebarSections) { section in
                Section {
                    ForEach(section.chiefs + section.bots) { botRow($0) }
                    ForEach(section.channels) { Label($0.name, systemImage: "person.2") }
                } header: {
                    Text(verbatim: section.name)
                }
            }
            let others = state.unsectionedBots + state.pinnedBots
            if !others.isEmpty {
                Section("Bots") { ForEach(others) { botRow($0) } }
            }
            if let map, !map.running.isEmpty || !map.queued.isEmpty {
                Section("Working together") {
                    Text("\(map.running.count) running")
                    Text("\(map.queued.count) waiting")
                }
            }
        }
        .navigationTitle("Team map")
        .navigationBarTitleDisplayMode(.inline)
        .task { map = await session.teamMap() }
    }

    private func botRow(_ bot: Bot) -> some View {
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

    var body: some View {
        List(session.state.bots.filter { $0.hidden != true }) { bot in
            NavigationLink { AgentProfileView(bot: bot) } label: {
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
