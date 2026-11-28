// App entry, and the one place that decides when the event stream lives.
//
// A phone is not a desktop: the stream is torn down the moment the app
// leaves the screen's short background grace period, because iOS is going to
// suspend it anyway and doing it deliberately means the cursor is written
// down at a known point. Coming back asks the harness what was missed rather
// than asking for everything.
import SwiftUI
import CompanionCore
import UserNotifications

@main
struct CompanionApp: App {
    /// Held, not observed: nothing in the scene reads the session's state,
    /// and observing it here re-created the root view on every publish —
    /// a second full Home render each time a busy fleet sent a batch. The
    /// views that draw from the session observe it themselves.
    @State private var session = Session()
    @StateObject private var liveCall = LiveCallController.forThisLaunch()
    @Environment(\.scenePhase) private var scenePhase
    @State private var liveActivities = LiveActivityBridge()
    @State private var widgetSync = WidgetSyncBridge.makeAppGroupBridge()
    @AppStorage(PrefKey.language) private var language = AppLanguage.system.rawValue
    @State private var showsLaunchVisor = true

    var body: some Scene {
        WindowGroup {
            ZStack {
                RootView()
                    .liveCallScreen()
                    .appliesAppearance()
                    .environmentObject(session)
                    .environmentObject(liveCall)
                    .environment(\.avatarLoader, AvatarLoader(session: session))
                    // One modifier is the whole language seam. SwiftUI resolves a
                    // `LocalizedStringKey` against the environment's locale, so
                    // every `Text("…")`, `Button("…")`, `Section("…")` and
                    // `navigationTitle("…")` already in the app — including the
                    // ones inside sheets and pushed screens — follows this line
                    // without a call site changing. Following the system means
                    // leaving it on the phone's own locale, which is what an app
                    // with no language setting would use anyway.
                    .environment(\.locale, AppLanguage.resolved(language).locale ?? .autoupdatingCurrent)
                    // A navigation title is drawn by UIKit, which reads its string
                    // once and does not re-read it when the environment changes —
                    // so the body would switch language while the title above it
                    // stayed behind. Re-identifying the tree on the chosen language
                    // rebuilds that chrome. It costs the navigation stack and any
                    // view state below, which is the right trade for something a
                    // person changes deliberately and almost never. `session` lives
                    // on the App, not here, so the connection survives.
                    .id(language)
                    .onAppear {
                        SeiraSharedInbox.removeDirectories(olderThan: 60 * 60)
                        session.connect()
                        liveActivities.attach(to: session)
                        widgetSync.attach(to: session)
                        liveCall.attach(to: session)
                    }
                    .onOpenURL { url in
                        if let route = AppRouter.route(for: url) {
                            AppRouter.shared.pending = route
                        } else {
                            session.receiveURL(url)
                        }
                    }
                    .onValueChange(of: scenePhase) { phase in
                        // Only a true background hangs up (CompanionCore's rule):
                        // Control Center and the mic prompt pass through .inactive.
                        // Hang up *before* linger(): the end request rides the same
                        // ~25 s background window the stream's cursor write uses.
                        if LiveCallLifecycle.endsCall(on: phase.liveCallPhase) { liveCall.leaveForeground() }
                        switch phase {
                        case .active:
                            SeiraSharedInbox.removeDirectories(olderThan: 60 * 60)
                            session.connect()
                            Task { await session.refreshNotificationAuthorization() }
                        case .background:
                            session.linger()
                            widgetSync.flush(session.state, connectionID: session.connection?.id)
                        case .inactive: break
                        @unknown default: break
                        }
                    }

                if showsLaunchVisor {
                    LaunchVisorView { showsLaunchVisor = false }
                }
            }
        }
        // `.defaultSize` is iOS 17 and `SceneBuilder` takes no conditionals,
        // so it is dropped while the app supports 16. It only suggested an
        // opening window size for iPad and Stage Manager; the system picks a
        // sensible one on its own.
    }
}

struct RootView: View {
    @EnvironmentObject private var session: Session
    @EnvironmentObject private var liveCall: LiveCallController
    @ObservedObject private var router = AppRouter.shared
    @State private var talkConfirmation: Bot?
    @AppStorage("companion.onboarding.welcomeSeen") private var hasSeenWelcome = false
    @AppStorage("companion.onboarding.notificationsSeen") private var hasSeenNotificationPrompt = false
    @AppStorage(CompanionOnboardingPreferences.pendingNotificationOnboardingKey)
    private var notificationOnboardingPending = false
    var body: some View {
        let _ = busyFleetPass("root")
        Group {
            switch route {
            case .welcome:
                CompanionWelcomeView(
                    onConnect: startPairing,
                    onSkip: {
                        hasSeenWelcome = true
                        session.endPairing()
                    }
                )
            case .pairing:
                PairingView {
                    hasSeenWelcome = true
                    session.endPairing()
                }
                .onAppear {
                    hasSeenWelcome = true
                    session.beginPairing()
                }
            case .unpairedHome:
                UnpairedHomeView(onConnect: startPairing)
            case .notificationPrompt:
                NotificationOnboardingView {
                    hasSeenNotificationPrompt = true
                    notificationOnboardingPending = false
                    session.endPairing()
                }
                .onAppear { hasSeenWelcome = true }
            case .chats:
                Group {
                #if DEBUG
                if ProcessInfo.processInfo.arguments.contains("-browser-preview"), let bot = session.state.bots.first {
                    BrowserPreviewNavigation(bot: bot)
                } else {
                    ChatListView()
                }
                #else
                ChatListView()
                #endif
                }
                    .onAppear {
                        hasSeenWelcome = true
                        // This is either an existing pairing or a new pairing
                        // which needed no notification education. Do not let
                        // a later voluntary unpair reopen Pairing by itself.
                        session.endPairing()
                        reconcileNotificationOnboarding()
                    }
            case .revoked:
                UnpairedView(
                    onPairAgain: {
                        session.pairAgain()
                        startPairing()
                    },
                    onChooseAnother: session.connections.first(where: {
                        $0.id != session.connection?.id
                    }).map { computer in
                        { session.switchComputer(to: computer.id) }
                    }
                )
            }
        }
        .background(Color(uiColor: .systemBackground).ignoresSafeArea())
        .onValueChange(of: session.pairingInvite) { invite in
            guard invite != nil else { return }
            hasSeenWelcome = true
            session.beginPairing()
        }
        .onAppear { reconcileNotificationOnboarding() }
        .modifier(TalkConfirmation(bot: $talkConfirmation, start: startTalk))
        .onValueChange(of: router.pending, initial: true) { _ in handlePendingRoute() }
        .onValueChange(of: session.status) { _ in handlePendingRoute() }
        .onValueChange(of: session.connection?.id) { _ in handlePendingRoute() }
        .onValueChange(of: session.notificationAuthorizationResolved) { _ in
            reconcileNotificationOnboarding()
        }
        .onValueChange(of: session.notificationAuthorization) { _ in
            reconcileNotificationOnboarding()
        }
        .onValueChange(of: notificationOnboardingPending) { isPending in
            if isPending { reconcileNotificationOnboarding() }
        }
        .alert(
            "Something went wrong",
            isPresented: Binding(
                get: { session.actionError != nil },
                set: { if !$0 { session.actionError = nil } }
            ),
            presenting: session.actionError
        ) { _ in
            Button("OK", role: .cancel) { session.actionError = nil }
        } message: { message in
            Text(message)
        }
    }

    private var route: CompanionOnboardingRoute {
        let pairingState: CompanionPairingState
        if session.status == .unauthorized {
            pairingState = .revoked
        } else if session.connection != nil {
            pairingState = .paired
        } else {
            pairingState = .unpaired
        }
        return CompanionOnboardingRouter.route(for: .init(
            pairingState: pairingState,
            hasSeenWelcome: hasSeenWelcome,
            pairingRequested: session.pairingRequested,
            hasPendingPairingInvite: session.pairingInvite != nil,
            notificationOnboardingPending: notificationOnboardingPending,
            hasSeenNotificationPrompt: hasSeenNotificationPrompt,
            notificationAuthorization: notificationAuthorizationState
        ))
    }

    private var notificationAuthorizationState: CompanionNotificationAuthorizationState {
        #if DEBUG
        // Store-preview runs are deterministic screenshot fixtures, not a
        // first pairing, and must keep landing on the requested chat surface.
        if ProcessInfo.processInfo.arguments.contains("-store-preview") { return .determined }
        #endif
        guard session.notificationAuthorizationResolved else { return .unresolved }
        return session.notificationAuthorization == .notDetermined ? .notDetermined : .determined
    }

    private func reconcileNotificationOnboarding() {
        notificationOnboardingPending = CompanionNotificationOnboardingPolicy.shouldKeepPending(
            isPending: notificationOnboardingPending,
            hasCompletedStep: hasSeenNotificationPrompt,
            authorization: notificationAuthorizationState
        )
    }

    private func startPairing() {
        hasSeenWelcome = true
        session.beginPairing()
    }

    /// A route waits until the chat list is ready: the computer is paired and
    /// its stream has delivered the bots. An app that is not paired drops it.
    private func handlePendingRoute() {
        guard let pending = router.pending else { return }
        guard session.connection != nil else {
            router.pending = nil
            return
        }
        switch session.status {
        case .connecting, .offline:
            return
        case .live:
            break
        case .unpaired, .unauthorized:
            router.pending = nil
            return
        }
        run(pending)
        router.pending = nil
    }

    private func run(_ route: AppRoute) {
        let store = session.state
        guard let chief = store.unsectionedChief ?? store.sidebarSections.flatMap(\.chiefs).first else { return }
        switch route {
        case .newChat:
            Task {
                if let created = await session.createRosterThread(for: chief) {
                    session.openChat(threadId: created.threadId)
                }
            }
        case .talk(let confirmed):
            if liveCall.machine.isActive {
                liveCall.presentScreen()
            } else if confirmed, !LiveCallDisclosure().isDue {
                startTalk(chief)
            } else {
                talkConfirmation = chief
            }
        }
    }

    private func startTalk(_ bot: Bot) {
        guard !liveCall.machine.isActive else { return }
        liveCall.start(bot: bot)
        liveCall.presentScreen()
    }
}

/// The token stopped working. Almost always because someone revoked this
/// phone on the computer — which is exactly what that button is for, so the
/// honest thing is to say so and offer to pair again.
struct UnpairedView: View {
    let onPairAgain: () -> Void
    let onChooseAnother: (() -> Void)?
    @EnvironmentObject private var liveCall: LiveCallController

    var body: some View {
        NavigationStack {
            SeiraEmptyState(
                icon: "Icons3D/not-connected",
                title: "This device was unpaired",
                message: "The connection was removed on your computer. Pair again to keep using your chats here."
            ) {
                // A Live call the refusal hung up says why here: this screen
                // replaces the chat whose bar would have said it.
                if case let .stopped(_, notice) = liveCall.machine.phase, case .signedOut = notice {
                    notice.text
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .accessibilityIdentifier("live-call-signed-out")
                }
                Button("Pair again", action: onPairAgain)
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                if let onChooseAnother {
                    Button("Use another computer", action: onChooseAnother)
                        .buttonStyle(.bordered)
                        .controlSize(.large)
                }
            }
        }
    }
}
