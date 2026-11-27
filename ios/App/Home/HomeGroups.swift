import CompanionCore
import SwiftUI

/// A group title. A foldable one is a button with a chevron; the trailing
/// controls sit beside it and stay 48 pt wide.
struct HomeGroupHeader<Trailing: View>: View {
    let title: String
    /// nil: the group does not fold.
    let folded: Bool?
    var toggle: () -> Void = {}
    /// Bots of this group that wait for the person. Shown only while the group is folded.
    var waiting = 0
    @ViewBuilder let trailing: () -> Trailing

    var body: some View {
        HStack(spacing: 0) {
            titleBlock
            if folded == true, waiting > 0 {
                HomeWaitingBadge(count: waiting)
                    .padding(.leading, 8)
            }
            Spacer(minLength: 0)
            trailing()
        }
        .padding(.leading, HomeRowMetrics.side)
        .padding(.trailing, 6)
        .frame(minHeight: 48)
        .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
    }

    private var titleText: some View {
        Text(verbatim: title)
            .font(.seira(15, .semibold, relativeTo: .body))
            .foregroundStyle(Color.seiraInk)
            .lineLimit(1)
    }

    @ViewBuilder private var titleBlock: some View {
        if let folded {
            Button(action: toggle) {
                HStack(spacing: 8) {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Color.seiraInk3)
                        .rotationEffect(.degrees(folded ? 0 : 90))
                        .frame(width: 14)
                    titleText
                }
                .frame(minHeight: 48)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityValue(folded ? "Folded" : "Open")
            .accessibilityAddTraits(.isHeader)
            .accessibilityIdentifier("group-fold-\(title)")
        } else {
            titleText
                .accessibilityAddTraits(.isHeader)
        }
    }
}

extension HomeGroupHeader where Trailing == EmptyView {
    init(title: String, folded: Bool?, toggle: @escaping () -> Void = {}, waiting: Int = 0) {
        self.init(title: title, folded: folded, toggle: toggle, waiting: waiting) { EmptyView() }
    }
}

/// A small accent dot with the number of bots that wait for the person.
struct HomeWaitingBadge: View {
    let count: Int

    var body: some View {
        HStack(spacing: 4) {
            Circle()
                .fill(Color.seiraAccent)
                .frame(width: 8, height: 8)
            Text(verbatim: "\(count)")
                .font(.seira(12, .semibold, relativeTo: .caption))
                .monospacedDigit()
                .foregroundStyle(Color.seiraInk2)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("Waiting for you"))
        .accessibilityValue(Text("\(count) waiting"))
    }
}

struct HomeGroupIconButton: View {
    let symbol: String
    let label: Text
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(Color.seiraInk3)
                .frame(width: 48, height: 48)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/// A small coloured dot for a bot that waits or has a problem.
struct HomeDotBadge: View {
    let dot: HomeDot

    var body: some View {
        Circle()
            .fill(dot == .problem ? Color.seiraBad : Color.seiraWarning)
            .frame(width: 12, height: 12)
            .overlay(Circle().stroke(Color.seiraApp, lineWidth: 2))
            .accessibilityHidden(true)
    }
}

/// A bot as a tile in Simple mode: face, name, a crown for the chief and a
/// dot only for a wait or a problem.
struct BotTile: View, Equatable {
    let bot: Bot
    let dot: HomeDot

    private var label: String {
        var parts = [bot.name]
        if bot.chiefOfStaff == true { parts.append(String(localized: "Chief of Staff")) }
        if dot == .waiting { parts.append(String(localized: "Waiting for you")) }
        if dot == .problem { parts.append(String(localized: "Needs a look")) }
        return parts.joined(separator: ", ")
    }

    var body: some View {
        VStack(spacing: 6) {
            SeiraAvatar(bot: bot, size: 56)
                .overlay(alignment: .topTrailing) {
                    if HomeDotRules.tileShows(dot) { HomeDotBadge(dot: dot) }
                }
                .overlay(alignment: .topLeading) {
                    if bot.chiefOfStaff == true {
                        Image(systemName: "crown.fill")
                            .font(.system(size: 9))
                            .foregroundStyle(Color.seiraOnInv)
                            .padding(4)
                            .background(Color.seiraInv, in: Circle())
                            .offset(x: -4, y: -4)
                    }
                }
            Text(verbatim: bot.name)
                .font(.seira(12, .medium, relativeTo: .caption))
                .foregroundStyle(Color.seiraInk)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, minHeight: 84)
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
    }
}

/// A bot in Advanced mode: a chevron for its threads, its face with a dot,
/// name, crown, time and a one-line preview.
struct AdvancedBotRow: View, Equatable {
    let bot: Bot
    let preview: String
    let at: Double
    let today: Date
    let mood: CharacterMood
    let waiting: Bool
    let expanded: Bool
    var highlighted = false
    /// Compact density: no preview line.
    var compact = false
    var toggle: () -> Void = {}
    var open: () -> Void = {}

    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.bot == rhs.bot && lhs.preview == rhs.preview && lhs.at == rhs.at && lhs.today == rhs.today
            && lhs.mood == rhs.mood && lhs.waiting == rhs.waiting && lhs.expanded == rhs.expanded
            && lhs.highlighted == rhs.highlighted && lhs.compact == rhs.compact
    }

    private var rowHeight: CGFloat { compact ? HomeRowMetrics.compactHeight : HomeRowMetrics.minHeight }

    private var working: Bool { bot.busy == true && !waiting }

    private var detail: String {
        if !compact, !preview.isEmpty { return preview }
        return waiting ? String(localized: "Waiting on you") : ""
    }

    private var toggleLabel: Text {
        expanded ? Text("Hide \(bot.name)'s threads") : Text("Show \(bot.name)'s threads")
    }

    var body: some View {
        HStack(spacing: 0) {
            Button(action: toggle) {
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color.seiraInk3)
                    .rotationEffect(.degrees(expanded ? 90 : 0))
                    .frame(width: 44, height: max(44, rowHeight))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(toggleLabel)
            .accessibilityIdentifier("row-threads-toggle.\(bot.id)")

            Button(action: open) {
                HStack(spacing: HomeRowMetrics.gap) {
                    SeiraAvatar(
                        bot: bot,
                        size: HomeRowMetrics.avatar,
                        ring: working ? .working : .none,
                        waiting: waiting,
                        mood: mood
                    )
                    .accessibilityHidden(true)
                    HomeRowLabels(
                        title: bot.name,
                        showsCrown: bot.chiefOfStaff == true,
                        unread: bot.unread && !working,
                        stamp: RelativeStamp.list(at),
                        detail: detail,
                        detailWaiting: waiting,
                        working: working,
                        compact: compact
                    )
                }
                .padding(.trailing, HomeRowMetrics.side)
                .frame(minHeight: rowHeight)
                .contentShape(Rectangle())
            }
            .buttonStyle(HomeRowButtonStyle())
        }
        .padding(.leading, 4)
        .background {
            if highlighted {
                RoundedRectangle(cornerRadius: 14).fill(Color.seiraAccSoft)
                    .padding(.horizontal, 8)
            }
        }
    }
}

/// The search field of Advanced mode. It opens the search page.
struct HomeSearchRow: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 16, weight: .medium))
                Text("Search chats and bots")
                    .font(.seira(15, relativeTo: .body))
                Spacer(minLength: 0)
            }
            .foregroundStyle(Color.seiraInk3)
            .padding(.horizontal, 14)
            .frame(minHeight: 48)
            .background(Color.seiraS1, in: RoundedRectangle(cornerRadius: 14))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(.horizontal, HomeRowMetrics.side)
        .padding(.bottom, 4)
        .accessibilityIdentifier("home-search")
    }
}

/// A department the person acts on from its menu or its plus.
struct HomeDepartmentTarget: Identifiable, Equatable {
    let name: String
    var id: String { name }
}

/// The department's menu: add bots, or make a bot there.
struct DepartmentMenuSheet: View {
    let department: String
    let addBots: () -> Void
    let newBot: () -> Void

    var body: some View {
        NavigationStack {
            List {
                Button {
                    Haptics.selection()
                    addBots()
                } label: {
                    Label("Add bots", systemImage: "person.badge.plus")
                        .foregroundStyle(Color.seiraInk)
                        .frame(minHeight: 44)
                }
                Button {
                    Haptics.selection()
                    newBot()
                } label: {
                    Label("New bot here", systemImage: "plus.circle")
                        .foregroundStyle(Color.seiraInk)
                        .frame(minHeight: 44)
                }
            }
            .navigationTitle(Text(verbatim: department))
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.height(240)])
    }
}

/// Checks mark the bots in the department. Save sends what the person changed, once.
struct AddBotsSheet: View {
    let department: String

    @EnvironmentObject private var session: Session
    @Environment(\.dismiss) private var dismiss
    @State private var edits = DepartmentEdits()
    @State private var saving = false
    @State private var problem: SaveProblem?

    private enum SaveProblem {
        case failed, partial

        var text: String {
            switch self {
            case .failed: return String(localized: "Could not save. Try again.")
            case .partial: return String(localized: "Some changes did not save. Try again.")
            }
        }
    }

    private var bots: [Bot] { DepartmentRules.selectable(session.state) }

    private var members: Set<String> { DepartmentRules.members(bots, department) }

    private var placement: DepartmentPlacement {
        edits.placement(bots, members: members)
    }

    var body: some View {
        NavigationStack {
            Group {
                if bots.isEmpty {
                    Text("No bots to add.")
                        .foregroundStyle(Color.seiraInk3)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List {
                        ForEach(bots) { bot in row(bot) }
                    }
                    .disabled(saving)
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if let problem {
                    Text(verbatim: problem.text)
                        .font(.seira(14, relativeTo: .subheadline))
                        .foregroundStyle(Color.seiraDangerText)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, HomeRowMetrics.side)
                        .padding(.vertical, 12)
                        .background(Color.seiraS1)
                        .accessibilityIdentifier("add-bots-error")
                }
            }
            .navigationTitle(Text("Add bots to \(department)"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.disabled(saving)
                }
                ToolbarItem(placement: .confirmationAction) {
                    if saving {
                        HStack(spacing: 6) {
                            ProgressView().controlSize(.small)
                            Text("Saving")
                        }
                    } else {
                        Button("Save") { save() }
                            .disabled(!DepartmentRules.canSave(placement, saving: saving))
                    }
                }
            }
        }
        .interactiveDismissDisabled(saving)
    }

    private func row(_ bot: Bot) -> some View {
        let isOn = edits.isChecked(bot.id, members: members)
        let section = bot.section?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let place: String = {
            if section.isEmpty { return String(localized: "Bots") }
            return section == department ? "" : String(localized: "In \(section)")
        }()
        return Button {
            Haptics.selection()
            edits.toggle(bot.id, members: members)
            problem = nil
        } label: {
            HStack(spacing: 12) {
                SeiraAvatar(bot: bot, size: 36)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: bot.name)
                        .font(.seira(15, .medium, relativeTo: .body))
                        .foregroundStyle(Color.seiraInk)
                    if !place.isEmpty {
                        Text(verbatim: place)
                            .font(.seira(12, relativeTo: .caption))
                            .foregroundStyle(Color.seiraInk3)
                    }
                }
                Spacer(minLength: 0)
                Image(systemName: isOn ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 22))
                    .foregroundStyle(isOn ? Color.seiraAccent : Color.seiraInk3)
            }
            .frame(minHeight: 48)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }

    private func save() {
        guard DepartmentRules.canSave(placement, saving: saving) else { return }
        let calls = placement.calls(department)
        saving = true
        problem = nil
        Task {
            var saved = 0
            var ok = true
            for call in calls {
                if await session.assignSection(name: call.name, botIds: call.botIds, reportsError: false) == nil {
                    ok = false
                    break
                }
                saved += 1
            }
            if !ok, saved > 0 { await session.refresh() }
            saving = false
            if ok {
                Haptics.success()
                dismiss()
            } else {
                let next: SaveProblem = saved > 0 ? .partial : .failed
                problem = next
                Haptics.notification(.error)
                UIAccessibility.post(notification: .announcement, argument: next.text)
            }
        }
    }
}

/// Asks for a name and makes an empty department.
struct AddDepartmentSheet: View {
    @EnvironmentObject private var session: Session
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var adding = false
    @State private var failed = false
    @FocusState private var focused: Bool

    private var problem: DepartmentRules.NameProblem? {
        DepartmentRules.nameProblem(name, session.state.sidebarSections.map(\.name))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Department name", text: $name, prompt: Text("Marketing"))
                        .focused($focused)
                        .autocorrectionDisabled()
                        .onValueChange(of: name) { value in
                            let clamped = DepartmentRules.clampName(value)
                            if clamped != value { name = clamped }
                            failed = false
                        }
                } footer: {
                    if problem == .exists {
                        Text("That name is already used.").foregroundStyle(Color.seiraDangerText)
                    } else if failed {
                        Text("Could not add the department. Try again.").foregroundStyle(Color.seiraDangerText)
                    }
                }
            }
            .navigationTitle("Add department")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.disabled(adding)
                }
                ToolbarItem(placement: .confirmationAction) {
                    if adding {
                        HStack(spacing: 6) {
                            ProgressView().controlSize(.small)
                            Text("Adding")
                        }
                    } else {
                        Button("Add") { add() }.disabled(problem != nil)
                    }
                }
            }
            .onAppear { focused = true }
        }
        .presentationDetents([.medium])
        .interactiveDismissDisabled(adding)
    }

    private func add() {
        guard problem == nil, !adding else { return }
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        adding = true
        failed = false
        Task {
            let result = await session.assignSection(name: trimmed, botIds: [], reportsError: false)
            adding = false
            if result != nil {
                Haptics.success()
                dismiss()
            } else {
                failed = true
                Haptics.notification(.error)
            }
        }
    }
}

struct HomeLiveCallDisclosure: ViewModifier {
    @Binding var bot: Bot?
    let start: (Bot) -> Void

    func body(content: Content) -> some View {
        content.alert(
            Text("A Live call sends your voice to OpenAI, along with the chat's recent messages, the bot's answers and the details of any approval it asks for. The OpenAI key stays on your computer."),
            isPresented: Binding(
                get: { bot != nil },
                set: { if !$0 { bot = nil } }
            ),
            presenting: bot
        ) { target in
            Button("Cancel", role: .cancel) {}
            Button("Start call") {
                LiveCallDisclosure().accept()
                start(target)
            }
        }
    }
}
