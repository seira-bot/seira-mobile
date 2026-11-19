// The carved avatar: one head picture per hair style, the hair tinted inside its mask, the eyes and the
// mouth drawn on the visor, and the accessory stickers on top. It ports src/components/CarvedAvatar.tsx,
// seira-accessories.ts and seira-hair.ts of the desktop project. Every small avatar in the app uses it:
// lists, headers, notifications, widgets, Live Activities and intents.
import CompanionCore
import CoreGraphics
import SwiftUI
import UIKit

/// Where the face looks and how it feels. The same fields as the desktop `CarvedPose`.
struct SeiraAvatarPose: Equatable {
    var gx = 0.0
    var gy = 0.0
    /// Startle, 0...1: round eyes, open mouth, stretched head.
    var wide = 0.0
    /// Eyelids, 0 open to 1 shut.
    var shut = 0.0
    /// 1 is the resting smile. Below 0 it is a frown.
    var smile = 1.0

    init(gx: Double = 0, gy: Double = 0, wide: Double = 0, shut: Double = 0, smile: Double = 1) {
        self.gx = gx
        self.gy = gy
        self.wide = wide
        self.shut = shut
        self.smile = smile
    }

    init(mood: CharacterMood) {
        switch mood {
        case .surprised, .alerting, .scared, .excited, .celebrate, .spawning, .waking:
            self.init(wide: 1)
        case .sleeping, .poweringDown:
            self.init(shut: 0.92, smile: 0.4)
        case .drowsy, .bored:
            self.init(shut: 0.55, smile: 0.6)
        case .happy, .laughing, .proud, .playful:
            self.init(smile: 1.5)
        case .sad:
            self.init(gy: 0.6, smile: -0.8)
        case .thinking:
            self.init(gx: 0.6, gy: -0.6)
        case .working, .writing, .loading:
            self.init(gy: 0.5)
        default:
            self.init()
        }
    }
}

enum SeiraAvatarMood {
    /// The mood a pinned expression stands for. A mood name maps to itself. A face id maps to the nearest mood.
    static func resolve(expression: String?, mood: CharacterMood) -> CharacterMood {
        guard let name = expression?.lowercased(), !name.isEmpty else { return mood }
        if let named = CharacterMood.normalize(name) { return named }
        switch name {
        case "calm": return .idle
        case "happy", "giggle", "love": return .happy
        case "laugh": return .laughing
        case "shy": return .shy
        case "proud": return .proud
        case "surprised": return .surprised
        case "confused": return .confused
        case "thinking", "think-state": return .thinking
        case "sad", "cry": return .sad
        case "pout", "bored": return .bored
        case "angry": return .angry
        case "sleepy": return .drowsy
        case "asleep": return .sleeping
        case "wink": return .playful
        case "determined", "working": return .working
        case "smug": return .suspicious
        case "scared": return .scared
        case "cheer": return .excited
        case "listening": return .listening
        case "error": return .alerting
        default: return mood
        }
    }
}

/// The attributes the face needs for a pose, in the 240 unit square of the head picture.
struct SeiraAvatarGeometry {
    struct Eye {
        var x: Double
        var y: Double
        var width: Double
        var height: Double
        var radius: Double
        var tilt: Double
    }

    var lean: Double
    var scaleX: Double
    var scaleY: Double
    var eyes: [Eye]
    var smileFrom: CGPoint
    var smileTo: CGPoint
    var smileControl: CGPoint
    var smileTilt: Double
    var smilePivot: CGPoint
    var smileOpacity: Double
    var openCenter: CGPoint
    var openRadius: CGSize
    var openOpacity: Double

    private static let pivotY = 150.0

    init(body: SeiraKitBody, pose p: SeiraAvatarPose) {
        let e = body.eyes
        let ax = abs(p.gx)
        lean = -p.gx * 9
        scaleX = 1 - 0.06 * p.wide
        scaleY = 1 + 0.09 * p.wide
        let spread = e.dx * (1 - 0.1 * ax) * (1 + 0.14 * p.wide)
        let cx = e.cx + p.gx * e.dx * 1.05
        let cy = e.cy + p.gy * 16 - p.wide * e.h * 0.42
        var w = e.w * (1 + 0.05 * ax)
        var h = e.h * (1 - 0.3 * ax)
        w += (e.round - w) * p.wide
        h += (e.round * 1.08 - h) * p.wide
        h *= 1 - 0.9 * p.shut
        let tilt = -p.gx * 18 * (1 - p.wide)
        eyes = [-1.0, 1.0].map { side in
            let k = 1 - 0.1 * max(0, side * -p.gx)
            let ew = w * k
            let eh = max(h * k, 0.5)
            return Eye(x: cx + side * spread, y: cy, width: ew, height: eh, radius: min(ew, eh) / 2, tilt: tilt)
        }
        let mw = e.dx * 0.46
        let half = mw * (1 - 0.25 * ax)
        let depth = mw * 0.5 * p.smile
        let my = (e.mouthY ?? e.cy + e.h * 0.5 + e.dx * 0.42) + p.gy * 10
        smileFrom = CGPoint(x: cx - half, y: my - depth * 0.35)
        smileTo = CGPoint(x: cx + half, y: my - depth * 0.35)
        smileControl = CGPoint(x: cx, y: my + depth)
        smileTilt = tilt * 0.6
        smilePivot = CGPoint(x: cx, y: my)
        smileOpacity = 1 - p.wide
        let gape = max(0, p.wide)
        openCenter = CGPoint(x: cx, y: my + mw * 0.1)
        openRadius = CGSize(width: mw * 0.5 * gape, height: mw * 0.62 * gape)
        openOpacity = gape
    }
}

/// Reads the absolute M, C and Z commands that the visor clip path of a head uses.
enum SeiraSVGPath {
    static func parse(_ d: String) -> CGPath {
        let path = CGMutablePath()
        let spaced = d.map { "MCZ".contains($0) ? " \($0) " : String($0) }.joined()
        var command = ""
        var numbers: [CGFloat] = []
        for token in spaced.split(whereSeparator: { $0 == " " || $0 == "," || $0 == "\n" }) {
            if let value = Double(token) {
                numbers.append(CGFloat(value))
            } else {
                apply(command, numbers, to: path)
                command = String(token)
                numbers = []
            }
        }
        apply(command, numbers, to: path)
        return path
    }

    private static func apply(_ command: String, _ n: [CGFloat], to path: CGMutablePath) {
        switch command {
        case "M":
            var i = 0
            while i + 1 < n.count {
                let point = CGPoint(x: n[i], y: n[i + 1])
                if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
                i += 2
            }
        case "C":
            var i = 0
            while i + 5 < n.count {
                path.addCurve(
                    to: CGPoint(x: n[i + 4], y: n[i + 5]),
                    control1: CGPoint(x: n[i], y: n[i + 1]),
                    control2: CGPoint(x: n[i + 2], y: n[i + 3])
                )
                i += 6
            }
        case "Z":
            path.closeSubpath()
        default:
            break
        }
    }
}

enum SeiraAvatarHeadRenderer {
    private static let square: CGFloat = 240
    private static let box = CGRect(x: -14, y: -14, width: 268, height: 268)
    private static let lightScreen = "#ECEEF1"
    private static let darkInk = "#0C0F14"

    private static let paths = NSCache<NSString, CGPath>()

    private static func clip(for style: String, body: SeiraKitBody) -> CGPath {
        let key = style as NSString
        if let hit = paths.object(forKey: key) { return hit }
        let path = SeiraSVGPath.parse(body.clip)
        paths.setObject(path, forKey: key)
        return path
    }

    /// The hair tint of a colour. Silver and chrome keep the hair as rendered, so they have none.
    private static func tint(for look: SeiraLook) -> String? {
        if look.color == "silver" || look.color == "chrome" { return nil }
        return SeiraLookCatalog.colorHex[look.color]
    }

    private static func pick(_ sizes: [Int], atLeast pixels: CGFloat) -> Int? {
        sizes.sorted().first(where: { CGFloat($0) >= pixels }) ?? sizes.max()
    }

    private static func headPicture(style: String, body: SeiraKitBody, pixels: CGFloat, hex: String?) -> CGImage? {
        guard let headPx = pick(body.sizes.front, atLeast: pixels),
              let head = SeiraKit.image("carved/\(style)/front-\(headPx).webp", maxPixel: headPx) else { return nil }
        guard let hex, let hairPx = pick(body.sizes.hair, atLeast: pixels), let ext = body.hairExt,
              let hair = SeiraKit.image("carved/\(style)/hair-\(hairPx).\(ext)", maxPixel: hairPx) else { return head }
        return SeiraHairTint.carvedHead(head: head, hair: hair, hex: hex, seira: style == "seira", key: "\(style)|\(headPx)|\(hairPx)")
    }

    /// The box that shows the avatar and every sticker, as the desktop `spritesViewBox`.
    private static func viewBox(_ stickers: [Sticker]) -> CGRect {
        guard !stickers.isEmpty else { return box }
        let centre = box.midX
        let minX = min(box.minX, stickers.map { $0.rect.minX }.min() ?? box.minX)
        let maxX = max(box.maxX, stickers.map { $0.rect.maxX }.max() ?? box.maxX)
        let minY = min(box.minY, stickers.map { $0.rect.minY }.min() ?? box.minY)
        let maxY = max(box.maxY, stickers.map { $0.rect.maxY }.max() ?? box.maxY)
        let width = 2 * max(centre - minX, maxX - centre)
        let fit = max(width, maxY - minY)
        return CGRect(x: centre - fit / 2, y: minY - (fit - (maxY - minY)) / 2, width: fit, height: fit)
    }

    struct Sticker {
        let file: String
        let rect: CGRect
    }

    private static func stickers(style: String, body: SeiraKitBody, ids: [String]) -> [Sticker] {
        guard let placement = SeiraKit.placement, let head = placement.styles[style] else { return [] }
        return ids.compactMap { id -> Sticker? in
            guard let sticker = placement.accessories[id] else { return nil }
            let anchor = sticker.anchor ?? "top"
            let point: [Double]? = anchor == "eyes" ? [body.eyes.cx, body.eyes.cy] : head.anchors[anchor]
            guard let point, point.count == 2, sticker.size.count == 2 else { return nil }
            let w = sticker.size[0], h = sticker.size[1]
            let scale = (sticker.width ?? 1) * head.r / w
            let px = sticker.pivot.count > 0 ? sticker.pivot[0] : w / 2
            let py = sticker.pivot.count > 1 ? sticker.pivot[1] : h / 2
            let rect = CGRect(x: point[0] - px * scale, y: point[1] - py * scale, width: w * scale, height: h * scale)
            return Sticker(file: "accessories/\(sticker.file)", rect: rect)
        }
    }

    /// Draws the avatar into `rect`. The context's y axis points down, as in UIKit and in `Canvas`.
    static func draw(in ctx: CGContext, rect: CGRect, look: SeiraLook, mood: CharacterMood) {
        let style = SeiraKit.bodies[look.hair] != nil ? look.hair : "seira"
        guard let body = SeiraKit.bodies[style] else { return }
        let resolved = SeiraAvatarMood.resolve(expression: look.expression, mood: mood)
        let geometry = SeiraAvatarGeometry(body: body, pose: SeiraAvatarPose(mood: resolved))
        let marks = stickers(style: style, body: body, ids: look.accessories)
        let view = viewBox(marks)
        let unit = rect.width / view.width
        let device = abs(ctx.userSpaceToDeviceSpaceTransform.a)
        let pixels = rect.width * (device > 0 ? device : 1) * square / view.width

        ctx.saveGState()
        ctx.translateBy(x: rect.minX, y: rect.minY)
        ctx.scaleBy(x: unit, y: unit)
        ctx.translateBy(x: -view.minX, y: -view.minY)
        applyBody(ctx, geometry)
        if let picture = headPicture(style: style, body: body, pixels: pixels, hex: tint(for: look)) {
            ctx.drawUpright(picture, in: CGRect(x: 0, y: 0, width: square, height: square))
        }
        let ink = SeiraFaceInk(faceInk: look.faceInk)
        drawEyes(ctx, geometry, body: body, ink: ink, glow: unit * device, clipPath: clip(for: style, body: body))
        if body.eyes.mouth { drawMouth(ctx, geometry, body: body, ink: ink, glow: unit * device) }
        for mark in marks {
            let want = mark.rect.width * unit * device
            if let image = SeiraKit.image(mark.file, maxPixel: SeiraKit.bucket(want)) {
                ctx.drawUpright(image, in: mark.rect)
            }
        }
        ctx.restoreGState()
    }

    private static func applyBody(_ ctx: CGContext, _ g: SeiraAvatarGeometry) {
        ctx.translateBy(x: 120, y: 150)
        ctx.rotate(by: CGFloat(g.lean) * .pi / 180)
        ctx.scaleBy(x: CGFloat(g.scaleX), y: CGFloat(g.scaleY))
        ctx.translateBy(x: -120, y: -150)
    }

    private static func eyeInk(_ body: SeiraKitBody, _ ink: SeiraFaceInk) -> String {
        ink == .light ? darkInk : body.eyes.ink
    }

    private static func mouthInk(_ body: SeiraKitBody, _ ink: SeiraFaceInk) -> String {
        ink == .light ? darkInk : body.eyes.mouthInk
    }

    private static func setGlow(_ ctx: CGContext, hex: String, ink: SeiraFaceInk, scale: CGFloat) {
        guard ink == .dark else { return }
        ctx.setShadow(offset: .zero, blur: 7 * scale, color: seiraCGColor(hex, alpha: 0.9))
    }

    private static func drawEyes(_ ctx: CGContext, _ g: SeiraAvatarGeometry, body: SeiraKitBody, ink: SeiraFaceInk, glow: CGFloat, clipPath: CGPath) {
        ctx.saveGState()
        ctx.addPath(clipPath)
        ctx.clip()
        if ink == .light {
            ctx.setFillColor(seiraCGColor(lightScreen))
            ctx.addPath(clipPath)
            ctx.fillPath()
        }
        let color = eyeInk(body, ink)
        setGlow(ctx, hex: color, ink: ink, scale: glow)
        ctx.setFillColor(seiraCGColor(color))
        for eye in g.eyes {
            ctx.saveGState()
            ctx.translateBy(x: CGFloat(eye.x), y: CGFloat(eye.y))
            ctx.rotate(by: CGFloat(eye.tilt) * .pi / 180)
            let rect = CGRect(x: -eye.width / 2, y: -eye.height / 2, width: eye.width, height: eye.height)
            let radius = CGFloat(eye.radius)
            ctx.addPath(CGPath(roundedRect: rect, cornerWidth: radius, cornerHeight: radius, transform: nil))
            ctx.fillPath()
            ctx.restoreGState()
        }
        ctx.restoreGState()
    }

    private static func drawMouth(_ ctx: CGContext, _ g: SeiraAvatarGeometry, body: SeiraKitBody, ink: SeiraFaceInk, glow: CGFloat) {
        let color = mouthInk(body, ink)
        ctx.saveGState()
        setGlow(ctx, hex: color, ink: ink, scale: glow)
        if g.smileOpacity > 0.001 {
            ctx.saveGState()
            ctx.setAlpha(CGFloat(g.smileOpacity))
            ctx.translateBy(x: g.smilePivot.x, y: g.smilePivot.y)
            ctx.rotate(by: CGFloat(g.smileTilt) * .pi / 180)
            ctx.translateBy(x: -g.smilePivot.x, y: -g.smilePivot.y)
            let path = CGMutablePath()
            path.move(to: g.smileFrom)
            path.addQuadCurve(to: g.smileTo, control: g.smileControl)
            ctx.setStrokeColor(seiraCGColor(color))
            ctx.setLineWidth(CGFloat(body.eyes.w * 0.36))
            ctx.setLineCap(.round)
            ctx.addPath(path)
            ctx.strokePath()
            ctx.restoreGState()
        }
        if g.openOpacity > 0.001 {
            ctx.setFillColor(seiraCGColor(color, alpha: CGFloat(g.openOpacity)))
            let r = g.openRadius
            ctx.fillEllipse(in: CGRect(x: g.openCenter.x - r.width, y: g.openCenter.y - r.height, width: r.width * 2, height: r.height * 2))
        }
        ctx.restoreGState()
    }
}

/// A still carved avatar for SwiftUI. For notifications, widgets, Live Activities and intents use `image`.
struct SeiraAvatarHead: View {
    let look: SeiraLook
    let size: CGFloat
    var mood: CharacterMood = .idle
    @Environment(\.displayScale) private var displayScale

    var body: some View {
        Image(uiImage: SeiraAvatarHead.image(look: look, mood: mood, size: size, scale: displayScale) ?? UIImage())
            .resizable()
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }

    /// The avatar as an image of `size` x `size` points. Safe to call from any thread and from an app extension.
    nonisolated static func image(look: SeiraLook, mood: CharacterMood = .idle, size: CGFloat, scale: CGFloat) -> UIImage? {
        guard size > 0 else { return nil }
        let key = "\(look.hashValue)|\(mood.rawValue)|\(Int(size.rounded()))|\(scale)" as NSString
        if let hit = SeiraAvatarImageCache.images.object(forKey: key) { return hit }
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        let side = CGSize(width: size, height: size)
        let image = UIGraphicsImageRenderer(size: side, format: format).image { context in
            SeiraAvatarHeadRenderer.draw(in: context.cgContext, rect: CGRect(origin: .zero, size: side), look: look, mood: mood)
        }
        SeiraAvatarImageCache.images.setObject(image, forKey: key)
        return image
    }
}

private enum SeiraAvatarImageCache {
    static let images = NSCache<NSString, UIImage>()
}
