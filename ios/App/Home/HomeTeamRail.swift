import CompanionCore
import SwiftUI

struct HomeTeamMember: Identifiable, Equatable {
    let bot: Bot
    let status: RosterRowStatus

    var id: String { bot.id }

    var spokenLabel: String {
        switch status {
        case .working: return String(localized: "\(bot.name), working")
        case .waitingOnYou: return String(localized: "\(bot.name), needs you")
        case .idle: return bot.name
        }
    }
}

struct HomeTeamRail: View {
    let members: [HomeTeamMember]
    let open: (Bot) -> Void
    let seeAll: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
            rail
        }
    }

    private var header: some View {
        HStack(alignment: .firstTextBaseline) {
            HomeSectionTitle(text: Text("Team"))
            Spacer(minLength: 8)
            Button(action: seeAll) {
                Text("See all \(members.count)")
                    .font(.seira(13, relativeTo: .subheadline))
                    .foregroundStyle(Color.seiraAccentText)
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(.trailing, HomeRowMetrics.side)
        }
        .frame(minHeight: 44)
    }

    private var rail: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .top, spacing: 16) {
                ForEach(members) { member in
                    item(member)
                }
            }
            .padding(.horizontal, HomeRowMetrics.side)
            .padding(.top, 4)
            .padding(.bottom, 4)
        }
    }

    private func item(_ member: HomeTeamMember) -> some View {
        Button { open(member.bot) } label: {
            VStack(spacing: 6) {
                SeiraAvatar(
                    bot: member.bot,
                    size: 60,
                    ring: member.status == .working ? .working : .none,
                    waiting: member.status == .waitingOnYou
                )
                .padding(2)
                Text(verbatim: member.bot.name)
                    .font(.seira(12, relativeTo: .caption))
                    .foregroundStyle(Color.seiraInk2)
                    .lineLimit(1)
            }
            .frame(width: 64)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: member.spokenLabel))
        .accessibilityAddTraits(.isButton)
    }
}
