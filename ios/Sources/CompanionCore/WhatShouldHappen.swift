import Foundation

/// The rules behind the "What should happen?" sheet.
public enum WhatShouldHappen {
    /// Seira when it is there, otherwise the first bot.
    public static func defaultAssistant(in bots: [Bot]) -> Bot? {
        bots.first { $0.name.caseInsensitiveCompare("Seira") == .orderedSame } ?? bots.first
    }

    /// "Set it up" waits for a sentence.
    public static func canSetUp(_ prompt: String) -> Bool {
        !prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}
