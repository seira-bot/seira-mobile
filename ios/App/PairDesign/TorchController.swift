import AVFoundation
import SwiftUI

@MainActor
final class TorchController: ObservableObject {
    @Published private(set) var isOn = false

    private var device: AVCaptureDevice? { AVCaptureDevice.default(for: .video) }

    var isAvailable: Bool {
        guard let device else { return false }
        return device.hasTorch && device.isTorchAvailable
    }

    func toggle() {
        setOn(!isOn)
    }

    func turnOff() {
        guard isOn else { return }
        setOn(false)
    }

    private func setOn(_ on: Bool) {
        guard let device, device.hasTorch else {
            isOn = false
            return
        }
        do {
            try device.lockForConfiguration()
            device.torchMode = on ? .on : .off
            device.unlockForConfiguration()
            isOn = on
        } catch {
            isOn = false
        }
    }
}
