import CompanionCore
import Foundation

enum AvatarTab: CaseIterable, Identifiable {
    case seira, generate, upload

    var id: Self { self }
}

enum AvatarSheetRules {
    static let maxUploadBytes = 10 * 1_024 * 1_024
    static let maxPromptLength = 400

    static func crop(of bot: Bot) -> AvatarCrop { bot.avatarCrop ?? .mascot }

    static func showsPicture(_ bot: Bot) -> Bool {
        bot.avatarUrl != nil && crop(of: bot) != .mascot
    }

    static func hasHiddenPicture(_ bot: Bot) -> Bool {
        bot.avatarUrl != nil && crop(of: bot) == .mascot
    }

    static func defaultTab(for bot: Bot) -> AvatarTab {
        showsPicture(bot) ? .upload : .seira
    }

    static func imageMIME(_ data: Data) -> String? {
        let bytes = [UInt8](data.prefix(12))
        if bytes.starts(with: [0x89, 0x50, 0x4e, 0x47]) { return "image/png" }
        if bytes.starts(with: [0xff, 0xd8, 0xff]) { return "image/jpeg" }
        if bytes.starts(with: Array("GIF8".utf8)) { return "image/gif" }
        if bytes.count >= 12,
           String(bytes: bytes[0..<4], encoding: .ascii) == "RIFF",
           String(bytes: bytes[8..<12], encoding: .ascii) == "WEBP" { return "image/webp" }
        return nil
    }

    static func trimmedPrompt(_ text: String) -> String {
        String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(maxPromptLength))
    }
}
