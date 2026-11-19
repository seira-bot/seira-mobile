import CompanionCore
import SwiftUI

enum HomeRowMetrics {
    static let minHeight: CGFloat = 68
    static let side: CGFloat = 20
    static let avatar: CGFloat = 48
    static let gap: CGFloat = 12
    static let groupFace: CGFloat = 32
    static let nameInset: CGFloat = side + avatar + gap
}

struct HomeRowButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(configuration.isPressed ? Color.seiraTint : Color.clear)
            .animation(Animation.seira(SeiraMotion.fast), value: configuration.isPressed)
    }
}

struct HomeSectionTitle: View {
    let text: Text

    var body: some View {
        text
            .font(.seira(15, .semibold, relativeTo: .body))
            .foregroundStyle(Color.seiraInk)
            .padding(.horizontal, HomeRowMetrics.side)
            .accessibilityAddTraits(.isHeader)
    }
}

struct HomeRoomFaces: View {
    let members: [Bot]

    var body: some View {
        faces
            .frame(width: HomeRowMetrics.avatar, height: HomeRowMetrics.avatar)
            .accessibilityHidden(true)
    }

    @ViewBuilder private var faces: some View {
        if members.count >= 2 {
            stacked(members[0], members[1])
        } else if let only = members.first {
            SeiraAvatar(bot: only, size: HomeRowMetrics.avatar)
        } else {
            Image(systemName: "person.2.fill")
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(Color.seiraInk3)
                .frame(width: HomeRowMetrics.avatar, height: HomeRowMetrics.avatar)
                .background(Color.seiraTint, in: Circle())
        }
    }

    private func stacked(_ first: Bot, _ second: Bot) -> some View {
        ZStack {
            SeiraAvatar(bot: first, size: HomeRowMetrics.groupFace)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            SeiraAvatar(bot: second, size: HomeRowMetrics.groupFace)
                .padding(2.5)
                .background(Color.seiraApp, in: Circle())
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                .offset(x: 2.5, y: 2.5)
        }
    }
}

struct HomeRowAvatar: View {
    let chat: Chat
    let members: [Bot]
    var working = false
    var waiting = false

    var body: some View {
        switch chat {
        case let .bot(bot):
            SeiraAvatar(
                bot: bot,
                size: HomeRowMetrics.avatar,
                ring: working ? .working : .none,
                waiting: waiting
            )
            .accessibilityHidden(true)
        case .room:
            HomeRoomFaces(members: members)
        }
    }
}

struct HomeRowLabels: View {
    let title: String
    var showsCrown = false
    let unread: Bool
    let stamp: String
    let detail: String
    var detailWaiting = false
    var working = false
    var spinnerLabel: LocalizedStringKey = "Working"

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            titleLine
            detailLine
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder private var titleLine: some View {
        if typeSize.isAccessibilitySize {
            VStack(alignment: .leading, spacing: 2) {
                titleText.lineLimit(3).fixedSize(horizontal: false, vertical: true)
                trailing
            }
        } else {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                titleText.lineLimit(1).layoutPriority(1)
                Spacer(minLength: 4)
                trailing
            }
        }
    }

    private var titleText: some View {
        HStack(spacing: 6) {
            Text(verbatim: title)
                .font(.seira(15, unread ? .semibold : .medium, relativeTo: .body))
                .foregroundStyle(Color.seiraInk)
            if showsCrown { ChiefBadge() }
        }
    }

    @ViewBuilder private var trailing: some View {
        if working {
            ProgressView()
                .controlSize(.mini)
                .accessibilityLabel(spinnerLabel)
        } else if !stamp.isEmpty {
            Text(verbatim: stamp)
                .font(.seira(12, relativeTo: .caption))
                .monospacedDigit()
                .foregroundStyle(Color.seiraInk3)
                .fixedSize()
        }
    }

    private var detailLine: some View {
        let color: Color = detailWaiting ? .seiraWarningText : .seiraInk2
        return HStack(spacing: 10) {
            Text(verbatim: detail.isEmpty ? " " : detail)
                .font(.seira(13, relativeTo: .subheadline))
                .foregroundStyle(color)
                .lineLimit(typeSize.isAccessibilitySize ? 3 : 1)
            Spacer(minLength: 0)
            if unread {
                Circle()
                    .fill(Color.seiraAccent)
                    .frame(width: 8, height: 8)
                    .accessibilityLabel("Unread")
            }
        }
    }
}
