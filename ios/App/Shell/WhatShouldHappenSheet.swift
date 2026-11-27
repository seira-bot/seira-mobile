import CompanionCore
import SwiftUI

/// "What should happen?": a sentence and a bot, then the routine form opens
/// with both filled in. `onSetUp` gets the sentence (empty for "Set it up
/// yourself") and the chosen bot.
struct WhatShouldHappenSheet: View {
    let onSetUp: (_ prompt: String, _ botId: String) -> Void

    @EnvironmentObject private var session: Session
    @Environment(\.dismiss) private var dismiss
    @State private var prompt = ""
    @State private var chosenId = ""
    @State private var picking = false
    @FocusState private var focused: Bool

    private var bots: [Bot] { session.state.bots.filter { $0.hidden != true } }
    private var chosen: Bot? { bots.first { $0.id == chosenId } ?? WhatShouldHappen.defaultAssistant(in: bots) }
    private var canSetUp: Bool { WhatShouldHappen.canSetUp(prompt) && chosen != nil }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Describe it")
                            .font(.seira(13, .medium, relativeTo: .caption))
                            .foregroundStyle(Color.seiraInk2)
                        TextField("Every morning at 9, summarize my inbox", text: $prompt, axis: .vertical)
                            .lineLimit(3...6)
                            .focused($focused)
                            .padding(12)
                            .background(Color.seiraS1, in: RoundedRectangle(cornerRadius: 14))
                            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.seiraHairline))
                            .accessibilityIdentifier("what-should-happen-prompt")
                    }
                    if let chosen {
                        Button {
                            Haptics.selection()
                            picking = true
                        } label: {
                            HStack(spacing: 12) {
                                SeiraAvatar(bot: chosen, size: 36)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(verbatim: chosen.name)
                                        .font(.seira(15, .medium, relativeTo: .body))
                                        .foregroundStyle(Color.seiraInk)
                                    Text("Who should do it?")
                                        .font(.seira(13, relativeTo: .caption))
                                        .foregroundStyle(Color.seiraInk3)
                                }
                                Spacer(minLength: 0)
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(Color.seiraInk3)
                            }
                            .frame(minHeight: 56)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(Text("\(chosen.name), who should do it?"))
                        .accessibilityHint(Text("Tap to change."))
                        .accessibilityIdentifier("what-should-happen-bot")
                    }
                    Button {
                        Haptics.selection()
                        if let chosen { onSetUp("", chosen.id) }
                    } label: {
                        Label("Set it up yourself", systemImage: "slider.horizontal.3")
                            .font(.seira(15, .medium, relativeTo: .body))
                            .foregroundStyle(Color.seiraInk)
                            .frame(maxWidth: .infinity, minHeight: 52, alignment: .leading)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(chosen == nil)
                    .accessibilityIdentifier("what-should-happen-yourself")
                    Button {
                        Haptics.selection()
                        if let chosen { onSetUp(prompt.trimmingCharacters(in: .whitespacesAndNewlines), chosen.id) }
                    } label: {
                        Text("Set it up")
                    }
                    .buttonStyle(SeiraPrimaryButtonStyle())
                    .disabled(!canSetUp)
                    .opacity(canSetUp ? 1 : 0.4)
                    .accessibilityIdentifier("what-should-happen-go")
                    if !canSetUp {
                        Text("Describe what should happen first.")
                            .font(.seira(13, relativeTo: .caption))
                            .foregroundStyle(Color.seiraInk3)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
            }
            .background(Color.seiraSheet)
            .navigationTitle("What should happen?")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .navigationDestination(isPresented: $picking) {
                WhoDoesItList(bots: bots, selectedId: chosen?.id) { bot in
                    Haptics.selection()
                    chosenId = bot.id
                    picking = false
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

/// The bot list: name and role, a check on the current one.
private struct WhoDoesItList: View {
    let bots: [Bot]
    let selectedId: String?
    let onPick: (Bot) -> Void

    var body: some View {
        List(bots) { bot in
            Button { onPick(bot) } label: {
                HStack(spacing: 12) {
                    SeiraAvatar(bot: bot, size: 36)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(verbatim: bot.name)
                            .font(.seira(15, .medium, relativeTo: .body))
                            .foregroundStyle(Color.seiraInk)
                        if !bot.title.isEmpty {
                            Text(verbatim: bot.title)
                                .font(.seira(13, relativeTo: .caption))
                                .foregroundStyle(Color.seiraInk3)
                        }
                    }
                    Spacer(minLength: 0)
                    if bot.id == selectedId {
                        Image(systemName: "checkmark").foregroundStyle(Color.seiraInk)
                    }
                }
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .listRowBackground(Color.seiraS1)
            .accessibilityAddTraits(bot.id == selectedId ? .isSelected : [])
        }
        .scrollContentBackground(.hidden)
        .background(Color.seiraSheet)
        .navigationTitle("Who should do it?")
        .navigationBarTitleDisplayMode(.inline)
    }
}
