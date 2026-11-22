import CompanionCore
import PhotosUI
import SwiftUI

/// The one avatar sheet: the Seira mascot, a generated picture, or an uploaded picture.
struct ChangeLookSheet: View {
    let bot: Bot

    @EnvironmentObject private var session: Session
    @Environment(\.dismiss) private var dismiss
    @StateObject private var model: ChangeLookModel
    @State private var tab: AvatarTab
    @State private var photo: PhotosPickerItem?
    @State private var prompt = ""
    @State private var working = false
    @State private var problem: String?
    @State private var imageReady = false
    @AccessibilityFocusState private var titleFocused: Bool

    init(bot: Bot) {
        self.bot = bot
        _model = StateObject(wrappedValue: ChangeLookModel(original: SeiraLook(bot: bot)))
        _tab = State(initialValue: AvatarSheetRules.defaultTab(for: bot))
    }

    private var current: Bot { session.state.bot(bot.id)?.projected(forThread: bot.threadId) ?? bot }
    private var connected: Bool { session.status == .live }
    private var showsPicture: Bool { AvatarSheetRules.showsPicture(current) }
    private var canSave: Bool {
        connected && (model.changed || showsPicture) && !model.saving
    }
    private var canGenerate: Bool {
        imageReady && connected && !working
            && !prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            content
            if tab == .seira { footer }
        }
        .background(Color.seiraSheet, in: RoundedRectangle(cornerRadius: 34, style: .continuous))
        .padding(8)
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
        .modifier(ChangeLookSheetChrome())
        .accessibilityAction(.escape) { dismiss() }
        .onAppear { titleFocused = true }
        .task { await loadImageSetup() }
        .onValueChange(of: photo) { item in
            guard let item else { return }
            Task { await upload(item) }
        }
        .onValueChange(of: tab) { _ in problem = nil }
    }

    private var content: some View {
        ScrollView {
            VStack(spacing: 20) {
                preview
                tabPicker
                switch tab {
                case .seira: seiraTab
                case .generate: generateTab
                case .upload: uploadTab
                }
                if let problem {
                    Text(verbatim: problem)
                        .font(.seira(13, relativeTo: .footnote))
                        .foregroundStyle(Color.seiraDangerText)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 16)
                }
            }
            .padding(.bottom, 16)
        }
    }

    // MARK: Header

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Avatar")
                    .font(.seira(20, .semibold, relativeTo: .title3))
                    .foregroundStyle(Color.seiraInk)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityFocused($titleFocused)
                Text(verbatim: bot.name)
                    .font(.seira(14, relativeTo: .subheadline))
                    .foregroundStyle(Color.seiraInk2)
                    .lineLimit(1)
            }
            .padding(.top, 8)
            Spacer(minLength: 8)
            closeButton
        }
        .padding(.leading, 20)
        .padding(.trailing, 12)
        .padding(.top, 12)
        .padding(.bottom, 8)
    }

    private var closeButton: some View {
        Button { dismiss() } label: {
            Image(systemName: "xmark")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Color.seiraInk2)
                .frame(width: 32, height: 32)
                .background(Circle().fill(Color.seiraControl))
                .frame(width: 44, height: 44)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Close")
    }

    // MARK: Preview and tabs

    @ViewBuilder private var preview: some View {
        if tab == .seira {
            Button { model.celebrate() } label: {
                ZStack {
                    Circle().fill(SeiraIdentity.plate(hex: model.colorHex))
                    SeiraCharacter(look: model.look, size: 184, mood: model.mood, motion: .sway)
                }
                .frame(width: 216, height: 216)
                .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Look preview")
            .padding(.top, 4)
        } else {
            BotAvatarView(bot: current, size: 216, state: .happy)
                .accessibilityLabel("Look preview")
                .padding(.top, 4)
        }
    }

    private var tabPicker: some View {
        Picker("Avatar", selection: $tab) {
            ForEach(AvatarTab.allCases) { item in
                Text(title(of: item)).tag(item)
            }
        }
        .pickerStyle(.segmented)
        .padding(.horizontal, 16)
    }

    private func title(of tab: AvatarTab) -> LocalizedStringKey {
        switch tab {
        case .seira: return "Seira"
        case .generate: return "Generate"
        case .upload: return "Upload"
        }
    }

    // MARK: Seira tab

    private var seiraTab: some View {
        VStack(spacing: 20) {
            chips
            ChangeLookPanel(model: model)
                .padding(.horizontal, 16)
                .seiraAnimation(0.18, value: model.look)
        }
    }

    private var chips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(ChangeLookCategory.allCases) { category in
                    ChangeLookChip(title: Text(category.title), selected: model.category == category) {
                        model.category = category
                    }
                }
            }
            .padding(.horizontal, 16)
        }
    }

    // MARK: Generate tab

    private var generateTab: some View {
        VStack(alignment: .leading, spacing: 12) {
            TextField("Describe the picture", text: $prompt, axis: .vertical)
                .lineLimit(2...5)
                .padding(12)
                .background(Color.seiraControl, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .disabled(working)
            Button { Task { await generate() } } label: {
                generateLabel
                    .font(.seira(16, .semibold))
                    .frame(maxWidth: .infinity, minHeight: 52)
            }
            .buttonStyle(SeiraPrimaryButtonStyle(height: 52))
            .disabled(!canGenerate)
            .opacity(canGenerate || working ? 1 : 0.4)
            if !imageReady {
                Text("Set up image generation in Seira on your computer to use this.")
                    .font(.seira(13, relativeTo: .footnote))
                    .foregroundStyle(Color.seiraInk2)
            }
        }
        .padding(.horizontal, 16)
    }

    @ViewBuilder private var generateLabel: some View {
        if working {
            ProgressView().tint(Color.seiraOnPrimary)
        } else {
            Text("Generate")
        }
    }

    // MARK: Upload tab

    private var uploadTab: some View {
        VStack(alignment: .leading, spacing: 12) {
            PhotosPicker(selection: $photo, matching: .images) {
                Text("Choose a picture")
                    .font(.seira(16, .semibold))
                    .foregroundStyle(Color.seiraOnPrimary)
                    .frame(maxWidth: .infinity, minHeight: 52)
                    .background(Color.seiraPrimary, in: Capsule())
            }
            .disabled(working || !connected)
            .opacity(working || !connected ? 0.4 : 1)
            Text("PNG, JPEG, GIF, or WebP, up to 10 MB.")
                .font(.seira(13, relativeTo: .footnote))
                .foregroundStyle(Color.seiraInk2)
            if AvatarSheetRules.hasHiddenPicture(current) {
                Button { Task { await usePicture() } } label: {
                    Text("Use this picture")
                        .font(.seira(16, .semibold))
                        .frame(maxWidth: .infinity, minHeight: 52)
                }
                .buttonStyle(SeiraControlButtonStyle(height: 52))
                .disabled(working || !connected)
            }
            if current.avatarUrl != nil {
                Button { Task { await removePicture() } } label: {
                    Text("Remove picture")
                        .font(.seira(16, .semibold))
                        .foregroundStyle(Color.seiraDangerText)
                        .frame(maxWidth: .infinity, minHeight: 52)
                }
                .buttonStyle(SeiraControlButtonStyle(height: 52))
                .disabled(working || !connected)
            }
        }
        .padding(.horizontal, 16)
    }

    // MARK: Footer (Seira tab)

    private var footer: some View {
        VStack(spacing: 10) {
            if let note = footerNote {
                note
                    .font(.seira(13, relativeTo: .footnote))
                    .foregroundStyle(footerNoteInk)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            HStack(spacing: 10) {
                surpriseButton
                saveButton
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 10)
        .padding(.bottom, 16)
        .seiraAnimation(SeiraMotion.fast, value: model.saveFailed)
    }

    private var footerNote: Text? {
        if model.saveFailed { return Text("Couldn't save. Try again.") }
        if !connected { return Text("Connect to your computer to save.") }
        return nil
    }

    private var footerNoteInk: Color {
        model.saveFailed ? .seiraDangerText : .seiraInk2
    }

    private var surpriseButton: some View {
        Button { model.surprise() } label: {
            Text("Surprise me")
                .font(.seira(16, .semibold))
                .frame(maxWidth: .infinity, minHeight: 52)
        }
        .buttonStyle(SeiraControlButtonStyle(height: 52))
        .disabled(model.saving)
    }

    private var saveButton: some View {
        Button(action: save) {
            saveLabel
        }
        .buttonStyle(SeiraPrimaryButtonStyle(height: 52))
        .disabled(!canSave)
        .opacity(canSave || model.saving ? 1 : 0.4)
        .accessibilityHint(saveHint)
    }

    @ViewBuilder private var saveLabel: some View {
        if model.saving {
            ProgressView()
                .tint(Color.seiraOnPrimary)
                .accessibilityLabel("Saving")
        } else {
            Text("Save")
        }
    }

    private var saveHint: Text {
        if !connected { return Text("Connect to your computer to save.") }
        if !model.changed && !showsPicture { return Text("No changes yet") }
        return Text(verbatim: "")
    }

    // MARK: Actions

    private func save() {
        guard canSave else { return }
        model.saving = true
        model.saveFailed = false
        let look = model.look
        Task { @MainActor in
            var saved = await session.saveLook(look, from: model.original, for: bot)
            if saved, AvatarSheetRules.showsPicture(current) {
                saved = await session.updateProfile(BotProfilePatch(avatarCrop: .mascot), for: current) != nil
                session.actionError = nil
            }
            model.saving = false
            if saved {
                Haptics.success()
                dismiss()
            } else {
                model.saveFailed = true
            }
        }
    }

    private func loadImageSetup() async {
        guard session.canAdminister else { return }
        let status = await session.configStatus()
        imageReady = status?.imageGen?.configured == true
    }

    /// Runs one picture call. The session reports a failure in its shared alert, which cannot
    /// show over this sheet, so the message is taken from there and shown under the tabs.
    private func perform(_ work: () async -> Bool) async {
        working = true
        problem = nil
        session.actionError = nil
        let succeeded = await work()
        if !succeeded {
            problem = session.actionError ?? String(localized: "Couldn't change the avatar. Try again.")
            session.actionError = nil
        }
        working = false
    }

    private func upload(_ item: PhotosPickerItem) async {
        defer { photo = nil }
        await perform {
            guard let data = try? await item.loadTransferable(type: Data.self),
                  let mime = AvatarSheetRules.imageMIME(data)
            else {
                session.actionError = String(localized: "Choose a PNG, JPEG, GIF, or WebP image.")
                return false
            }
            if data.count > AvatarSheetRules.maxUploadBytes {
                session.actionError = String(localized: "That image is larger than 10 MB.")
                return false
            }
            return await session.uploadAvatar(data, mime: mime, for: current, crop: .circle) != nil
        }
    }

    private func usePicture() async {
        await perform {
            await session.updateProfile(BotProfilePatch(avatarCrop: .circle), for: current) != nil
        }
    }

    private func removePicture() async {
        await perform {
            await session.updateProfile(BotProfilePatch(avatarUrl: .clear, avatarCrop: .mascot), for: current) != nil
        }
    }

    private func generate() async {
        await perform {
            guard let generated = await session.generateAvatar(
                prompt: AvatarSheetRules.trimmedPrompt(prompt),
                for: current
            ) else { return false }
            if generated.avatarCrop != .circle {
                _ = await session.updateProfile(BotProfilePatch(avatarCrop: .circle), for: generated)
                session.actionError = nil
            }
            return true
        }
    }
}
