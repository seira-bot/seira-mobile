import SwiftUI

struct SettingsSectionHeader: View {
    let title: LocalizedStringKey

    var body: some View {
        Text(title)
            .font(.seira(13, .medium, relativeTo: .subheadline))
            .foregroundStyle(Color.seiraInk3)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 28)
            .padding(.horizontal, 4)
            .padding(.bottom, 8)
            .accessibilityAddTraits(.isHeader)
    }
}

struct SettingsCard<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        VStack(spacing: 0) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .seiraCard(radius: 22)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}

struct SettingsDivider: View {
    var body: some View {
        Rectangle()
            .fill(Color.seiraHairline)
            .frame(height: 1)
            .padding(.leading, 50)
            .accessibilityHidden(true)
    }
}

struct SettingsCaption: View {
    let text: Text

    var body: some View {
        text
            .font(.seira(13, .regular, relativeTo: .subheadline))
            .foregroundStyle(Color.seiraInk2)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 8)
            .padding(.horizontal, 4)
    }
}

struct SettingsRowButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(configuration.isPressed ? Color.seiraHairline : Color.clear)
            .contentShape(Rectangle())
    }
}

struct SettingsRowIcon: View {
    let symbol: String

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: 17, weight: .regular))
            .foregroundStyle(Color.seiraInk2)
            .frame(width: 22, height: 22)
            .accessibilityHidden(true)
    }
}

struct SettingsChevron: View {
    var body: some View {
        Image(systemName: "chevron.right")
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(Color.seiraInk3)
            .accessibilityHidden(true)
    }
}

struct SettingsLinkLabel: View {
    let symbol: String
    let title: LocalizedStringKey
    var value: Text?

    var body: some View {
        HStack(spacing: 14) {
            SettingsRowIcon(symbol: symbol)
            Text(title)
                .font(.seira(16, .medium))
                .foregroundStyle(Color.seiraInk)
                .frame(maxWidth: .infinity, alignment: .leading)
            valueText
            SettingsChevron()
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 56)
        .contentShape(Rectangle())
    }

    @ViewBuilder private var valueText: some View {
        if let value {
            value
                .font(.seira(15))
                .foregroundStyle(Color.seiraInk2)
        }
    }
}

struct SettingsMenuRow<Options: View>: View {
    let symbol: String
    let title: LocalizedStringKey
    let value: Text
    @ViewBuilder var options: Options

    var body: some View {
        Menu {
            options
        } label: {
            label
        }
        .buttonStyle(SettingsRowButtonStyle())
    }

    private var label: some View {
        HStack(spacing: 14) {
            SettingsRowIcon(symbol: symbol)
            Text(title)
                .font(.seira(16, .medium))
                .foregroundStyle(Color.seiraInk)
                .frame(maxWidth: .infinity, alignment: .leading)
            value
                .font(.seira(15))
                .foregroundStyle(Color.seiraInk2)
            Image(systemName: "chevron.up.chevron.down")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color.seiraInk3)
                .accessibilityHidden(true)
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 56)
        .contentShape(Rectangle())
    }
}

struct SettingsBackButton: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Button {
            dismiss()
        } label: {
            Image(systemName: "chevron.left")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Color.seiraInk)
        }
        .buttonStyle(SeiraCircleButtonStyle(size: 44, fill: .seiraTint))
        .accessibilityLabel("Back")
    }
}

struct SettingsFooter: View {
    private var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    }

    var body: some View {
        VStack(spacing: 8) {
            SeiraMark(size: 28)
                .accessibilityHidden(true)
            Text(verbatim: "Seira \(version)")
                .font(.seira(13, .regular, relativeTo: .subheadline))
                .foregroundStyle(Color.seiraInk3)
                .monospacedDigit()
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 36)
        .padding(.bottom, 56)
        .accessibilityElement(children: .combine)
    }
}

struct SettingsListChrome: ViewModifier {
    func body(content: Content) -> some View {
        content
            .scrollContentBackground(.hidden)
            .background(Color.seiraApp.ignoresSafeArea())
            .font(.seira(16))
            .tint(Color.seiraInk)
    }
}

extension View {
    func settingsListChrome() -> some View {
        modifier(SettingsListChrome())
    }
}
