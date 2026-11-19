import SwiftUI
import UIKit

enum PairViewfinderPhase {
    case scanning, found
}

extension Color {
    static let seiraViewfinderGreen = Color(hex: "#3ccf8e")
}

struct PairViewfinder: View {
    let phase: PairViewfinderPhase
    let isScanning: Bool
    @ObservedObject var torch: TorchController
    let onPayload: (String) -> Bool

    @StateObject private var camera = PairingCamera()
    @Environment(\.scenePhase) private var scenePhase

    private var bracketColor: Color {
        phase == .scanning ? .white : .seiraViewfinderGreen
    }

    private var showsTorch: Bool {
        isScanning && camera.access == .allowed && camera.scannerReady && torch.isAvailable
    }

    var body: some View {
        ZStack {
            background
            scannerLayer
            PairBrackets(color: bracketColor, scaled: phase != .scanning)
        }
        .overlay(alignment: .bottomTrailing) { torchButton }
        .frame(width: 270, height: 270)
        .clipShape(RoundedRectangle(cornerRadius: 34, style: .continuous))
        .task(id: isScanning) { if isScanning { await camera.resolve() } }
        .onValueChange(of: scenePhase) { newPhase in
            if newPhase == .active {
                if isScanning { Task { await camera.resolve() } }
            } else {
                torch.turnOff()
            }
        }
        .onValueChange(of: isScanning) { scanning in
            if !scanning { torch.turnOff() }
        }
        .onDisappear { torch.turnOff() }
    }

    private var background: some View {
        RadialGradient(
            colors: [Color(hex: "#2a2a30"), Color(hex: "#0d0d0f")],
            center: UnitPoint(x: 0.5, y: 0.3),
            startRadius: 0,
            endRadius: 230
        )
    }

    @ViewBuilder private var scannerLayer: some View {
        if isScanning {
            switch camera.access {
            case .unresolved:
                ProgressView()
                    .tint(.white)
            case .denied:
                PairCameraMessage(
                    title: "Camera access needed",
                    detail: "Allow the camera to scan the code on your computer.",
                    showsSettingsButton: true
                )
            case .allowed:
                allowedContent
            }
        }
    }

    @ViewBuilder private var allowedContent: some View {
        if camera.scannerReady {
            PairingQRScanner(onPayload: onPayload)
        } else {
            PairCameraMessage(
                title: "Scanner unavailable",
                detail: "Use the option below to enter a code instead.",
                showsSettingsButton: false
            )
        }
    }

    @ViewBuilder private var torchButton: some View {
        if showsTorch {
            let fill: Color = torch.isOn ? Color(hex: "#f2f2f3") : Color.white.opacity(0.16)
            let ink: Color = torch.isOn ? Color(hex: "#111113") : .white
            Button {
                torch.toggle()
            } label: {
                Image(systemName: torch.isOn ? "flashlight.on.fill" : "flashlight.off.fill")
                    .font(.system(size: 18, weight: .regular))
                    .foregroundStyle(ink)
            }
            .buttonStyle(SeiraCircleButtonStyle(size: 44, fill: fill))
            .feedback(.selection, trigger: torch.isOn)
            .accessibilityLabel("Flashlight")
            .accessibilityAddTraits(torch.isOn ? .isSelected : [])
            .padding(12)
        }
    }
}

private struct PairCameraMessage: View {
    let title: LocalizedStringKey
    let detail: LocalizedStringKey
    let showsSettingsButton: Bool

    var body: some View {
        VStack(spacing: 8) {
            Text(title)
                .font(.seira(16, .semibold, relativeTo: .headline))
                .foregroundStyle(Color.white)
            Text(detail)
                .font(.seira(13, .regular, relativeTo: .subheadline))
                .foregroundStyle(Color.white.opacity(0.7))
            if showsSettingsButton {
                settingsButton
            }
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, 28)
        .accessibilityElement(children: .contain)
    }

    private var settingsButton: some View {
        Button {
            guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
            UIApplication.shared.open(url)
        } label: {
            Text("Open Settings")
                .font(.seira(14, .medium, relativeTo: .subheadline))
                .foregroundStyle(Color.white)
                .padding(.horizontal, 18)
                .frame(minHeight: 44)
                .background(Color.white.opacity(0.16), in: Capsule())
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .padding(.top, 4)
    }
}
