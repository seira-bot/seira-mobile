// The big animated character for the places that earn motion: the live call, the look preview, the
// home hero and empty states. Small avatars use `SeiraAvatarHead`.
import CompanionCore
import SwiftUI
import UIKit

enum SeiraCharacterMotion {
    /// A picture from the cache. For lists.
    case still
    /// Blinks every 3 to 6 seconds.
    case blink
    /// Blinks and turns 8 degrees to each side over 2.4 seconds.
    case sway
}

/// The blink and the sway are functions of time, so a view needs no state and a redraw never jumps.
enum SeiraCharacterClock {
    private static let blinkLength = 0.15
    private static let swayDegrees = 8.0
    private static let swayPeriod = 2.4

    private static func intervals(seed: UInt64) -> [Double] {
        var state = seed | 1
        return (0..<16).map { _ in
            state = state &* 6364136223846793005 &+ 1442695040888963407
            return 3 + Double(state >> 40) / Double(1 << 24) * 3
        }
    }

    static func blink(at time: Double, seed: UInt64) -> Double {
        let gaps = intervals(seed: seed)
        let cycle = gaps.reduce(0, +)
        var t = time.truncatingRemainder(dividingBy: cycle)
        if t < 0 { t += cycle }
        var start = 0.0
        for gap in gaps {
            if t < start + gap {
                let q = (t - start) / blinkLength
                return q < 1 ? sin(q * Double.pi) : 0
            }
            start += gap
        }
        return 0
    }

    static func turn(at time: Double) -> Double {
        swayDegrees * sin(2 * Double.pi * time / swayPeriod)
    }
}

/// Still pictures of the character. Safe to call from any thread.
enum SeiraCharacterStill {
    private static let images = NSCache<NSString, UIImage>()

    static func image(look: SeiraLook, mood: CharacterMood, size: CGFloat, scale: CGFloat, talk: Double) -> UIImage? {
        guard size > 0 else { return nil }
        let key = "\(look.hashValue)|\(mood.rawValue)|\(Int(size.rounded()))|\(scale)|\(Int(talk * 10))" as NSString
        if let hit = images.object(forKey: key) { return hit }
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        let side = CGSize(width: size, height: size)
        let pose = SeiraCharacterPose(view: .front, talk: talk)
        let image = UIGraphicsImageRenderer(size: side, format: format).image { context in
            SeiraCharacterRenderer.draw(in: context.cgContext, rect: CGRect(origin: .zero, size: side), look: look, pose: pose, mood: mood)
        }
        images.setObject(image, forKey: key)
        return image
    }
}

struct SeiraCharacter: View {
    let look: SeiraLook
    var size: CGFloat
    var mood: CharacterMood
    var motion: SeiraCharacterMotion
    var talkLevel: Double?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale

    init(look: SeiraLook, size: CGFloat, mood: CharacterMood = .idle, motion: SeiraCharacterMotion = .still, talkLevel: Double? = nil) {
        self.look = look
        self.size = size
        self.mood = mood
        self.motion = motion
        self.talkLevel = talkLevel
    }

    var body: some View {
        ZStack {
            content
                .id(look)
                .transition(.opacity)
        }
        .frame(width: size, height: size)
        .animation(.timingCurve(0.22, 1, 0.36, 1, duration: 0.18), value: look)
        .accessibilityHidden(true)
    }

    private var moves: Bool {
        motion != .still && !reduceMotion
    }

    @ViewBuilder
    private var content: some View {
        if moves {
            moving
        } else {
            still
        }
    }

    private var still: some View {
        let picture = SeiraCharacterStill.image(look: look, mood: mood, size: size, scale: displayScale, talk: talkLevel ?? 0)
        return Image(uiImage: picture ?? UIImage())
            .resizable()
            .frame(width: size, height: size)
    }

    private var moving: some View {
        TimelineView(.animation) { timeline in
            canvas(at: timeline.date.timeIntervalSinceReferenceDate)
        }
    }

    private func pose(at time: Double) -> SeiraCharacterPose {
        let seed = UInt64(truncatingIfNeeded: look.hashValue)
        var pose = SeiraCharacterPose(talk: talkLevel ?? 0, time: time)
        pose.blink = SeiraCharacterClock.blink(at: time, seed: seed)
        if motion == .sway { pose.turn = SeiraCharacterClock.turn(at: time) }
        return pose
    }

    private func canvas(at time: Double) -> some View {
        let current = pose(at: time)
        let look = self.look
        let mood = self.mood
        return Canvas { context, canvasSize in
            context.withCGContext { cg in
                SeiraCharacterRenderer.draw(in: cg, rect: CGRect(origin: .zero, size: canvasSize), look: look, pose: current, mood: mood)
            }
        }
        .frame(width: size, height: size)
    }
}
