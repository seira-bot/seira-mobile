import CompanionCore
import SwiftUI

/// Lets the person choose a bot's character, hair, colour, face and extras, with a live preview.
struct ChangeLookSheet: View {
    let bot: Bot

    @EnvironmentObject private var session: Session
    @Environment(\.dismiss) private var dismiss
    @StateObject private var model: ChangeLookModel
    @AccessibilityFocusState private var titleFocused: Bool

    init(bot: Bot) {
        self.bot = bot
        _model = StateObject(wrappedValue: ChangeLookModel(original: SeiraLook(bot: bot)))
    }

    private var connected: Bool { session.status == .live }
    private var canSave: Bool { connected && model.changed && !model.saving }

    var body: some View {
        VStack(spacing: 0) {
            header
            content
            footer
        }
        .background(Color.seiraSheet, in: RoundedRectangle(cornerRadius: 34, style: .continuous))
        .padding(8)
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
        .modifier(ChangeLookSheetChrome())
        .accessibilityAction(.escape) { dismiss() }
        .onAppear { titleFocused = true }
    }

    private var content: some View {
        ScrollView {
            VStack(spacing: 20) {
                preview
                chips
                ChangeLookPanel(model: model)
                    .padding(.horizontal, 16)
                    .seiraAnimation(0.18, value: model.look)
            }
            .padding(.bottom, 16)
        }
        .overlay(alignment: .bottom) { toast }
        .seiraAnimation(SeiraMotion.fast, value: model.toastShown)
    }

    // MARK: Header

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Change look")
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

    // MARK: Preview

    private var preview: some View {
        Button { model.celebrate() } label: {
            ZStack {
                Circle().fill(SeiraIdentity.plate(hex: model.colorHex))
                SeiraCharacter(look: model.look, size: 200, mood: model.mood, motion: .sway)
            }
            .frame(width: 232, height: 232)
            .modifier(ChangeLookShake(travel: CGFloat(model.shakes)))
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Look preview")
        .padding(.top, 4)
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

    @ViewBuilder private var toast: some View {
        if model.toastShown {
            Text("Up to 3 extras. Remove one first.")
                .font(.seira(14, .medium, relativeTo: .subheadline))
                .foregroundStyle(Color.seiraOnInverse)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Color.seiraInverse, in: Capsule())
                .padding(.bottom, 8)
                .transition(.opacity)
        }
    }

    // MARK: Footer

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
        if !model.changed { return Text("No changes yet") }
        return Text(verbatim: "")
    }

    private func save() {
        guard canSave else { return }
        model.saving = true
        model.saveFailed = false
        let look = model.look
        Task { @MainActor in
            let saved = await session.saveLook(look, from: model.original, for: bot)
            model.saving = false
            if saved {
                Haptics.success()
                dismiss()
            } else {
                model.saveFailed = true
            }
        }
    }
}
