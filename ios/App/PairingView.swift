// Pairing: make QR scanning the obvious path, then keep discovery and manual
// entry available without making network plumbing part of onboarding.
import SwiftUI
import CompanionCore
#if canImport(UIKit)
import UIKit
#endif

struct PairingView: View {
    @EnvironmentObject private var session: Session
    @StateObject private var discovery = Discovery()
    @StateObject private var torch = TorchController()

    @State private var manualAddress = ""
    @State private var code = ""
    @State private var scannedCredential: String?
    /// Stable across Retry. If the Mac committed a device but the response
    /// was lost, repeating this same logical request recovers its token.
    @State private var pairRequestId: String?
    @State private var chosen: Connection?
    @State private var submission = CompanionPairingSubmissionState()
    @State private var failure: String?
    @State private var showingManualInput = false
    @State private var choiceGeneration = 0

    private let onCancel: () -> Void

    init(onCancel: @escaping () -> Void = {}) {
        self.onCancel = onCancel
    }

    private var pairing: Bool { submission.isInFlight }

    private var phase: PairViewfinderPhase {
        chosen != nil ? .found : .scanning
    }

    var body: some View {
        NavigationStack {
            ZStack(alignment: .topLeading) {
                Color.seiraApp.ignoresSafeArea()
                ScrollView { scrollContent }
                PairBackButton(action: onCancel)
                    .disabled(!submission.allowsNavigation)
                    .padding(.leading, 12)
                    .padding(.top, 4)
            }
            .safeAreaInset(edge: .bottom) { bottomCard }
            .toolbar(.hidden, for: .navigationBar)
            .onAppear {
                discovery.start()
                accept(session.pairingInvite)
                showLinkError(session.pairingLinkError)
            }
            .onDisappear {
                choiceGeneration += 1
                discovery.stop()
            }
            .onValueChange(of: session.pairingInvite) { invite in accept(invite) }
            .onValueChange(of: session.pairingLinkError) { message in showLinkError(message) }
            .interactiveDismissDisabled(pairing)
        }
    }

    private var scrollContent: some View {
        VStack(spacing: 0) {
            PairHeader()
            PairViewfinder(
                phase: phase,
                isScanning: chosen == nil,
                torch: torch,
                onPayload: handleScanned
            )
            .padding(.top, 24)
            PairStatusLine(text: statusText, isPositive: chosen != nil)
                .padding(.top, 16)
            details
        }
        .padding(.top, 56)
        .padding(.bottom, 24)
        .frame(maxWidth: 560)
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder private var details: some View {
        VStack(spacing: 16) {
            if let failure {
                PairErrorBanner(message: failure)
            }
            if let chosen {
                consentDetails(for: chosen)
            } else if chosen == nil {
                searchDetails
            }
        }
        .padding(.horizontal, 12)
        .padding(.top, 16)
    }

    @ViewBuilder private var searchDetails: some View {
        if showingManualInput {
            PairSurfaceCard { manualEntry }
        }
        if discovery.found.count > 1 {
            PairSurfaceCard { otherComputers }
        }
    }

    private var statusText: Text {
        if pairing { return Text("Connecting…") }
        if chosen != nil {
            return scannedCredential != nil ? Text("Code found") : Text("Enter the code shown on your computer")
        }
        return Text("Looking for a code…")
    }

    private func handleScanned(_ payload: String) -> Bool {
        guard let url = URL(string: payload), let invite = PairingInvite.parse(url) else {
            failure = String(localized: "That isn't a Seira pairing QR code.")
            return false
        }
        accept(invite)
        return true
    }

    @ViewBuilder private var bottomCard: some View {
        if let chosen {
            PairBottomCard { chosenCard(for: chosen) }
        } else {
            PairBottomCard { searchCard }
        }
    }

    @ViewBuilder private var searchCard: some View {
        if let service = discovery.found.first {
            PairComputerRow(
                symbol: "laptopcomputer",
                title: Text(verbatim: service.name),
                subtitle: Text("Same Wi-Fi")
            )
            PairPrimaryButton(title: "Connect") {
                Haptics.selection()
                Task { await choose(service) }
            }
        } else {
            PairComputerRow(
                symbol: "laptopcomputer",
                title: Text("Looking for your computer"),
                subtitle: Text(discovery.failure ?? "Open Seira on it and stay on the same Wi-Fi.")
            )
        }
        PairTextButton(title: "Enter address and code") {
            Haptics.selection()
            withSeiraAnimation(SeiraMotion.base) { showingManualInput.toggle() }
        }
    }

    @ViewBuilder
    private func chosenCard(for connection: Connection) -> some View {
        PairComputerRow(
            symbol: "laptopcomputer",
            title: Text(verbatim: connection.name),
            subtitle: chosenSubtitle(for: connection)
        )
        if scannedCredential == nil {
            codeEntry
        }
        PairPrimaryButton(
            title: pairing ? "Connecting…" : "Connect",
            isBusy: pairing,
            isDisabled: scannedCredential == nil && !Self.codeLooksComplete(code)
        ) {
            Haptics.selection()
            beginSubmission(connection, credential: scannedCredential ?? code)
        }
        PairTextButton(title: "Choose a different computer", isDisabled: !submission.allowsNavigation) {
            Haptics.selection()
            chooseDifferentComputer()
        }
    }

    private func chosenSubtitle(for connection: Connection) -> Text {
        guard scannedCredential != nil else { return Text("Enter the code shown on your computer") }
        return Text(connectionBadge(for: connection).title)
    }

    private var codeEntry: some View {
        VStack(spacing: 8) {
            TextField("000000", text: $code)
                .keyboardType(.asciiCapable)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .textContentType(.oneTimeCode)
                .font(.system(size: 28, weight: .bold, design: .rounded))
                .foregroundStyle(Color.seiraInk)
                .multilineTextAlignment(.center)
                .padding(.vertical, 12)
                .background(Color.seiraApp, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .onValueChange(of: code) { value in
                    // six digits for a computer, ABCD-EFGH-JKLM for a server
                    code = String(value.uppercased().filter { $0.isASCII && ($0.isNumber || $0.isLetter || $0 == "-") }.prefix(14))
                }

            if code.count >= 12, !Self.codeLooksComplete(code) {
                Text("A server's code is 12 letters and digits, never 0, O, 1 or I.")
                    .font(.seira(13, .regular, relativeTo: .subheadline))
                    .foregroundStyle(Color.seiraInk2)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(.top, 14)
    }

    private var otherComputers: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Other computers nearby")
                .font(.seira(13, .medium, relativeTo: .subheadline))
                .foregroundStyle(Color.seiraInk3)
            ForEach(Array(discovery.found.dropFirst())) { service in
                otherComputerRow(service)
            }
        }
    }

    private func otherComputerRow(_ service: Discovery.Found) -> some View {
        Button {
            Haptics.selection()
            Task { await choose(service) }
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "laptopcomputer")
                    .foregroundStyle(Color.seiraInk2)
                    .frame(width: 24)
                    .accessibilityHidden(true)
                Text(verbatim: service.name)
                    .font(.seira(16, .medium))
                    .foregroundStyle(Color.seiraInk)
                    .frame(maxWidth: .infinity, alignment: .leading)
                SettingsChevron()
            }
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityHint("Enter the code shown on this computer")
    }

    private var manualEntry: some View {
        VStack(alignment: .leading, spacing: 12) {
            TextField("Computer address", text: $manualAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.URL)
                .textContentType(.URL)
                .padding(12)
                .background(Color.seiraApp, in: RoundedRectangle(cornerRadius: 12, style: .continuous))

            Text("Use the address shown in Phone settings on your computer.")
                .font(.seira(13, .regular, relativeTo: .subheadline))
                .foregroundStyle(Color.seiraInk2)

            Button {
                continueWithManualAddress()
            } label: {
                Text("Continue")
                    .font(.seira(15, .medium))
            }
            .buttonStyle(SeiraControlButtonStyle())
            .disabled(manualAddress.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
    }

    private func continueWithManualAddress() {
        Haptics.selection()
        failure = nil
        // The whole link a server printed (https://host/pair#code=…) is
        // fine here too: it names both the address and the code.
        if let url = URL(string: manualAddress.trimmingCharacters(in: .whitespacesAndNewlines)),
           let invite = PairingInvite.parse(url) {
            accept(invite)
            return
        }
        guard let connection = Self.parse(manualAddress) else {
            failure = String(localized: "That address doesn't look right. Copy it from Phone settings and try again.")
            return
        }
        choiceGeneration += 1
        scannedCredential = nil
        pairRequestId = nil
        chosen = connection
    }

    private func chooseDifferentComputer() {
        chosen = nil
        code = ""
        scannedCredential = nil
        pairRequestId = nil
        failure = nil
    }

    private func consentDetails(for connection: Connection) -> some View {
        PairSurfaceCard {
            Text("Computer address")
                .font(.seira(14, .semibold, relativeTo: .subheadline))
                .foregroundStyle(Color.seiraInk)
            Text(connection.pairingConsentOrigin)
                .font(.footnote.monospaced())
                .foregroundStyle(Color.seiraInk2)
                .textSelection(.enabled)
                .fixedSize(horizontal: false, vertical: true)
            if connection.otherPairingAddressCount > 0 {
                // A desktop QR consents to every local address it carries
                // (`establishRoutePolicyFromInvite(everyLocalRoute:)`), so
                // the confirmation says so rather than showing only the first.
                consentNote("and \(connection.otherPairingAddressCount) more of this computer’s addresses")
            }
            consentNote("Make sure this is the computer you expect before connecting.")
            if scannedCredential != nil, !connectionIsProtected(connection) {
                consentNote("Only continue on a network you trust. Local connections are authenticated but are not encrypted by Seira.")
            }
        }
    }

    private func consentNote(_ text: LocalizedStringKey) -> some View {
        Text(text)
            .font(.seira(13, .regular, relativeTo: .subheadline))
            .foregroundStyle(Color.seiraInk2)
            .fixedSize(horizontal: false, vertical: true)
    }

    @MainActor
    private func choose(_ service: Discovery.Found) async {
        choiceGeneration += 1
        let generation = choiceGeneration
        failure = nil
        pairRequestId = nil
        do {
            let resolved = try await discovery.resolve(service)
            guard generation == choiceGeneration else { return }
            chosen = resolved
        } catch {
            guard generation == choiceGeneration else { return }
            failure = error.localizedDescription
        }
    }

    @MainActor
    private func beginSubmission(_ connection: Connection, credential: String) {
        guard submission.begin() else { return }
        Task { await submit(connection, credential: credential) }
    }

    @MainActor
    private func submit(_ connection: Connection, credential: String) async {
        failure = nil
        var succeeded = false
        defer {
            submission.finish()
            // A deep link received during the commit cannot replace the
            // consent screen. If this request failed, present it only after
            // the in-flight request has fully settled.
            if succeeded {
                session.consumePairingInvite()
                onCancel()
            } else {
                accept(session.pairingInvite)
            }
        }
        let cameFromScanner = scannedCredential != nil
        let requestId = pairRequestId ?? UUID().uuidString
        pairRequestId = requestId
        do {
            try await session.pair(
                with: connection,
                credential: credential,
                deviceName: Self.deviceName(),
                pairRequestId: requestId
            )
            pairRequestId = nil
            succeeded = true
        } catch {
            if cameFromScanner {
                if error is PairingRouteError {
                    failure = error.localizedDescription
                } else {
                    failure = String(localized: "\(error.localizedDescription) Start pairing again on your computer and rescan the new QR code.")
                    chosen = nil
                    scannedCredential = nil
                    pairRequestId = nil
                }
            } else {
                failure = error.localizedDescription
                if !(error is PairingRouteError) {
                    code = ""
                    pairRequestId = nil
                }
            }
        }
    }

    /// A companion's six digits, or a server's twelve characters.
    static func codeLooksComplete(_ code: String) -> Bool {
        (code.count == 6 && code.allSatisfy(\.isNumber)) || PairingInvite.normalizedServerCode(code) != nil
    }

    private func accept(_ invite: PairingInvite?) {
        guard submission.allowsNavigation, let invite else { return }
        choiceGeneration += 1
        chosen = invite.connection
        scannedCredential = invite.credential
        pairRequestId = UUID().uuidString
        code = ""
        failure = nil
        session.consumePairingInvite()
    }

    /// A pairing link that could not be read opened this screen; say so here,
    /// next to the button that fixes it.
    private func showLinkError(_ message: String?) {
        guard let message else { return }
        failure = String(localized: String.LocalizationValue(message))
        session.consumePairingLinkError()
    }

    private func connectionIsProtected(_ connection: Connection) -> Bool {
        connection.activeEndpoint?.protectsCredentials
            ?? connection.automaticEndpoints.first?.protectsCredentials
            ?? false
    }

    private func connectionBadge(for connection: Connection) -> (title: LocalizedStringKey, systemImage: String) {
        let kind = connection.activeEndpoint?.kind ?? connection.automaticEndpoints.first?.kind
        if kind == .hosted {
            return ("HTTPS connection", "lock.shield.fill")
        }
        if kind == .tailnet {
            return ("Tailscale connection", "lock.shield.fill")
        }
        return ("Trusted local connection", "checkmark.shield.fill")
    }

    static func deviceName() -> String {
        #if canImport(UIKit)
        return UIDevice.current.name
        #else
        return "Companion"
        #endif
    }

    static func parse(_ text: String) -> Connection? {
        Connection.parse(text)
    }
}
