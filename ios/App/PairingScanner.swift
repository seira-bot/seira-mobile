// Native QR scanning for the one action where typing is needless friction.
//
// The scanner only recognizes QR codes and never pairs by itself. A valid
// payload is returned to PairingView, which shows the computer name and
// address for explicit confirmation. That mirrors T3 Code's production rule:
// an attacker-controlled deep link or QR may prefill a target, but it cannot
// silently authorize that target.
import AVFoundation
import SwiftUI
import UIKit
#if !targetEnvironment(macCatalyst)
import VisionKit
#endif

@MainActor
final class PairingCamera: ObservableObject {
    enum Access {
        case unresolved, allowed, denied
    }

    @Published private(set) var access: Access

    init() {
        access = Self.currentAccess()
    }

    /// `DataScannerViewController` needs iOS 16 and a supported camera.
    var scannerReady: Bool {
#if targetEnvironment(macCatalyst)
        // VisionKit's DataScannerViewController is unavailable in Mac Catalyst (SDK 26).
        false
#else
        DataScannerViewController.isSupported && DataScannerViewController.isAvailable
#endif
    }

    /// If access was granted in Settings, the screen recovers when the person
    /// returns instead of requiring it to reopen.
    func resolve() async {
#if targetEnvironment(macCatalyst)
        access = .allowed
        return
#else
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            access = .allowed
        case .notDetermined:
            access = await AVCaptureDevice.requestAccess(for: .video) ? .allowed : .denied
        default:
            access = .denied
        }
#endif
    }

    private static func currentAccess() -> Access {
#if targetEnvironment(macCatalyst)
        return .allowed
#else
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: return .allowed
        case .notDetermined: return .unresolved
        default: return .denied
        }
#endif
    }
}

#if targetEnvironment(macCatalyst)
struct PairingQRScanner: View {
    let onPayload: (String) -> Bool

    var body: some View { EmptyView() }
}
#else
struct PairingQRScanner: UIViewControllerRepresentable {
    /// Return true to accept the code and stop reading, false to keep scanning.
    let onPayload: (String) -> Bool

    func makeCoordinator() -> Coordinator {
        Coordinator(onPayload: onPayload)
    }

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let controller = DataScannerViewController(
            recognizedDataTypes: [.barcode(symbologies: [.qr])],
            qualityLevel: .balanced,
            recognizesMultipleItems: false,
            isHighFrameRateTrackingEnabled: false,
            isPinchToZoomEnabled: true,
            isGuidanceEnabled: false,
            isHighlightingEnabled: true
        )
        controller.delegate = context.coordinator
        DispatchQueue.main.async {
            try? controller.startScanning()
        }
        return controller
    }

    func updateUIViewController(_ controller: DataScannerViewController, context: Context) {
        context.coordinator.onPayload = onPayload
        if !controller.isScanning {
            try? controller.startScanning()
        }
    }

    static func dismantleUIViewController(_ controller: DataScannerViewController, coordinator: Coordinator) {
        controller.stopScanning()
    }

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        var onPayload: (String) -> Bool
        private var locked = false

        init(onPayload: @escaping (String) -> Bool) {
            self.onPayload = onPayload
        }

        func dataScanner(
            _ dataScanner: DataScannerViewController,
            didAdd addedItems: [RecognizedItem],
            allItems: [RecognizedItem]
        ) {
            guard !locked else { return }
            guard let first = addedItems.first,
                  case let .barcode(barcode) = first,
                  let payload = barcode.payloadStringValue
            else { return }

            locked = true
            if !onPayload(payload) {
                // A camera reports the same QR on many consecutive frames.
                // Give the error time to be read before allowing a retry.
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { [weak self] in
                    self?.locked = false
                }
            }
        }
    }
}
#endif
