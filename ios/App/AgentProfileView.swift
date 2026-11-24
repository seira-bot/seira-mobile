import CompanionCore
import SwiftUI

/// The paired-safe subset of bot settings. Shared provider keys remain on the
/// computer; the phone sees only the model catalog, configured/not-configured
/// status, and renderer-neutral profile operations.
struct AgentProfileView: View {
    let bot: Bot

    @EnvironmentObject private var session: Session
    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var title: String
    @State private var description: String
    @State private var notifications: Bool
    @State private var voice: String
    @State private var speakReplies: Bool
    @State private var voices: [Voice] = []
    @State private var config: ConfigStatus?
    @State private var engine: VoiceProvider = .elevenlabs
    @State private var hostIsMac = false
    @State private var chatterboxURL = ""
    @State private var chatterboxModel = ""
    @State private var switchingEngine = false
    @State private var savingServer = false
    @State private var serverProblem: String?
    @State private var instances: [Instance] = []
    @State private var limits: [String: EngineLimit] = [:]
    @State private var modelsLoaded = false
    @State private var selectedInstanceID: String
    @State private var selectedModelID: String
    @State private var selectedEffort: String?
    @State private var savedModel: ModelSelection
    @State private var busy = false
    @StateObject private var player = VoiceNotePlayer()
    @State private var previewTask: Task<Void, Never>?
    @State private var baseline: ProfileFormSnapshot
    @State private var showingChangeLook = false

    init(bot: Bot) {
        self.bot = bot
        _name = State(initialValue: bot.name)
        _title = State(initialValue: bot.title)
        _description = State(initialValue: bot.description)
        _notifications = State(initialValue: bot.notifications)
        _voice = State(initialValue: bot.voice ?? "")
        _speakReplies = State(initialValue: bot.speakReplies == true)
        _selectedInstanceID = State(initialValue: bot.currentTaskModelSelection.instanceId)
        _selectedModelID = State(initialValue: bot.currentTaskModelSelection.model)
        _selectedEffort = State(initialValue: bot.currentTaskModelSelection.effort)
        _savedModel = State(initialValue: bot.currentTaskModelSelection)
        _baseline = State(initialValue: ProfileFormSnapshot(bot: bot))
    }

    private var current: Bot { session.state.bot(bot.id)?.projected(forThread: bot.threadId) ?? bot }
    private var voiceConfigured: Bool { config?.isTTSConfigured == true }
    private var hasWorkspaceDefaultVoice: Bool { config?.hasWorkspaceDefaultVoice == true }
    private var selectedVoiceCanSpeak: Bool { config?.canSpeak(agentVoice: voice) == true }
    private var selectedInstance: Instance? {
        instances.first { $0.instanceId == selectedInstanceID }
    }
    private var availableInstances: [Instance] { instances.filter(\.snapshot.isAvailable) }
    private var instanceChoices: [Instance] {
        guard let currentInstance = instances.first(where: { $0.instanceId == savedModel.instanceId }),
              !currentInstance.snapshot.isAvailable
        else { return availableInstances }
        return [currentInstance] + availableInstances
    }
    private var selectedModelChoices: [ModelChoice] {
        guard let instance = selectedInstance else {
            return selectedModelID.isEmpty ? [] : [ModelChoice(id: selectedModelID, label: selectedModelID)]
        }
        var seen = Set<String>()
        var choices: [ModelChoice] = []
        let defaultOption = instance.models.options.first { $0.id == instance.models.default }
        if !instance.models.default.isEmpty {
            seen.insert(instance.models.default)
            choices.append(ModelChoice(
                id: instance.models.default,
                label: defaultOption?.label ?? instance.models.default
            ))
        }
        for option in instance.models.options where seen.insert(option.id).inserted {
            choices.append(ModelChoice(id: option.id, label: option.label))
        }
        if !selectedModelID.isEmpty, seen.insert(selectedModelID).inserted {
            choices.append(ModelChoice(id: selectedModelID, label: selectedModelID))
        }
        return choices
    }
    private var effortLevels: [String] {
        var seen = Set<String>()
        return (selectedInstance?.capabilities?.effortLevels ?? []).filter {
            !$0.isEmpty && seen.insert($0).inserted
        }
    }
    private var modelDraft: ModelSelection {
        ModelSelection(instanceId: selectedInstanceID, model: selectedModelID, effort: selectedEffort)
    }
    private var canApplyModel: Bool {
        guard modelsLoaded, current.busy != true,
              let selectedInstance, selectedInstance.snapshot.isAvailable
        else { return false }
        let modelIsOffered = selectedModelID == selectedInstance.models.default
            || selectedInstance.models.options.contains { $0.id == selectedModelID }
        let effortIsOffered = selectedEffort.map(effortLevels.contains) ?? true
        return modelIsOffered && effortIsOffered && modelDraft != savedModel
    }
    /// Which engine's words to use. An unloaded status is ElevenLabs for the
    /// same reason a missing `provider` is: that is the server's own fallback,
    /// and the copy that shipped.
    private var usesSystemVoices: Bool { config?.voiceProvider == .system }
    private var usesChatterbox: Bool { config?.voiceProvider == .chatterbox }
    private var usesFishAudio: Bool { config?.voiceProvider == .fish }

    var body: some View {
        NavigationStack {
            Form {
                // Changing the model needs the admin scope on a server; a
                // chat-only phone is not shown either.
                if session.canAdminister {
                    modelSection
                }
                avatarSection
                identitySection
                voiceSection
                Section {
                    Button("Save profile changes") { Task { await save() } }
                        .disabled(busy || name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .navigationTitle("Bot settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Done") { dismiss() } }
            }
            .overlay { if busy { ProgressView().controlSize(.large) } }
            .task { await loadProfile() }
            .task { await loadUsage() }
            .onValueChange(of: engine) { selected in
                Task { await switchEngine(to: selected) }
            }
            .onValueChange(of: player.isPlaying) { playing in
                if !playing { VoiceNoteCenter.shared.release(player) }
            }
            .onDisappear {
                previewTask?.cancel()
                player.pause()
                VoiceNoteCenter.shared.release(player)
            }
            .sheet(isPresented: $showingChangeLook) {
                ChangeLookSheet(bot: current)
                    .environmentObject(session)
            }
        }
    }

    private var modelSection: some View {
        Section {
            if !modelsLoaded {
                HStack {
                    Text("Loading models")
                    Spacer()
                    ProgressView()
                }
            } else if instanceChoices.isEmpty {
                Label("No model providers are available", systemImage: "exclamationmark.triangle")
                    .foregroundStyle(.secondary)
            } else {
                modelPickers
            }
        } header: {
            Text("Model")
        } footer: {
            Text("Provider accounts and API keys stay on your computer. Default sends no reasoning level and lets the provider decide.")
        }
    }

    @ViewBuilder private var modelPickers: some View {
        Picker("Provider", selection: $selectedInstanceID) {
            if !instances.contains(where: { $0.instanceId == selectedInstanceID }) {
                Text("Current provider (unavailable)")
                    .tag(selectedInstanceID)
                    .disabled(true)
            }
            ForEach(instanceChoices) { instance in
                Text(instanceLabel(instance))
                    .tag(instance.instanceId)
                    .disabled(!instance.snapshot.isAvailable)
            }
        }
        .onValueChange(of: selectedInstanceID) { instanceID in
            selectDefaults(for: instanceID)
        }

        Picker("Model", selection: $selectedModelID) {
            ForEach(selectedModelChoices) { option in
                Text(option.label).tag(option.id)
            }
        }
        .disabled(selectedInstance?.snapshot.isAvailable != true)

        if !effortLevels.isEmpty {
            Picker("Reasoning effort", selection: $selectedEffort) {
                Text("Default").tag(String?.none)
                ForEach(effortLevels, id: \.self) { level in
                    Text(effortLabel(level)).tag(Optional(level))
                }
            }
        }

        if current.busy == true {
            Label("Stop this bot before changing its model.", systemImage: "hourglass")
                .font(.footnote)
                .foregroundStyle(.secondary)
        } else if selectedInstance?.snapshot.isAvailable != true {
            Label("Choose an available provider to change this bot's model.", systemImage: "info.circle")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }

        Button("Apply model", systemImage: "checkmark") {
            Task { await saveModel() }
        }
        .disabled(busy || !canApplyModel)
    }

    private var avatarSection: some View {
        Section {
            Button { showingChangeLook = true } label: {
                HStack(spacing: 14) {
                    BotAvatarView(bot: current, size: 44, state: .idle)
                    Text("Avatar")
                        .foregroundStyle(Color.primary)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.tertiary)
                }
            }
        }
    }

    private var identitySection: some View {
        Section("Identity") {
            NavigationLink {
                BotOverviewView(bot: current)
            } label: {
                Label("What this bot does", systemImage: "list.bullet.rectangle")
            }
            NavigationLink {
                BotActivityView(bot: current)
            } label: {
                Label("Activity", systemImage: "checklist")
            }
            NavigationLink {
                TeamMemoryView(section: current.section ?? "")
            } label: {
                Label("Team memory", systemImage: "brain")
            }
            TextField("Name", text: $name)
                .textInputAutocapitalization(.words)
            TextField("Title", text: $title)
            TextField("What this agent does", text: $description, axis: .vertical)
                .lineLimit(3...8)
            Toggle("Agent notifications", isOn: $notifications)
        }
    }

    private var voiceSection: some View {
        Section {
            Picker("Voice engine", selection: $engine) {
                Text("ElevenLabs").tag(VoiceProvider.elevenlabs)
                Text("Fish Audio").tag(VoiceProvider.fish)
                Text("Built-in Mac voices")
                    .tag(VoiceProvider.system)
                    .disabled(!hostIsMac)
                Text("Chatterbox (local)").tag(VoiceProvider.chatterbox)
            }
            .disabled(switchingEngine)

            if usesChatterbox {
                chatterboxFields
            }

            if voiceConfigured {
                voiceControls
            } else if usesSystemVoices {
                Label("Built-in Mac voices are unavailable", systemImage: "speaker.slash")
                    .foregroundStyle(.secondary)
            } else if !usesChatterbox {
                Label(
                    usesFishAudio ? "Fish Audio is not configured" : "ElevenLabs is not configured",
                    systemImage: "speaker.slash"
                )
                    .foregroundStyle(.secondary)
            }
        } header: {
            Text("Voice")
        } footer: {
            voiceFooter
        }
    }

    @ViewBuilder private var chatterboxFields: some View {
        TextField(
            "Chatterbox server address",
            text: $chatterboxURL,
            prompt: Text("http://127.0.0.1:4123")
        )
        .keyboardType(.URL)
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
        TextField(
            "Chatterbox model",
            text: $chatterboxModel,
            prompt: Text("chatterbox-turbo")
        )
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
        Button {
            Task { await saveChatterboxServer() }
        } label: {
            HStack {
                Text("Save server")
                if savingServer { Spacer(); ProgressView() }
            }
        }
        .disabled(savingServer || chatterboxURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        if let serverProblem {
            Label(serverProblem, systemImage: "exclamationmark.triangle")
                .foregroundStyle(Color.orange)
        }
    }

    @ViewBuilder private var voiceControls: some View {
        Picker("Voice", selection: $voice) {
            if hasWorkspaceDefaultVoice {
                Text("Workspace default").tag("")
            } else {
                Text("Choose an agent voice").tag("").disabled(true)
            }
            if !voice.isEmpty, !voices.contains(where: { $0.id == voice }) {
                Text("Current agent voice").tag(voice)
            }
            ForEach(voices) { option in
                VStack(alignment: .leading) {
                    Text(option.label)
                    if let detail = option.description { Text(detail) }
                }
                .tag(option.id)
            }
        }
        Toggle("Speak replies", isOn: $speakReplies)
            .disabled(!selectedVoiceCanSpeak)
        Button("Preview voice", systemImage: "speaker.wave.2") {
            previewTask = Task { await previewVoice() }
        }
        .disabled(busy || !selectedVoiceCanSpeak)

        if !hasWorkspaceDefaultVoice, voice.isEmpty {
            Label("Pick a voice for this agent before enabling speech.", systemImage: "info.circle")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    @ViewBuilder private var voiceFooter: some View {
        if !voiceConfigured {
            // Under the built-in engine "not configured" is not a
            // missing credential — there is none — so the remedy
            // cannot be a key. `providerConfigured` in
            // `server/tts/index.ts` is reporting that this
            // computer has no built-in voices to speak with.
            if usesSystemVoices {
                Text("Built-in Mac voices need no key, and this computer has none available. Switch the voice engine above to ElevenLabs to keep using voice.")
            } else if usesChatterbox {
                Text("Any OpenAI-compatible server running Chatterbox works, no key needed. Save its address and model id above.")
            } else if usesFishAudio {
                Text("Add the shared Fish Audio key in Seira on your computer. The key is never returned to iOS.")
            } else {
                Text("Add the shared ElevenLabs key in this agent's profile on the computer. The key is never returned to iOS.")
            }
        } else if !hasWorkspaceDefaultVoice {
            if usesSystemVoices {
                Text("No workspace default voice is selected. Choose an agent-specific voice above; synthesis still uses the built-in Mac voices on your computer.")
            } else if usesChatterbox {
                Text("No workspace default voice is selected. Choose an agent-specific voice above; synthesis still uses the Chatterbox server on your computer.")
            } else if usesFishAudio {
                Text("No workspace default voice is selected. Choose an agent-specific voice above; synthesis still uses the shared Fish Audio key on your computer.")
            } else {
                Text("No workspace default voice is selected. Choose an agent-specific voice above; synthesis still uses the shared ElevenLabs key on your computer.")
            }
        } else {
            Text("The voice choice belongs to this agent. Workspace default uses the shared voice selected on your computer.")
        }
    }

    private func loadProfile() async {
        async let status = session.configStatus()
        async let options = session.voiceOptions()
        async let catalog = loadModelCatalog()
        async let environment = session.serverEnvironment()
        let (loadedConfig, loadedVoices, loadedInstances) = await (status, options, catalog)
        config = loadedConfig
        engine = loadedConfig?.voiceProvider ?? .elevenlabs
        chatterboxURL = loadedConfig?.tts?.baseUrl ?? ""
        chatterboxModel = loadedConfig?.tts?.model ?? ""
        hostIsMac = (await environment)?.platform == "darwin"
        voices = loadedVoices
        instances = loadedInstances
        modelsLoaded = true
        if let loadedConfig, !loadedConfig.canSpeak(agentVoice: voice) {
            speakReplies = false
        }
    }

    /// Switch the workspace's voice engine. The engine is a setting, not a
    /// secret, so it rides the ordinary config write; the voice list reloads
    /// because every engine offers different voices. A failed switch snaps
    /// the picker back to whatever the server still reports.
    private func switchEngine(to selected: VoiceProvider) async {
        guard selected != (config?.voiceProvider ?? .elevenlabs) else { return }
        switchingEngine = true
        defer { switchingEngine = false }
        if let status = await session.setVoiceProvider(selected) {
            let reset = AgentProfileVoiceState(
                voice: voice,
                speakReplies: speakReplies,
                baselineVoice: baseline.voice
            ).afterProviderSwitch(to: status)
            voice = reset.voice
            speakReplies = reset.speakReplies
            baseline.voice = reset.baselineVoice
            config = status
            engine = status.voiceProvider
            // Do not render the previous provider's catalog while the new
            // one loads. Its identifiers are invalid under this provider.
            voices = []
            voices = await session.voiceOptions()
        } else {
            engine = config?.voiceProvider ?? .elevenlabs
        }
    }

    private func saveChatterboxServer() async {
        let address = chatterboxURL.trimmingCharacters(in: .whitespacesAndNewlines)
        guard address.hasPrefix("http://") || address.hasPrefix("https://") else {
            serverProblem = String(localized: "The server address must start with http:// or https://.")
            return
        }
        serverProblem = nil
        savingServer = true
        defer { savingServer = false }
        // Both fields commit together: an address without its model id (or
        // the reverse) is half a setting, exactly as on the desktop.
        if let status = await session.saveChatterboxServer(
            baseURL: address,
            model: chatterboxModel.trimmingCharacters(in: .whitespacesAndNewlines)
        ) {
            config = status
            voices = await session.voiceOptions()
        }
    }

    private func profilePatch() -> BotProfilePatch {
        let savedSpeakReplies = config.map { $0.canSpeak(agentVoice: voice) && speakReplies } ?? speakReplies
        return BotProfilePatch(
            // The shared server contract owns the 100/200/4000 limits. Do not
            // silently apply narrower iOS-only limits to a user's profile.
            name: name == baseline.name ? nil : name.trimmingCharacters(in: .whitespacesAndNewlines),
            title: title == baseline.title ? nil : title.trimmingCharacters(in: .whitespacesAndNewlines),
            description: description == baseline.description
                ? nil : description.trimmingCharacters(in: .whitespacesAndNewlines),
            notifications: notifications == baseline.notifications ? nil : notifications,
            // Empty is the server's explicit "use workspace default" value;
            // nil would mean the voice field is not part of this patch.
            voice: voice == baseline.voice ? nil : voice,
            speakReplies: savedSpeakReplies == baseline.speakReplies ? nil : savedSpeakReplies
        )
    }

    private func save() async {
        busy = true
        if let updated = await session.updateProfile(profilePatch(), for: current) {
            synchronizeForm(with: updated)
        }
        busy = false
    }

    /// The provider list is admin-only on a server: asking without the
    /// scope would only put a 403 on screen for a picker that is not shown.
    private func loadModelCatalog() async -> [Instance] {
        session.canAdminister ? await session.modelInstances() : []
    }

    private func saveModel() async {
        guard canApplyModel else { return }
        busy = true
        defer { busy = false }
        if let updated = await session.updateModel(modelDraft, for: current) {
            let model = updated.projected(forThread: bot.threadId)?.currentTaskModelSelection ?? modelDraft
            selectedInstanceID = model.instanceId
            selectedModelID = model.model
            selectedEffort = model.effort
            savedModel = model
        }
    }

    private func selectDefaults(for instanceID: String) {
        guard let instance = instances.first(where: { $0.instanceId == instanceID }) else { return }
        if instanceID == savedModel.instanceId {
            selectedModelID = savedModel.model
            let supported = instance.capabilities?.effortLevels ?? []
            selectedEffort = savedModel.effort.flatMap { supported.contains($0) ? $0 : nil }
        } else {
            selectedModelID = instance.models.default
            selectedEffort = nil
        }
    }

    private func instanceLabel(_ instance: Instance) -> String {
        let base = instance.displayName?.trimmingCharacters(in: .whitespacesAndNewlines)
        let name = base.flatMap { $0.isEmpty ? nil : $0 } ?? instance.instanceId
        guard instance.snapshot.isAvailable else { return "\(name) (Unavailable)" }
        guard let note = usageNote(for: instance.instanceId) else { return name }
        return "\(name) · \(note)"
    }

    private func usageNote(for instanceId: String) -> String? {
        guard let limit = limits[instanceId], EngineUsage.reportsUsage(limit) else { return nil }
        let line = limitLine(limit)
        if line.atLimit {
            guard let reset = line.resetsAt else { return String(localized: "at limit") }
            return String(localized: "at limit until \(limitResetTime(reset))")
        }
        guard let left = usageRows(limit).map(\.left).min() else { return nil }
        return String(localized: "\(left)% left")
    }

    private func loadUsage() async {
        guard session.canAdminister else { return }
        if case let .report(report) = await session.loadEngineLimits() {
            limits = Dictionary(report.engines.map { ($0.engine, $0) }, uniquingKeysWith: { first, _ in first })
        }
    }

    private func effortLabel(_ effort: String) -> String {
        switch effort.lowercased() {
        case "xhigh": "X-High"
        default: effort.capitalized
        }
    }

    private func previewVoice() async {
        guard selectedVoiceCanSpeak else {
            session.actionError = "Pick an agent voice or configure a workspace default on your computer first."
            return
        }
        // The preview reconfigures and later deactivates the shared audio
        // session, which would cut a Live call's audio. Same arbiter as
        // voice notes: refused while an input owner holds the session.
        guard VoiceNoteCenter.shared.playbackAllowed else {
            session.actionError = String(localized: "Voice previews are paused during a Live call.")
            return
        }
        busy = true
        defer { busy = false }
        guard let data = await session.previewVoice(voice, for: current), !Task.isCancelled else { return }
        // A call may have taken ownership while the generated clip loaded.
        guard VoiceNoteCenter.shared.playbackAllowed else {
            session.actionError = String(localized: "Voice previews are paused during a Live call.")
            return
        }
        do {
            player.reset()
            try player.load(data)
            VoiceNoteCenter.shared.claim(player)
            player.play(mode: .spokenAudio)
            guard player.isPlaying else {
                VoiceNoteCenter.shared.release(player)
                session.actionError = "The generated audio could not be played."
                return
            }
        } catch {
            player.reset()
            VoiceNoteCenter.shared.release(player)
            session.actionError = "The generated audio could not be played."
        }
    }

    private func synchronizeForm(with bot: Bot) {
        name = bot.name
        title = bot.title
        description = bot.description
        notifications = bot.notifications
        voice = bot.voice ?? ""
        speakReplies = bot.speakReplies == true
        baseline = ProfileFormSnapshot(bot: bot)
    }
}

private struct ModelChoice: Identifiable {
    let id: String
    let label: String
}

private struct ProfileFormSnapshot {
    var name: String
    var title: String
    var description: String
    var notifications: Bool
    var voice: String
    var speakReplies: Bool

    init(bot: Bot) {
        name = bot.name
        title = bot.title
        description = bot.description
        notifications = bot.notifications
        voice = bot.voice ?? ""
        speakReplies = bot.speakReplies == true
    }
}
