import CompanionCore
import SwiftUI

enum HomeTab: String, CaseIterable, Hashable {
    case chats, inbox, automations

    var title: LocalizedStringKey {
        switch self {
        case .chats: "Chats"
        case .inbox: "Inbox"
        case .automations: "Automations"
        }
    }

    var symbol: String {
        switch self {
        case .chats: "bubble.left.and.bubble.right"
        case .inbox: "tray"
        case .automations: "clock.arrow.circlepath"
        }
    }
}

enum HomeRoute: Hashable {
    case search
    case settings
    case teamMap
    case departmentMemory
    case computers
    case botProfiles
}

struct HomeTopBar: View {
    let profileName: String
    let needsYou: Bool
    let openMenu: () -> Void
    let openSeira: () -> Void
    let openProfile: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button(action: openMenu) {
                Image(systemName: "line.3.horizontal")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(Color.seiraInk)
            }
            .buttonStyle(SeiraCircleButtonStyle(size: 48, fill: .seiraS1))
            .accessibilityLabel("Menu")
            .accessibilityIdentifier("home-menu")

            Spacer(minLength: 0)

            Button(action: openSeira) { seiraFace }
                .buttonStyle(SeiraCircleButtonStyle(size: 48, fill: .seiraS1))
                .accessibilityLabel(needsYou ? "Seira, a decision is waiting" : "Seira")
                .accessibilityIdentifier("home-seira")

            Spacer(minLength: 0)

            Button(action: openProfile) {
                ProfileAvatar(name: profileName, size: 40)
                    .frame(width: 48, height: 48)
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Settings")
            .accessibilityIdentifier("home-profile")
        }
        .padding(.horizontal, 16)
        .padding(.top, 4)
        .frame(minHeight: 56)
    }

    private var seiraFace: some View {
        ZStack(alignment: .topTrailing) {
            SeiraMark(size: 26)
                .colorMultiply(needsYou ? Color.seiraWarn : Color.white)
            if needsYou {
                Circle()
                    .fill(Color.seiraWarn)
                    .frame(width: 10, height: 10)
                    .overlay(Circle().stroke(Color.seiraS1, lineWidth: 2))
                    .offset(x: 4, y: -4)
            }
        }
    }
}

struct HomeTabBar: View {
    @Binding var tab: HomeTab
    let inboxCount: Int

    var body: some View {
        HStack(spacing: 0) {
            ForEach(HomeTab.allCases, id: \.self) { item in
                Button {
                    Haptics.selection()
                    tab = item
                } label: {
                    label(for: item)
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("tab-\(item.rawValue)")
                .accessibilityAddTraits(tab == item ? .isSelected : [])
            }
        }
        .padding(.horizontal, 12)
        .padding(.top, 8)
        .padding(.bottom, 4)
        .frame(maxWidth: .infinity)
        .background(Color.seiraS1.ignoresSafeArea(edges: .bottom))
        .overlay(alignment: .top) { Rectangle().fill(Color.seiraLine).frame(height: 1) }
    }

    private func label(for item: HomeTab) -> some View {
        let selected = tab == item
        let tint: Color = selected ? .seiraAccFg : .seiraDim
        return VStack(spacing: 3) {
            ZStack(alignment: .topTrailing) {
                Image(systemName: item.symbol)
                    .font(.system(size: 20, weight: .medium))
                    .frame(height: 24)
                if item == .inbox, inboxCount > 0 { badge }
            }
            Text(item.title)
                .font(.seira(11, .medium, relativeTo: .caption2))
        }
        .foregroundStyle(tint)
        .frame(maxWidth: .infinity, minHeight: 48)
        .contentShape(Rectangle())
    }

    private var badge: some View {
        Text(verbatim: inboxCount > 99 ? "99+" : "\(inboxCount)")
            .font(.system(size: 10, weight: .bold))
            .foregroundStyle(Color.seiraOnAcc)
            .padding(.horizontal, 5)
            .frame(minWidth: 16, minHeight: 16)
            .background(Color.seiraAcc, in: Capsule())
            .offset(x: 12, y: -4)
            .accessibilityLabel("\(inboxCount) waiting")
    }
}

struct HomeDrawer: View {
    let name: String
    let close: () -> Void
    let select: (HomeRoute) -> Void

    static let width: CGFloat = 308

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                ProfileAvatar(name: name, size: 40)
                Text(verbatim: name)
                    .font(.seira(17, .semibold, relativeTo: .headline))
                    .foregroundStyle(Color.seiraInk)
                    .lineLimit(1)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 20)
            .padding(.top, 24)
            .padding(.bottom, 16)

            item("Team map", symbol: "point.3.connected.trianglepath.dotted", route: .teamMap)
            item("Department memory", symbol: "brain", route: .departmentMemory)
            item("Computers", symbol: "desktopcomputer", route: .computers)
            item("Bot profiles", symbol: "person.crop.square", route: .botProfiles)
            item("Settings", symbol: "gearshape", route: .settings)
            Spacer(minLength: 0)
        }
        .frame(width: Self.width, alignment: .leading)
        .frame(maxHeight: .infinity, alignment: .top)
        .background(Color.seiraS1.ignoresSafeArea())
        .accessibilityIdentifier("home-drawer")
    }

    private func item(_ title: LocalizedStringKey, symbol: String, route: HomeRoute) -> some View {
        Button {
            Haptics.selection()
            close()
            select(route)
        } label: {
            HStack(spacing: 14) {
                Image(systemName: symbol)
                    .font(.system(size: 18, weight: .medium))
                    .frame(width: 26)
                Text(title)
                    .font(.seira(16, .medium, relativeTo: .body))
                Spacer(minLength: 0)
            }
            .foregroundStyle(Color.seiraInk)
            .padding(.horizontal, 20)
            .frame(minHeight: 52)
            .contentShape(Rectangle())
        }
        .buttonStyle(HomeRowButtonStyle())
    }
}

struct HomeFilterChips: View {
    @Binding var filter: HomeChatFilter

    var body: some View {
        HStack(spacing: 8) {
            chip(.all, "All")
            chip(.bots, "Bots")
            chip(.groups, "Groups")
            Spacer(minLength: 0)
        }
        .padding(.horizontal, HomeRowMetrics.side)
    }

    private func chip(_ value: HomeChatFilter, _ title: LocalizedStringKey) -> some View {
        let selected = filter == value
        let fill: Color = selected ? .seiraInv : .seiraS1
        let ink: Color = selected ? .seiraOnInv : .seiraFg
        return Button {
            Haptics.selection()
            filter = value
        } label: {
            Text(title)
                .font(.seira(14, .medium, relativeTo: .subheadline))
                .foregroundStyle(ink)
                .padding(.horizontal, 16)
                .frame(minHeight: 40)
                .background(fill, in: Capsule())
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// The side drawer over the home screen. The main layer slides right and
/// dims while the drawer is open.
struct HomeDrawerLayer<Content: View>: View {
    @Binding var open: Bool
    let name: String
    let select: (HomeRoute) -> Void
    @ViewBuilder let content: () -> Content

    var body: some View {
        ZStack(alignment: .leading) {
            content()
                .offset(x: open ? HomeDrawer.width : 0)
                .overlay { scrim }
            HomeDrawer(name: name, close: { open = false }, select: select)
                .offset(x: open ? 0 : -HomeDrawer.width)
                .allowsHitTesting(open)
                .accessibilityHidden(!open)
        }
        .seiraAnimation(SeiraMotion.slow, value: open)
    }

    @ViewBuilder private var scrim: some View {
        if open {
            Color.seiraScrim
                .contentShape(Rectangle())
                .onTapGesture { open = false }
                .accessibilityLabel("Close menu")
                .accessibilityAddTraits(.isButton)
        }
    }
}
