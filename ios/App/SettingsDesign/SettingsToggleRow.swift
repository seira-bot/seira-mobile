import SwiftUI

struct SettingsToggleRow: View {
    let symbol: String
    let title: LocalizedStringKey
    let subtitle: LocalizedStringKey
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            HStack(spacing: 14) {
                SettingsRowIcon(symbol: symbol)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.seira(15, .medium))
                        .foregroundStyle(Color.seiraInk)
                    Text(subtitle)
                        .font(.seira(12, .regular, relativeTo: .caption))
                        .foregroundStyle(Color.seiraInk3)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .toggleStyle(SeiraToggleStyle())
        .padding(.leading, 16)
        .padding(.trailing, 12)
        .padding(.vertical, 10)
        .frame(minHeight: 64)
    }
}
