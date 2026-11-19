import SwiftUI

struct PairBackButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "chevron.left")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Color.seiraInk)
        }
        .buttonStyle(SeiraCircleButtonStyle(size: 44, fill: .seiraTint))
        .accessibilityLabel("Back")
    }
}

struct PairHeader: View {
    var body: some View {
        VStack(spacing: 0) {
            SeiraMark(size: 36)
                .accessibilityHidden(true)
            Text("Connect your computer")
                .font(.seira(24, .semibold, relativeTo: .title2))
                .tracking(-0.36)
                .foregroundStyle(Color.seiraInk)
                .padding(.top, 14)
                .accessibilityAddTraits(.isHeader)
            Text("Open Seira on your computer and show the pairing code. Then point your camera at it.")
                .font(.seira(15, .regular, relativeTo: .body))
                .foregroundStyle(Color.seiraInk2)
                .padding(.top, 8)
        }
        .multilineTextAlignment(.center)
        .fixedSize(horizontal: false, vertical: true)
        .padding(.horizontal, 32)
    }
}

struct PairStatusLine: View {
    let text: Text
    let isPositive: Bool

    private var dotColor: Color { isPositive ? .seiraSuccess : .seiraInk3 }

    var body: some View {
        HStack(spacing: 8) {
            StatusDot(color: dotColor)
            text
                .font(.seira(14, .regular, relativeTo: .subheadline))
                .foregroundStyle(Color.seiraInk2)
        }
        .frame(minHeight: 20)
        .accessibilityElement(children: .combine)
    }
}

struct PairBottomCard<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        VStack(spacing: 0) { content }
            .padding(16)
            .frame(maxWidth: .infinity)
            .seiraCard(radius: 30)
            .padding(.horizontal, 12)
            .padding(.bottom, 8)
    }
}

struct PairComputerRow: View {
    let symbol: String
    let title: Text
    let subtitle: Text
    var fill: Color = .seiraApp
    var ink: Color = .seiraInk

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: symbol)
                .font(.system(size: 20, weight: .regular))
                .foregroundStyle(ink)
                .frame(width: 48, height: 48)
                .background(fill, in: Circle())
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                title
                    .font(.seira(17, .semibold, relativeTo: .headline))
                    .foregroundStyle(Color.seiraInk)
                    .lineLimit(2)
                subtitle
                    .font(.seira(14, .regular, relativeTo: .subheadline))
                    .foregroundStyle(Color.seiraInk2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 2)
        .accessibilityElement(children: .combine)
    }
}

struct PairPrimaryButton: View {
    let title: LocalizedStringKey
    var isBusy = false
    var isDisabled = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
        }
        .buttonStyle(SeiraPrimaryButtonStyle())
        .opacity(isBusy || isDisabled ? 0.6 : 1)
        .disabled(isBusy || isDisabled)
        .padding(.top, 16)
    }
}

struct PairTextButton: View {
    let title: LocalizedStringKey
    var isDisabled = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.seira(15, .medium, relativeTo: .body))
                .foregroundStyle(Color.seiraInk2)
                .frame(maxWidth: .infinity, minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(isDisabled)
        .padding(.top, 4)
    }
}

struct PairErrorBanner: View {
    let message: String

    var body: some View {
        Label {
            Text(message)
                .fixedSize(horizontal: false, vertical: true)
        } icon: {
            Image(systemName: "exclamationmark.triangle.fill")
        }
        .font(.seira(14, .regular, relativeTo: .subheadline))
        .foregroundStyle(Color.seiraDangerText)
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .seiraCard(radius: 18)
        .accessibilityElement(children: .combine)
    }
}

struct PairSurfaceCard<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) { content }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .seiraCard(radius: 22)
    }
}
