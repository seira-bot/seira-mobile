// One Live Activity for Glance: the bot that needs you most, then the one
// that failed, then the one that has worked longest. The model in
// CompanionCore picks the focus; this file keeps the system activity equal
// to it. The stream is foreground-only and there is no push path, so the
// activity is exact while the app is alive.
import ActivityKit
import Combine
import CompanionCore
import Foundation

/// ActivityKit's content API is iOS 16.2, so the coordinator is never built on anything older.
@available(iOS 16.2, *)
@MainActor
final class GlanceCoordinator {
    private typealias GlanceActivity = Activity<GlanceActivityAttributes>

    /// A bot's streamed line changes on every window; one update per this many seconds is enough.
    private static let lineInterval: TimeInterval = 5

    private var cancellable: AnyCancellable?
    private weak var session: Session?
    private let authorization = ActivityAuthorizationInfo()
    private var activitiesEnabled = false
    private var enablementTask: Task<Void, Never>?
    private var memory = GlanceMemory()
    private var couldNotSend = Set<String>()
    private var sent: (content: GlanceContent, at: Date)?
    private var due: Date?
    private var dueTask: Task<Void, Never>?

    func attach(to session: Session) {
        self.session = session
        activitiesEnabled = authorization.areActivitiesEnabled
        enablementTask?.cancel()
        enablementTask = Task { [weak self, authorization] in
            for await enabled in authorization.activityEnablementUpdates {
                self?.activitiesEnabled = enabled
            }
        }
        endPerBotActivities()
        GlanceAnswer.handler = { [weak self] botId, threadId, requestId, allow in
            await self?.answer(botId: botId, threadId: threadId, requestId: requestId, allow: allow)
        }
        // A fixed window, so a bot that streams all the time cannot hold the first update back.
        cancellable = session.$state
            .collect(.byTime(DispatchQueue.main, .milliseconds(400)))
            .compactMap(\.last)
            .sink { [weak self] state in self?.sync(state) }
    }

    private func endPerBotActivities() {
        for activity in Activity<BotActivityAttributes>.activities {
            Task { await activity.end(nil, dismissalPolicy: .immediate) }
        }
    }

    private func sync(_ state: CompanionState) {
        guard activitiesEnabled else { return }
        let now = Date()
        let (next, view) = Glance.step(memory, bots: Glance.bots(state), now: Int64(now.timeIntervalSince1970 * 1_000))
        memory = next
        var wake = view.nextAt.map { Date(timeIntervalSince1970: TimeInterval($0) / 1_000) }

        let running = GlanceActivity.activities
        for extra in running.dropFirst() {
            Task { await extra.end(nil, dismissalPolicy: .immediate) }
        }
        guard let focus = view.focus else {
            sent = nil
            couldNotSend.removeAll()
            if let activity = running.first { Task { await activity.end(nil, dismissalPolicy: .immediate) } }
            schedule(at: wake)
            return
        }

        let requestId = focus.bot.ask?.requestId
        couldNotSend = couldNotSend.filter { $0 == requestId }
        let content = GlanceContent(entry: focus, couldNotSend: requestId.map { couldNotSend.contains($0) } ?? false)
        if let last = sent, last.content == content { schedule(at: wake); return }
        if let last = sent, running.first != nil, isLineOnly(content, after: last.content) {
            let ready = last.at.addingTimeInterval(Self.lineInterval)
            if ready > now {
                wake = min(wake ?? ready, ready)
                schedule(at: wake)
                return
            }
        }
        let alert = isNewAsk(content, after: sent?.content)
        sent = (content, now)
        send(content, alert: alert, to: running.first)
        schedule(at: wake)
    }

    private func isLineOnly(_ content: GlanceContent, after previous: GlanceContent) -> Bool {
        guard content.state == .working, previous.state == .working, content.line != previous.line else { return false }
        var same = content
        same.line = previous.line
        return same == previous
    }

    private func isNewAsk(_ content: GlanceContent, after previous: GlanceContent?) -> Bool {
        guard content.requestId != nil else { return false }
        return previous?.requestId != content.requestId || previous?.threadId != content.threadId
    }

    private func send(_ content: GlanceContent, alert: Bool, to existing: GlanceActivity?) {
        var alertConfiguration: AlertConfiguration?
        if alert {
            let body: LocalizedStringResource = content.state == .question ? "Has a question" : "Needs you"
            alertConfiguration = AlertConfiguration(
                title: LocalizedStringResource(stringLiteral: content.name), body: body, sound: .default
            )
        }
        if let existing {
            Task { await existing.update(.init(state: content, staleDate: nil), alertConfiguration: alertConfiguration) }
            return
        }
        guard let created = try? GlanceActivity.request(
            attributes: GlanceActivityAttributes(), content: .init(state: content, staleDate: nil), pushType: nil
        ) else { return }
        if let alertConfiguration {
            Task { await created.update(.init(state: content, staleDate: nil), alertConfiguration: alertConfiguration) }
        }
    }

    /// One more step when a finished bot is due to leave, or a held line is due to show.
    private func schedule(at date: Date?) {
        guard date != due else { return }
        dueTask?.cancel()
        due = date
        guard let date else {
            dueTask = nil
            return
        }
        dueTask = Task { [weak self] in
            let wait = max(0, date.timeIntervalSinceNow)
            do { try await Task.sleep(nanoseconds: UInt64(wait * 1_000_000_000)) } catch { return }
            guard let self else { return }
            self.dueTask = nil
            self.due = nil
            if let state = self.session?.state { self.sync(state) }
        }
    }

    /// Security: the answer goes out only when `GlanceContent.canAnswer` still holds on the
    /// current state, and it never remembers the permission.
    private func answer(botId: String, threadId: String, requestId: String, allow: Bool) async {
        guard let session,
              GlanceContent.canAnswer(state: session.state, botId: botId, requestId: requestId, allow: allow),
              let ask = Glance.currentAsk(session.state, botId: botId, requestId: requestId),
              let choice = choice(for: ask, allow: allow, in: session.state)
        else { return }
        session.actionError = nil
        await session.answer(threadId: ask.threadId, requestId: requestId, choice: choice, isPermission: true)
        if session.actionError != nil || session.status == .unauthorized {
            couldNotSend.insert(requestId)
            session.actionError = nil
            sync(session.state)
        }
    }

    private func choice(for ask: GlanceAsk, allow: Bool, in state: CompanionState) -> String? {
        let card = state.pendingApprovals.first { $0.message.card?.requestId == ask.requestId }?.message.card
        let options = card?.options ?? []
        if allow {
            return options.first { $0.trimmingCharacters(in: .whitespaces).caseInsensitiveCompare("Allow") == .orderedSame }
                ?? options.first { $0.trimmingCharacters(in: .whitespaces).caseInsensitiveCompare("Allow once") == .orderedSame }
        }
        return options.first { OptionCard.isRefusal($0) } ?? "Deny"
    }
}

#if DEBUG
extension CompanionState {
    /// `-glance-preview`: one bot asks to run a short command, so Glance shows an approval.
    mutating func applyGlancePreview() {
        bots.removeAll { $0.id != "preview-mochi" }
        guard let threadId = bots.first?.threadId,
              let index = messages[threadId]?.lastIndex(where: { $0.card?.isPending == true }) else { return }
        guard var card = messages[threadId]?[index].card else { return }
        card.title = "Approval needed"
        card.subtitle = "git push origin fix/sidebar-search-collapse"
        card.options = ["Allow", "Deny"]
        card.requestId = "preview-glance"
        card.tool = "Bash"
        card.requestType = "permission"
        card.questionRequest = nil
        card.held = nil
        messages[threadId]?[index].card = card
    }
}
#endif
