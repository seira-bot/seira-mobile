import CompanionCore
import SwiftUI

struct SearchPage: View {
    let open: (Chat) -> Void

    @EnvironmentObject private var session: Session
    @State private var query = ""
    @State private var filter = HomeSearchFilter.all
    @State private var hits: [SearchHit] = []
    @State private var routines: [Routine] = []
    @State private var searching = false
    @FocusState private var focused: Bool

    private var bots: [Bot] {
        filter.showsBots ? HomeSearch.bots(session.state.bots, query: query) : []
    }

    private var rooms: [Room] {
        filter.showsGroups ? HomeSearch.rooms(session.state.rooms, query: query) : []
    }

    private var matchedRoutines: [Routine] {
        filter.showsRoutines ? HomeSearch.routines(routines, query: query) : []
    }

    private var shownHits: [SearchHit] { filter.showsMessages ? hits : [] }

    private var isEmpty: Bool {
        bots.isEmpty && rooms.isEmpty && matchedRoutines.isEmpty && shownHits.isEmpty
    }

    var body: some View {
        VStack(spacing: 0) {
            field
            chips
            results
        }
        .background(Color.seiraBg.ignoresSafeArea())
        .navigationTitle("Search")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { focused = true }
        .task { routines = await session.loadRoutines().routines }
        .task(id: query) { await runSearch() }
    }

    private var field: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(Color.seiraDim)
            TextField("Search", text: $query)
                .focused($focused)
                .submitLabel(.search)
                .autocorrectionDisabled()
            if !query.isEmpty {
                Button { query = "" } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(Color.seiraDim)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Clear")
            }
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 48)
        .background(Color.seiraS1, in: Capsule())
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }

    private var chips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                chip(.all, "All")
                chip(.messages, "Messages")
                chip(.bots, "Bots")
                chip(.groups, "Groups")
                chip(.routines, "Routines")
            }
            .padding(.horizontal, 16)
        }
        .padding(.vertical, 10)
    }

    private func chip(_ value: HomeSearchFilter, _ title: LocalizedStringKey) -> some View {
        let selected = filter == value
        let fill: Color = selected ? .seiraInv : .seiraS1
        let ink: Color = selected ? .seiraOnInv : .seiraFg
        return Button {
            filter = value
        } label: {
            Text(title)
                .font(.seira(14, .medium, relativeTo: .subheadline))
                .foregroundStyle(ink)
                .padding(.horizontal, 16)
                .frame(minHeight: 40)
                .background(fill, in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    @ViewBuilder private var results: some View {
        if HomeSearch.normalized(query).isEmpty {
            Spacer()
        } else if isEmpty && !searching {
            EmptyStateView(
                "Nothing matches",
                systemImage: "magnifyingglass",
                description: Text("Try another word.")
            )
        } else {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    botSection
                    roomSection
                    routineSection
                    messageSection
                }
            }
        }
    }

    @ViewBuilder private var botSection: some View {
        if !bots.isEmpty {
            HomeSectionTitle(text: Text("Bots")).padding(.vertical, 8)
            ForEach(bots) { bot in
                Button { open(.bot(bot)) } label: { nameRow(bot.name, bot.title, bot: bot) }
                    .buttonStyle(HomeRowButtonStyle())
            }
        }
    }

    @ViewBuilder private var roomSection: some View {
        if !rooms.isEmpty {
            HomeSectionTitle(text: Text("Groups")).padding(.vertical, 8)
            ForEach(rooms) { room in
                Button { open(.room(room)) } label: { nameRow(room.name, "", bot: nil) }
                    .buttonStyle(HomeRowButtonStyle())
            }
        }
    }

    @ViewBuilder private var routineSection: some View {
        if !matchedRoutines.isEmpty {
            HomeSectionTitle(text: Text("Routines")).padding(.vertical, 8)
            ForEach(matchedRoutines) { routine in
                nameRow(routine.name, routine.schedule.summary, bot: session.state.bot(routine.botId))
            }
        }
    }

    @ViewBuilder private var messageSection: some View {
        if !shownHits.isEmpty {
            HomeSectionTitle(text: Text("Messages")).padding(.vertical, 8)
            ForEach(shownHits) { hit in
                Button {
                    Task {
                        if let chat = await session.open(hit) { open(chat) }
                    }
                } label: {
                    SearchHitRow(hit: hit)
                }
                .buttonStyle(HomeRowButtonStyle())
                .padding(.horizontal, HomeRowMetrics.side)
            }
        }
    }

    private func nameRow(_ title: String, _ subtitle: String, bot: Bot?) -> some View {
        HStack(spacing: 12) {
            if let bot {
                SeiraAvatar(bot: bot, size: 40)
            } else {
                Image(systemName: "person.2.fill")
                    .foregroundStyle(Color.seiraDim)
                    .frame(width: 40, height: 40)
                    .background(Color.seiraS1, in: Circle())
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: title)
                    .font(.seira(16, .medium, relativeTo: .body))
                    .foregroundStyle(Color.seiraInk)
                if !subtitle.isEmpty {
                    Text(verbatim: subtitle)
                        .font(.seira(13, relativeTo: .caption))
                        .foregroundStyle(Color.seiraInk2)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, HomeRowMetrics.side)
        .frame(minHeight: 56)
        .contentShape(Rectangle())
    }

    private func runSearch() async {
        let expected = query
        guard HomeSearch.normalized(expected).count >= 2 else {
            hits = []
            searching = false
            return
        }
        searching = true
        try? await Task.sleep(for: .milliseconds(250))
        guard !Task.isCancelled, query == expected else { return }
        let found = await session.search(expected)
        guard !Task.isCancelled, query == expected else { return }
        hits = found
        searching = false
    }
}
