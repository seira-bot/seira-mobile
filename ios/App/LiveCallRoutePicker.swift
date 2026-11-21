import AVFoundation
import AVKit
import SwiftUI
import UIKit

/// Follows the audio route and says whether the sound goes somewhere other
/// than the phone's own receiver or speaker.
@MainActor
final class LiveCallRouteMonitor: ObservableObject {
    @Published private(set) var hasExternalOutput: Bool
    private var observer: (any NSObjectProtocol)?

    init() {
        hasExternalOutput = Self.externalOutputPresent()
        observer = NotificationCenter.default.addObserver(
            forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main
        ) { [weak self] _ in
            Task { @MainActor in self?.refresh() }
        }
    }

    deinit {
        if let observer { NotificationCenter.default.removeObserver(observer) }
    }

    private func refresh() {
        let present = Self.externalOutputPresent()
        if present != hasExternalOutput { hasExternalOutput = present }
    }

    private static func externalOutputPresent() -> Bool {
        AVAudioSession.sharedInstance().currentRoute.outputs.contains { isExternal($0.portType) }
    }

    private static func isExternal(_ port: AVAudioSession.Port) -> Bool {
        switch port {
        case .bluetoothA2DP, .bluetoothHFP, .bluetoothLE, .headphones, .airPlay, .carAudio, .usbAudio, .HDMI, .lineOut:
            return true
        default:
            return false
        }
    }
}

/// The system's audio output picker, as a round control for the call screen.
struct LiveCallRoutePicker: UIViewRepresentable {
    @Environment(\.colorScheme) private var colorScheme

    func makeUIView(context: Context) -> AVRoutePickerView {
        let picker = AVRoutePickerView()
        picker.prioritizesVideoDevices = false
        picker.backgroundColor = .clear
        apply(to: picker)
        return picker
    }

    func updateUIView(_ picker: AVRoutePickerView, context: Context) {
        apply(to: picker)
    }

    private func apply(to picker: AVRoutePickerView) {
        let traits = UITraitCollection(userInterfaceStyle: colorScheme == .dark ? .dark : .light)
        let ink = UIColor(Color.seiraInk).resolvedColor(with: traits)
        picker.tintColor = ink
        picker.activeTintColor = ink
        let label = String(localized: "Audio output")
        picker.accessibilityLabel = label
        picker.accessibilityIdentifier = "live-call-screen-route"
        for case let button as UIButton in picker.subviews {
            button.accessibilityLabel = label
            button.accessibilityIdentifier = "live-call-screen-route"
        }
    }
}
