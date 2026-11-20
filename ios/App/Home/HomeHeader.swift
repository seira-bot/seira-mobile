import SwiftUI

enum HomeGreeting {
    static func title(at date: Date = Date(), calendar: Calendar = .current) -> LocalizedStringKey {
        switch calendar.component(.hour, from: date) {
        case 5...11: return "Good morning"
        case 12...17: return "Good afternoon"
        default: return "Good evening"
        }
    }
}

struct HomeHeader: View {
    let profileName: String
    let openCalendar: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Text(HomeGreeting.title())
                .font(.seira(28, .semibold, relativeTo: .largeTitle))
                .tracking(-0.56)
                .foregroundStyle(Color.seiraInk)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityAddTraits(.isHeader)

            calendarButton
            settingsLink
        }
        .padding(.horizontal, HomeRowMetrics.side)
        .padding(.top, 4)
        .frame(minHeight: 48)
    }

    private var calendarButton: some View {
        Button(action: openCalendar) {
            Image(systemName: "calendar")
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(Color.seiraInk)
        }
        .buttonStyle(SeiraCircleButtonStyle(size: 44, fill: .seiraTint))
        .accessibilityLabel("Routine calendar")
        .accessibilityIdentifier("open-calendar")
    }

    private var settingsLink: some View {
        NavigationLink {
            SettingsView()
        } label: {
            ProfileAvatar(name: profileName, size: 40)
                .frame(width: 44, height: 44)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Settings")
    }
}
