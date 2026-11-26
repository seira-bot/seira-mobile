// Settings stays status-first. Network details and destructive pairing
// controls live one level deeper so the everyday screen remains calm.
import SwiftUI
import CompanionCore
import UIKit

struct SettingsView: View {
    @EnvironmentObject private var session: Session
    @State private var enablingNotifications = false
    @State private var confirmingSignOut = false
    @State private var usageReport: EngineLimitsReport?
    @AppStorage(PrefKey.activityDetail) private var activityDetail = ActivityDetail.phoneDefault.rawValue
    @AppStorage(PrefKey.islandIntro) private var islandIntro = IslandIntro.oncePerBot.rawValue
    @AppStorage(PrefKey.language) private var language = AppLanguage.system.rawValue
    @AppStorage(PrefKey.rosterDensity) private var rosterDensity = RosterDensity.default.rawValue
    @AppStorage(PrefKey.notifyApprovals) private var notifyApprovals = true
    @AppStorage(PrefKey.notifyUpdates) private var notifyUpdates = true
    @AppStorage(PrefKey.notifyRoutines) private var notifyRoutines = true
    @AppStorage(PrefKey.liveCaptions) private var liveCaptions = true
    private let onConnect: (() -> Void)?

    init(onConnect: (() -> Void)? = nil) {
        self.onConnect = onConnect
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                title
                computerSection
                appearanceSection
                notificationsSection
                chatSection
                Group {
                    languageSection
                    workspaceSection
                    privacySection
                    helpSection
                    signOutButton
                    SettingsFooter()
                }
            }
            .padding(.horizontal, 16)
        }
        .background(Color.seiraApp.ignoresSafeArea())
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) { SettingsBackButton() }
        }
        .toolbarBackground(Color.seiraApp, for: .navigationBar)
        .background(SwipeBackBridge())
        .task { await session.refreshNotificationAuthorization() }
        .task { await loadUsageSupport() }
        .confirmationDialog(
            "Remove this connection?",
            isPresented: $confirmingSignOut,
            titleVisibility: .visible
        ) {
            Button("Remove from this device", role: .destructive) {
                session.signOut()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes the connection from this device only. It does not revoke this device on your Mac. To remove Mac-side access, open Seira → Settings → Phone & devices and remove it there.")
        }
    }

    private var title: some View {
        Text("Settings")
            .font(.seira(28, .semibold, relativeTo: .largeTitle))
            .tracking(-0.56)
            .foregroundStyle(Color.seiraInk)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 14)
            .padding(.horizontal, 4)
            .padding(.bottom, 20)
            .accessibilityAddTraits(.isHeader)
    }

    private var appearanceSection: some View {
        VStack(spacing: 0) {
            SettingsSectionHeader(title: "Appearance")
            AppearanceSegmentedControl()
                .padding(6)
                .seiraCard(radius: 22)
            SettingsCaption(text: Text("Auto follows your phone setting."))
        }
    }

    private var computerSection: some View {
        VStack(spacing: 0) {
            SettingsSectionHeader(title: "Your computer")
            SettingsCard { computerRows }
        }
    }

    @ViewBuilder private var computerRows: some View {
        if let connection = session.connection {
            NavigationLink {
                ConnectedComputersView()
            } label: {
                computerLabel(
                    name: Text(verbatim: connection.name),
                    status: computerStatusText,
                    connected: session.status == .live
                )
            }
            .buttonStyle(SettingsRowButtonStyle())
            SettingsDivider()
            Button {
                Haptics.selection()
                session.beginPairing()
            } label: {
                SettingsLinkLabel(symbol: "qrcode", title: "Pair another computer")
            }
            .buttonStyle(SettingsRowButtonStyle())
        } else {
            Button {
                onConnect?()
            } label: {
                computerLabel(
                    name: Text("Connect a computer"),
                    status: Text("Not connected"),
                    connected: false
                )
            }
            .buttonStyle(SettingsRowButtonStyle())
            .disabled(onConnect == nil)
        }
    }

    private func computerLabel(name: Text, status: Text, connected: Bool) -> some View {
        HStack(spacing: 8) {
            ComputerSettingsRow(name: name, status: status, connected: connected)
            SettingsChevron()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .frame(minHeight: 64)
        .contentShape(Rectangle())
    }

    private var notificationsSection: some View {
        VStack(spacing: 0) {
            SettingsSectionHeader(title: "Notifications")
            SettingsCard {
                notificationStatusRow
                SettingsDivider()
                notificationToggles
            }
            notificationCaptions
        }
    }

    private var notificationToggles: some View {
        VStack(spacing: 0) {
            SettingsToggleRow(
                symbol: "checkmark.circle",
                title: "Needs your OK",
                subtitle: "When a bot waits for your answer",
                isOn: $notifyApprovals
            )
            SettingsDivider()
            SettingsToggleRow(
                symbol: "bell",
                title: "Task finished",
                subtitle: "When a bot is done with a job",
                isOn: $notifyUpdates
            )
            SettingsDivider()
            SettingsToggleRow(
                symbol: "exclamationmark.arrow.circlepath",
                title: "Routine problems",
                subtitle: "When a scheduled job fails",
                isOn: $notifyRoutines
            )
        }
        .disabled(!notificationsAreEnabled)
        .opacity(notificationsAreEnabled ? 1 : 0.5)
    }

    @ViewBuilder private var notificationCaptions: some View {
        if !notificationsAreEnabled {
            SettingsCaption(text: Text("Turn on notifications to choose which alerts you get."))
        }
        SettingsCaption(text: Text("Seira alerts you while the app is open or was used a short time ago."))
    }

    @ViewBuilder private var notificationStatusRow: some View {
        if notificationsAreEnabled {
            notificationRow
                .accessibilityHint(notificationAccessibilityHint)
        } else {
            Button {
                enablingNotifications = true
                Task {
                    await session.enableNotifications()
                    enablingNotifications = false
                }
            } label: {
                notificationRow
            }
            .buttonStyle(SettingsRowButtonStyle())
            .disabled(enablingNotifications)
            .accessibilityHint(notificationAccessibilityHint)
        }
    }

    private var chatSection: some View {
        VStack(spacing: 0) {
            SettingsSectionHeader(title: "Chat")
            SettingsCard {
                activityRow
                SettingsDivider()
                introRow
                SettingsDivider()
                NavigationLink {
                    QuickRepliesEditor()
                } label: {
                    SettingsLinkLabel(symbol: "bolt", title: "Quick replies")
                }
                .buttonStyle(SettingsRowButtonStyle())
                SettingsDivider()
                densityRow
                if session.connection != nil {
                    SettingsDivider()
                    SettingsToggleRow(
                        symbol: "captions.bubble",
                        title: "Live captions",
                        subtitle: "Show the words during a call",
                        isOn: $liveCaptions
                    )
                }
            }
            SettingsCaption(text: Text(LocalizedStringKey(currentActivity.caption)))
        }
    }

    private var activityRow: some View {
        SettingsMenuRow(
            symbol: "wrench.and.screwdriver",
            title: "Activity",
            value: Text(LocalizedStringKey(currentActivity.label))
        ) {
            Picker("Activity", selection: $activityDetail) {
                ForEach(ActivityDetail.allCases, id: \.rawValue) { level in
                    Text(LocalizedStringKey(level.label)).tag(level.rawValue)
                }
            }
        }
    }

    private var introRow: some View {
        SettingsMenuRow(
            symbol: "sparkles",
            title: "Bot intro animation",
            value: Text(LocalizedStringKey(currentIntro.label))
        ) {
            Picker("Bot intro animation", selection: $islandIntro) {
                ForEach(IslandIntro.allCases, id: \.rawValue) { option in
                    Text(LocalizedStringKey(option.label)).tag(option.rawValue)
                }
            }
        }
    }

    private var densityRow: some View {
        SettingsMenuRow(
            symbol: "list.bullet",
            title: "List density",
            value: Text(LocalizedStringKey(currentDensity.label))
        ) {
            Picker("List density", selection: densityBinding) {
                ForEach(RosterDensity.allCases, id: \.self) { density in
                    Text(LocalizedStringKey(density.label)).tag(density)
                }
            }
        }
        .accessibilityIdentifier("list-density")
    }

    private var languageSection: some View {
        VStack(spacing: 0) {
            SettingsSectionHeader(title: "Language")
            SettingsCard {
                SettingsMenuRow(
                    symbol: "globe",
                    title: "Language",
                    value: Text(currentLanguage.label)
                ) {
                    Picker("Language", selection: $language) {
                        ForEach(AppLanguage.allCases) { option in
                            Text(option.label).tag(option.rawValue)
                        }
                    }
                }
            }
            SettingsCaption(text: Text("Changes the language inside Seira. Buttons drawn by iOS itself follow the phone's language, which you can set for this app in iOS Settings."))
        }
    }

    @ViewBuilder private var workspaceSection: some View {
        if session.connection != nil {
            VStack(spacing: 0) {
                SettingsSectionHeader(title: "Workspace")
                SettingsCard {
                    NavigationLink {
                        TasksRoutinesView()
                    } label: {
                        SettingsLinkLabel(symbol: "calendar.badge.clock", title: "Threads & Routines")
                    }
                    .buttonStyle(SettingsRowButtonStyle())

                    if session.canAdminister, let usageReport {
                        SettingsDivider()
                        NavigationLink {
                            UsageView(initial: usageReport)
                        } label: {
                            SettingsLinkLabel(symbol: "chart.bar", title: "AI usage")
                        }
                        .buttonStyle(SettingsRowButtonStyle())
                    }
                }
            }
        }
    }

    /// Connecting apps needs the admin scope; a chat-only server session
    /// leaves it to the owner, in the server's own interface.
    @ViewBuilder private var privacySection: some View {
        if session.connection != nil, session.canAdminister {
            VStack(spacing: 0) {
                SettingsSectionHeader(title: "Privacy")
                SettingsCard {
                    NavigationLink {
                        ConnectedAppsView()
                    } label: {
                        SettingsLinkLabel(symbol: "link", title: "Connected apps")
                    }
                    .buttonStyle(SettingsRowButtonStyle())
                }
            }
        }
    }

    private var helpSection: some View {
        VStack(spacing: 0) {
            SettingsSectionHeader(title: "Help")
            SettingsCard {
                helpLink(symbol: "questionmark.circle", title: "Questions and answers", url: "https://seira.bot/#faq")
                SettingsDivider()
                helpLink(symbol: "doc.text", title: "Report a problem", url: "https://github.com/seira-bot/seira/issues")
                SettingsDivider()
                helpLink(symbol: "hand.raised", title: "Privacy policy", url: "https://seira.bot/privacy")
            }
        }
    }

    private func helpLink(symbol: String, title: LocalizedStringKey, url: String) -> some View {
        Button {
            if let target = URL(string: url) { UIApplication.shared.open(target) }
        } label: {
            SettingsLinkLabel(symbol: symbol, title: title)
        }
        .buttonStyle(SettingsRowButtonStyle())
    }

    private func loadUsageSupport() async {
        guard session.canAdminister, session.connection != nil else {
            usageReport = nil
            return
        }
        if case let .report(loaded) = await session.loadEngineLimits() {
            usageReport = loaded
        } else {
            usageReport = nil
        }
    }

    @ViewBuilder private var signOutButton: some View {
        if session.connection != nil {
            Button {
                confirmingSignOut = true
            } label: {
                Text("Sign out")
                    .font(.seira(16, .medium))
                    .foregroundStyle(Color.seiraDangerText)
                    .frame(maxWidth: .infinity, minHeight: 56)
                    .contentShape(Rectangle())
            }
            .buttonStyle(SettingsRowButtonStyle())
            .seiraCard(radius: 22)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .padding(.top, 28)
        }
    }

    private var currentActivity: ActivityDetail {
        ActivityDetail(rawValue: activityDetail) ?? .phoneDefault
    }

    private var currentIntro: IslandIntro {
        IslandIntro(rawValue: islandIntro) ?? .oncePerBot
    }

    private var currentDensity: RosterDensity { RosterDensity(stored: rosterDensity) }

    private var currentLanguage: AppLanguage {
        AppLanguage(rawValue: language) ?? .system
    }

    // Bound through the resolved value, so a stored value this build cannot
    // read still shows the density actually in use.
    private var densityBinding: Binding<RosterDensity> {
        Binding(
            get: { RosterDensity(stored: rosterDensity) },
            set: { rosterDensity = $0.rawValue }
        )
    }

    private var notificationsAreEnabled: Bool {
        switch session.notificationAuthorization {
        case .authorized, .provisional, .ephemeral: return true
        default: return false
        }
    }

    private var notificationAccessibilityHint: LocalizedStringKey {
        if notificationsAreEnabled { return "Notifications are enabled" }
        if session.notificationAuthorization == .denied { return "Opens device Settings" }
        return "Asks for permission to send notifications"
    }

    private var notificationRow: some View {
        HStack(spacing: 14) {
            SettingsRowIcon(symbol: "bell.badge")
            Text("Notifications")
                .font(.seira(16, .medium))
                .foregroundStyle(Color.seiraInk)
                .frame(maxWidth: .infinity, alignment: .leading)
            notificationTrailing
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 56)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder private var notificationTrailing: some View {
        if enablingNotifications {
            ProgressView()
                .controlSize(.small)
        } else {
            Text(LocalizedStringKey(session.notificationStatusText))
                .font(.seira(15))
                .foregroundStyle(Color.seiraInk2)
        }
    }

    private var statusText: Text { session.status.settingsText }

    private var computerStatusText: Text {
        guard session.connections.count > 1 else { return statusText }
        return statusText + Text(verbatim: " · ") + Text("\(session.connections.count) saved")
    }
}

private struct ComputerSettingsRow: View {
    let name: Text
    let status: Text
    let connected: Bool

    private var dotColor: Color { connected ? .seiraSuccess : .seiraInk3 }

    var body: some View {
        HStack(spacing: 14) {
            SettingsRowIcon(symbol: "laptopcomputer")
            VStack(alignment: .leading, spacing: 2) {
                name
                    .font(.seira(16, .medium))
                    .foregroundStyle(Color.seiraInk)
                    .lineLimit(1)
                HStack(spacing: 6) {
                    StatusDot(color: dotColor)
                    status
                        .font(.seira(13, .regular, relativeTo: .subheadline))
                        .foregroundStyle(Color.seiraInk2)
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .accessibilityElement(children: .combine)
    }
}

struct ConnectedComputersView: View {
    @EnvironmentObject private var session: Session
    @State private var pendingRemoval: Connection?

    /// The dialog interpolates the computer's own name. With no name there is
    /// copy to fall back to, rather than an English word inside a translated
    /// sentence.
    private var removalTitle: LocalizedStringKey {
        guard let name = pendingRemoval?.name else { return "Remove this computer?" }
        return "Remove \(name)?"
    }

    private var otherComputers: [Connection] {
        session.connections.filter { $0.id != session.connection?.id }
    }

    var body: some View {
        List {
            if let active = session.connection {
                Section("Current computer") {
                    NavigationLink {
                        ConnectionSecurityView()
                    } label: {
                        ComputerSettingsRow(
                            name: Text(verbatim: active.name),
                            status: session.status.settingsText,
                            connected: session.status == .live
                        )
                    }
                }
                .listRowBackground(Color.seiraTint)
            }

            if !otherComputers.isEmpty {
                Section("Other computers") {
                    ForEach(otherComputers) { computer in
                        Button {
                            Haptics.selection()
                            session.switchComputer(to: computer.id)
                        } label: {
                            HStack(spacing: 12) {
                                ProfileAvatar(name: computer.name, size: 38)
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(computer.name)
                                        .font(.seira(16, .medium))
                                        .foregroundStyle(Color.seiraInk)
                                        .lineLimit(1)
                                    Text("Tap to switch")
                                        .font(.seira(13, .regular, relativeTo: .subheadline))
                                        .foregroundStyle(Color.seiraInk2)
                                }
                                Spacer()
                                Text("Use")
                                    .font(.seira(15, .semibold, relativeTo: .subheadline))
                                    .foregroundStyle(Color.seiraInk)
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .swipeActions {
                            Button("Remove", role: .destructive) {
                                pendingRemoval = computer
                            }
                        }
                        .accessibilityHint("Switches Seira to this computer")
                    }
                }
                .listRowBackground(Color.seiraTint)
            }

            Section {
                Button {
                    Haptics.selection()
                    session.beginPairing()
                } label: {
                    Label("Connect another computer", systemImage: "plus.circle.fill")
                }
            } footer: {
                Text("Each computer is paired separately. Only the selected computer is active at a time.")
            }
            .listRowBackground(Color.seiraTint)
        }
        .settingsListChrome()
        .navigationTitle("Computers")
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog(
            removalTitle,
            isPresented: Binding(
                get: { pendingRemoval != nil },
                set: { if !$0 { pendingRemoval = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Remove from this device", role: .destructive) {
                guard let pendingRemoval else { return }
                session.forgetConnection(id: pendingRemoval.id)
                self.pendingRemoval = nil
            }
            Button("Cancel", role: .cancel) { pendingRemoval = nil }
        } message: {
            Text("This removes the saved connection from this device only.")
        }
    }
}

struct ConnectionSecurityView: View {
    @EnvironmentObject private var session: Session
    @Environment(\.dismiss) private var dismiss
    @State private var confirmingSignOut = false
    @State private var editingAddress = false
    @State private var addressText = ""
    @State private var showingFullAddress = false
    @State private var copiedAddress = false
    @State private var refreshing = false

    var body: some View {
        Form {
            if let connection = session.connection {
                Section {
                    HStack(spacing: 14) {
                        ProfileAvatar(name: connection.name, size: 46)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(connection.name)
                                .font(.seira(17, .semibold, relativeTo: .headline))
                                .foregroundStyle(Color.seiraInk)
                            Label {
                                session.status.settingsText
                            } icon: {
                                Image(systemName: session.status == .live ? "checkmark.circle.fill" : "circle.dotted")
                            }
                                .font(.seira(14, .regular, relativeTo: .subheadline))
                                .foregroundStyle(statusTint)
                        }
                    }
                    .padding(.vertical, 4)
                    .accessibilityElement(children: .combine)
                }
                .listRowBackground(Color.seiraTint)

                Section {
                    DisclosureGroup("Connection details") {
                        VStack(alignment: .leading, spacing: 12) {
                            Group {
                                if showingFullAddress {
                                    Text(connection.displayAddress)
                                        .textSelection(.enabled)
                                } else {
                                    Text(shortened(connection.displayAddress))
                                        .lineLimit(1)
                                        .truncationMode(.middle)
                                }
                            }
                            .font(.footnote.monospaced())
                            .foregroundStyle(Color.seiraInk2)

                            HStack(spacing: 16) {
                                Button(showingFullAddress ? "Hide full address" : "Show full address") {
                                    showingFullAddress.toggle()
                                }
                                Button(copiedAddress ? "Copied" : "Copy") {
                                    UIPasteboard.general.string = connection.displayAddress
                                    copiedAddress = true
                                    Task {
                                        try? await Task.sleep(for: .seconds(2))
                                        copiedAddress = false
                                    }
                                }
                            }
                            .font(.subheadline.weight(.medium))
                        }
                        .padding(.top, 10)
                    }

                    Button("Edit address") {
                        addressText = connection.displayAddress
                        editingAddress = true
                    }
                }
                .listRowBackground(Color.seiraTint)

                Section("Troubleshooting") {
                    troubleshootingText
                        .font(.seira(14, .regular, relativeTo: .subheadline))
                        .foregroundStyle(Color.seiraInk2)

                    Button {
                        refreshing = true
                        Task {
                            await session.refresh()
                            refreshing = false
                        }
                    } label: {
                        HStack {
                            Text("Try reconnecting")
                            if refreshing {
                                Spacer()
                                ProgressView().controlSize(.small)
                            }
                        }
                    }
                    .disabled(refreshing)
                }
                .listRowBackground(Color.seiraTint)

                Section {
                    Button("Remove connection from this device", role: .destructive) {
                        confirmingSignOut = true
                    }
                }
                .listRowBackground(Color.seiraTint)
            } else {
                SeiraEmptyState(icon: "Icons3D/not-connected", title: "No computer connected")
            }
        }
        .settingsListChrome()
        .navigationTitle("Connection & Security")
        .navigationBarTitleDisplayMode(.inline)
        .alert("Edit address", isPresented: $editingAddress) {
            TextField("Computer address", text: $addressText)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            Button("Save") {
                if !session.updateAddress(addressText) {
                    session.actionError = String(localized: "That address doesn't look right. Copy it from Phone settings and try again.")
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Use the address shown in Phone settings on your computer. Your pairing is kept.")
        }
        .confirmationDialog(
            "Remove this connection?",
            isPresented: $confirmingSignOut,
            titleVisibility: .visible
        ) {
            Button("Remove from this device", role: .destructive) {
                session.signOut()
                dismiss()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes the connection from this device only. It does not revoke this device on your Mac. To remove Mac-side access, open Seira → Settings → Phone & devices and remove it there.")
        }
    }

    private var statusTint: Color { session.status == .live ? .seiraSuccess : .seiraInk2 }

    /// Our copy, except for `.offline`, whose text the computer itself sent and
    /// which is shown exactly as it arrived.
    private var troubleshootingText: Text {
        switch session.status {
        case .live:
            return Text("This computer is connected and responding normally.")
        case .connecting:
            return Text("Seira is trying the saved connection automatically.")
        case let .offline(reason):
            return Text(verbatim: reason)
        case .unauthorized:
            return Text("This device was removed from the computer. Pair it again to reconnect.")
        case .unpaired:
            return Text("This device is not paired with a computer.")
        }
    }

    private func shortened(_ address: String) -> String {
        guard address.count > 14 else { return address }
        let leadingCount = min(20, max(8, address.count - 8))
        return "\(address.prefix(leadingCount))…\(address.suffix(6))"
    }
}

private extension Session.Status {
    var settingsText: Text {
        switch self {
        case .live: return Text("Connected")
        case .connecting: return Text("Connecting…")
        case .unpaired: return Text("Not paired")
        case .unauthorized: return Text("Needs pairing")
        case .offline: return Text("Offline")
        }
    }
}
