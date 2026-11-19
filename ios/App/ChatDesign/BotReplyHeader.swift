import CompanionCore
import SwiftUI

extension Bot {
    /// The bot as an avatar needs it: the profile only, so a long transcript
    /// or a busy flag does not make every row compare or redraw.
    var avatarFace: Bot {
        var face = self
        face.messages = nil
        face.tasks = nil
        face.projects = nil
        face.busy = nil
        face.activity = nil
        face.waitingOnTeammate = nil
        face.unread = false
        face.activeLeafId = nil
        face.hasMore = nil
        return face
    }
}

/// Avatar, name and time above the first reply of a run.
struct BotReplyHeader: View {
    let name: String
    let date: Date
    let faceBot: Bot?
    let isChief: Bool

    var body: some View {
        HStack(spacing: 8) {
            avatar
            Text(verbatim: name)
                .font(.seira(13, .semibold, relativeTo: .subheadline))
                .foregroundStyle(nameColor)
                .lineLimit(1)
            Text(date, style: .time)
                .font(.seira(12, .regular, relativeTo: .caption))
                .foregroundStyle(Color.seiraInk3)
        }
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder private var avatar: some View {
        if let faceBot {
            SeiraAvatar(bot: faceBot, size: 28)
        } else {
            SeiraInitials(text: String(name.prefix(1)).uppercased(), size: 28, style: .tint)
        }
    }

    private var nameColor: Color {
        isChief ? Color.seiraName : Color.seiraInk
    }
}
