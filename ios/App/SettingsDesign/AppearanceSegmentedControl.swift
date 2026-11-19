import SwiftUI

struct AppearanceSegmentedControl: View {
    @AppStorage(PrefKey.appearance) private var stored = AppearanceChoice.auto.rawValue
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var selected: AppearanceChoice { AppearanceChoice(rawValue: stored) ?? .auto }

    private var selectedIndex: Int {
        AppearanceChoice.allCases.firstIndex(of: selected) ?? 0
    }

    private var thumbAnimation: Animation? {
        reduceMotion ? nil : Animation.seira(SeiraMotion.sheet)
    }

    var body: some View {
        GeometryReader { proxy in
            let segment = (proxy.size.width - 8) / CGFloat(AppearanceChoice.allCases.count)
            ZStack(alignment: .leading) {
                thumb(width: segment)
                HStack(spacing: 0) {
                    ForEach(AppearanceChoice.allCases) { choice in
                        segmentButton(choice)
                    }
                }
                .padding(.horizontal, 4)
            }
        }
        .frame(height: 44)
        .background(Color.seiraSegment, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .feedback(.selection, trigger: stored)
        .accessibilityElement(children: .contain)
    }

    private func thumb(width: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: 12, style: .continuous)
            .fill(Color.seiraThumb)
            .shadow(color: .black.opacity(0.12), radius: 1, x: 0, y: 1)
            .frame(width: width, height: 36)
            .offset(x: 4 + CGFloat(selectedIndex) * width)
            .animation(thumbAnimation, value: stored)
            .accessibilityHidden(true)
    }

    private func segmentButton(_ choice: AppearanceChoice) -> some View {
        let isSelected = choice == selected
        let ink: Color = isSelected ? .seiraInk : .seiraInk2
        return Button {
            stored = choice.rawValue
        } label: {
            HStack(spacing: 7) {
                Image(systemName: choice.symbol)
                    .font(.system(size: 14, weight: .regular))
                    .accessibilityHidden(true)
                Text(choice.title)
                    .font(.seira(14, .medium, relativeTo: .subheadline))
            }
            .foregroundStyle(ink)
            .frame(maxWidth: .infinity, minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
