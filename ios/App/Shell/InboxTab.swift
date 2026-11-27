import CompanionCore
import SwiftUI

struct InboxTab: View {
    let open: (Chat) -> Void

    @EnvironmentObject private var session: Session
    @AppStorage(PrefKey.activityDetail) private var activityDetail = ActivityDetail.phoneDefault.rawValue
    @State private var runs: [RoutineRun] = []

    private var updates: [ChatUpdate] {
        session.state.updates(detail: ActivityDetail(rawValue: activityDetail) ?? .phoneDefault)
    }

    var body: some View {
        let all = updates
        let decisions = all.filter { $0.kind == .needsYou }
        let reviews = all.filter { $0.kind == .toReview }
        let finished = InboxLogic.finishedRuns(runs)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                Text("Inbox")
                    .font(.seira(28, .semibold, relativeTo: .largeTitle))
                    .foregroundStyle(Color.seiraInk)
                    .padding(.horizontal, HomeRowMetrics.side)
                    .accessibilityAddTraits(.isHeader)
                if decisions.isEmpty {
                    quietCard
                } else {
                    ForEach(decisions) { DecisionCard(update: $0, open: open) }
                }
                news(reviews: reviews, finished: finished)
            }
            .padding(.vertical, 12)
        }
        .refreshable { await reload() }
        .task { await reload() }
    }

    private var quietCard: some View {
        Text("All quiet. Nothing needs you.")
            .font(.seira(15, relativeTo: .body))
            .foregroundStyle(Color.seiraInk2)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .seiraCard(radius: 20)
            .padding(.horizontal, 16)
    }

    @ViewBuilder
    private func news(reviews: [ChatUpdate], finished: [RoutineRun]) -> some View {
        HomeSectionTitle(text: Text("News")).padding(.top, 8)
        if reviews.isEmpty && finished.isEmpty {
            Text("Finished and failed work shows up here.")
                .font(.seira(14, relativeTo: .subheadline))
                .foregroundStyle(Color.seiraInk3)
                .padding(.horizontal, HomeRowMetrics.side)
        }
        ForEach(reviews) { update in
            Button { open(update.chat) } label: {
                newsRow(title: update.chat.name, line: update.line, symbol: "checkmark.circle.fill", tint: .seiraOk)
            }
            .buttonStyle(HomeRowButtonStyle())
        }
        ForEach(finished) { run in
            Button { openRun(run) } label: {
                newsRow(
                    title: run.routineName,
                    line: run.error ?? run.output ?? "",
                    symbol: run.status.routineStatusSymbol,
                    tint: run.status.routineStatusTint
                )
            }
            .buttonStyle(HomeRowButtonStyle())
        }
    }

    private func newsRow(title: String, line: String, symbol: String, tint: Color) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: symbol).foregroundStyle(tint).frame(width: 28, height: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: title)
                    .font(.seira(16, .medium, relativeTo: .body))
                    .foregroundStyle(Color.seiraInk)
                if !line.isEmpty {
                    Text(verbatim: line)
                        .font(.seira(13, relativeTo: .caption))
                        .foregroundStyle(Color.seiraInk2)
                        .lineLimit(2)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, HomeRowMetrics.side)
        .padding(.vertical, 8)
        .frame(minHeight: 56)
        .contentShape(Rectangle())
    }

    private func openRun(_ run: RoutineRun) {
        guard let threadId = run.threadId,
              let target = NotificationTarget(botId: run.botId, threadId: threadId) else { return }
        Task { await session.openNotification(target) }
    }

    private func reload() async {
        runs = await session.loadRoutines().runs
    }
}

private struct DecisionCard: View {
    let update: ChatUpdate
    let open: (Chat) -> Void

    @EnvironmentObject private var session: Session
    @State private var answering = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                ChatAvatarView(
                    chat: update.chat, size: 40,
                    state: CharacterMood.forChat(update.chat, in: session.state),
                    members: session.state.members(of: update.chat)
                )
                VStack(alignment: .leading, spacing: 3) {
                    Text(verbatim: update.chat.name)
                        .font(.seira(16, .semibold, relativeTo: .body))
                        .foregroundStyle(Color.seiraInk)
                    Text(verbatim: update.line.isEmpty ? " " : update.line)
                        .font(.seira(14, relativeTo: .subheadline))
                        .foregroundStyle(Color.seiraInk2)
                        .lineLimit(4)
                }
                Spacer(minLength: 0)
            }
            actions
        }
        .padding(16)
        .seiraCard(radius: 20)
        .padding(.horizontal, 16)
    }

    @ViewBuilder private var actions: some View {
        switch InboxLogic.action(for: update.card) {
        case let .options(labels, primary):
            VStack(alignment: .leading, spacing: 8) {
                ForEach(labels, id: \.self) { label in
                    pill(Text(verbatim: label), primary: label == primary) { answer(label) }
                }
                pill(Text("Open in chat"), primary: false) { open(update.chat) }
            }
        case .answerInChat:
            pill(Text("Answer in chat"), primary: true) { open(update.chat) }
        }
    }

    private func pill(_ label: Text, primary: Bool, action: @escaping () -> Void) -> some View {
        let fill: Color = primary ? .seiraAcc : .seiraS2
        let ink: Color = primary ? .seiraOnAcc : .seiraFg
        return Button(action: action) {
            label
                .font(.seira(14, .semibold, relativeTo: .subheadline))
                .foregroundStyle(ink)
                .padding(.horizontal, 14)
                .frame(minHeight: 44)
                .background(fill, in: Capsule())
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .disabled(answering)
    }

    private func answer(_ choice: String) {
        guard let card = update.card, !answering else { return }
        Haptics.selection()
        answering = true
        Task {
            await session.answer(chat: update.chat, card: card, choice: choice, rememberingPermission: false)
            answering = false
        }
    }
}
