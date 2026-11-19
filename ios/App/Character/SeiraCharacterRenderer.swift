// The big animated character. It ports brand/mascot/runtime/bot.js of the desktop project: a rendered
// head frame with tinted hair, a screen face drawn on the visor, and the accessory stickers.
// Hands and the front effects of the desktop runtime are not ported.
import CompanionCore
import CoreGraphics
import Foundation

enum SeiraCharacterView {
    case front
    case yaw20
    case yaw20Mirrored
}

struct SeiraCharacterPose: Equatable {
    /// A fixed frame. When nil the frame follows `turn`.
    var view: SeiraCharacterView?
    /// Head turn in degrees. Positive looks to the right of the screen.
    var turn = 0.0
    /// A face id that overrides the face of the look and of the mood.
    var face: String?
    /// 0 is open, 1 is shut.
    var blink = 0.0
    /// Mouth opening from the voice, 0...1.
    var talk = 0.0
    /// Seconds, for the moving parts of a face.
    var time = 0.0

    init(view: SeiraCharacterView? = nil, turn: Double = 0, face: String? = nil, blink: Double = 0, talk: Double = 0, time: Double = 0) {
        self.view = view
        self.turn = turn
        self.face = face
        self.blink = blink
        self.talk = talk
        self.time = time
    }
}

enum SeiraCharacterRenderer {
    private static let defaultHair = "#3B3F4A"
    private static let ground: CGFloat = 1.82

    private struct Mix {
        let name: String
        let view: SeiraKitView
        let weight: Double
        let mirror: Bool
        let yaw: Double
    }

    private struct FacePlace {
        var cx: CGFloat
        var cy: CGFloat
        var k: CGFloat
        var squeeze: CGFloat
    }

    // MARK: Views

    private static func smooth(_ t: Double) -> Double {
        let x = max(0, min(1, t))
        return x * x * (3 - 2 * x)
    }

    private static func mixes(_ style: SeiraKitStyle, _ pose: SeiraCharacterPose) -> [Mix] {
        guard let front = style.views["front"] else { return [] }
        let turned = style.views["yaw20"]
        func entry(_ name: String, _ view: SeiraKitView, _ weight: Double, mirror: Bool) -> Mix {
            Mix(name: name, view: view, weight: weight, mirror: mirror, yaw: (mirror ? -1 : 1) * view.measuredYaw)
        }
        if let fixed = pose.view {
            switch fixed {
            case .front: return [entry("front", front, 1, mirror: false)]
            case .yaw20: return turned.map { [entry("yaw20", $0, 1, mirror: false)] } ?? [entry("front", front, 1, mirror: false)]
            case .yaw20Mirrored: return turned.map { [entry("yaw20", $0, 1, mirror: true)] } ?? [entry("front", front, 1, mirror: false)]
            }
        }
        guard let turned else { return [entry("front", front, 1, mirror: false)] }
        let b = smooth((abs(pose.turn) - 3) / 3)
        var out: [Mix] = []
        if b < 0.999 { out.append(entry("front", front, 1 - b, mirror: false)) }
        if b > 0.001 { out.append(entry("yaw20", turned, b, mirror: pose.turn < 0)) }
        return out
    }

    private static func world(_ v: SeiraKitView, mirror: Bool, _ px: Double, _ py: Double) -> CGPoint {
        let x = (px - v.pivot[0]) / v.r
        let y = (py - v.pivot[1]) / v.r
        return CGPoint(x: mirror ? -x : x, y: y)
    }

    private static func facePlace(_ style: SeiraKitStyle, _ mixes: [Mix], yaw: Double) -> FacePlace {
        let rad = Double.pi / 180
        let reference = style.views["front"] ?? style.views["yaw20"]
        var frontW = 1.5, frontCx = 0.0, frontCy = -0.05
        if let v = reference, v.visorBox.count == 4 {
            let c = world(v, mirror: false, v.visorBox[0] + v.visorBox[2] / 2, v.visorBox[1] + v.visorBox[3] / 2)
            frontW = v.visorBox[2] / v.r
            frontCx = Double(c.x)
            frontCy = Double(c.y)
        }
        var sx = 0.0, sy = 0.0, sw = 0.0
        for m in mixes where m.view.visorBox.count == 4 && m.view.visor != nil {
            let b = m.view.visorBox
            let c = world(m.view, mirror: m.mirror, b[0] + b[2] / 2, b[1] + b[3] / 2)
            sx += Double(c.x) * m.weight
            sy += Double(c.y) * m.weight
            sw += m.weight
        }
        var cx = sw > 0 ? sx / sw : frontCx + sin(yaw * rad) * 0.88
        let cy = sw > 0 ? sy / sw : frontCy
        if sw > 0 {
            for m in mixes { cx += m.weight * 0.88 * (sin(yaw * rad) - sin(m.yaw * rad)) }
        }
        return FacePlace(cx: CGFloat(cx), cy: CGFloat(cy), k: CGFloat(frontW / Double(SeiraFace.width)), squeeze: CGFloat(max(0.05, cos(yaw * rad))))
    }

    // MARK: Pictures

    private static let masks = NSCache<NSString, CGImage>()

    private static func maxPixel(_ view: SeiraKitView, unit: CGFloat, device: CGFloat, natural: CGSize) -> Int {
        let longest = max(natural.width, natural.height)
        return SeiraKit.bucket(longest / CGFloat(view.r) * unit * device)
    }

    private static func tintHex(_ style: SeiraKitStyle, _ look: SeiraLook) -> String? {
        let grey = look.color == "silver" || look.color == "chrome"
        if grey && !style.recolor { return nil }
        return SeiraLookCatalog.colorHex[look.color] ?? (style.recolor ? defaultHair : nil)
    }

    private static func frameImage(_ m: Mix, style: SeiraKitStyle, look: SeiraLook, pixel: Int) -> CGImage? {
        guard let frame = SeiraKit.image(m.view.file, maxPixel: pixel) else { return nil }
        guard let hex = tintHex(style, look), let hairFile = m.view.hair,
              let hair = SeiraKit.image(hairFile, maxPixel: pixel) else { return frame }
        return SeiraHairTint.frameHair(frame, mask: hair, hex: hex, key: m.view.file)
    }

    /// The visor as an alpha mask: the mean of the colour channels, kept as alpha.
    private static func visorMask(_ m: Mix, pixel: Int) -> CGImage? {
        guard let file = m.view.visor else { return nil }
        let key = "\(file)@\(pixel)" as NSString
        if let hit = masks.object(forKey: key) { return hit }
        guard let image = SeiraKit.image(file, maxPixel: pixel), var bitmap = SeiraBitmap(image) else { return nil }
        let count = bitmap.width * bitmap.height
        for n in 0..<count {
            let p = n * 4
            let v = UInt8((Int(bitmap.data[p]) + Int(bitmap.data[p + 1]) + Int(bitmap.data[p + 2])) / 3)
            bitmap.data[p] = v
            bitmap.data[p + 1] = v
            bitmap.data[p + 2] = v
            bitmap.data[p + 3] = v
        }
        guard let mask = bitmap.makeImage() else { return nil }
        masks.setObject(mask, forKey: key)
        return mask
    }

    // MARK: Drawing

    /// Draws the character into `rect`. The context's y axis points down, as in UIKit and in `Canvas`.
    static func draw(in ctx: CGContext, rect: CGRect, look: SeiraLook, pose: SeiraCharacterPose, mood: CharacterMood = .idle) {
        let styleId = SeiraKit.styles[look.hair] != nil ? look.hair : "seira"
        guard let style = SeiraKit.styles[styleId] else { return }
        let unit = min(rect.width / 3.8, rect.height / 4.05)
        let device = abs(ctx.userSpaceToDeviceSpaceTransform.a)
        let scale = device > 0 ? device : 1
        let views = mixes(style, pose)
        let yaw = pose.view == nil ? pose.turn : (views.first?.yaw ?? 0)
        let place = facePlace(style, views, yaw: yaw)

        ctx.saveGState()
        ctx.translateBy(x: rect.midX, y: rect.midY + 0.025 * unit)
        ctx.scaleBy(x: unit, y: unit)
        drawShadow(ctx)
        drawHead(ctx, style: style, views: views, look: look, pose: pose, mood: mood, place: place, unit: unit, device: scale)
        drawAccessories(ctx, style: style, views: views, look: look, place: place, yaw: yaw, unit: unit, device: scale)
        ctx.restoreGState()
    }

    private static func drawShadow(_ ctx: CGContext) {
        let space = SeiraBitmap.colorSpace
        let strong = CGColor(colorSpace: space, components: [20.0 / 255, 24.0 / 255, 32.0 / 255, 0.20]) ?? CGColor(gray: 0, alpha: 0.2)
        let none = CGColor(colorSpace: space, components: [20.0 / 255, 24.0 / 255, 32.0 / 255, 0]) ?? CGColor(gray: 0, alpha: 0)
        guard let gradient = CGGradient(colorsSpace: space, colors: [strong, none] as CFArray, locations: [0, 1]) else { return }
        ctx.saveGState()
        ctx.translateBy(x: 0, y: ground)
        ctx.scaleBy(x: 1, y: 1.0 / 6)
        ctx.drawRadialGradient(gradient, startCenter: .zero, startRadius: 0, endCenter: .zero, endRadius: 1.2, options: [])
        ctx.restoreGState()
    }

    private static func applyView(_ ctx: CGContext, _ m: Mix) {
        if m.mirror { ctx.scaleBy(x: -1, y: 1) }
        ctx.scaleBy(x: 1 / CGFloat(m.view.r), y: 1 / CGFloat(m.view.r))
        ctx.translateBy(x: -CGFloat(m.view.pivot[0]), y: -CGFloat(m.view.pivot[1]))
    }

    private static func drawHead(
        _ ctx: CGContext, style: SeiraKitStyle, views: [Mix], look: SeiraLook, pose: SeiraCharacterPose,
        mood: CharacterMood, place: FacePlace, unit: CGFloat, device: CGFloat
    ) {
        ctx.beginTransparencyLayer(auxiliaryInfo: nil)
        for m in views {
            guard let natural = SeiraKit.naturalSize(m.view.file) else { continue }
            let pixel = maxPixel(m.view, unit: unit, device: device, natural: natural)
            guard let image = frameImage(m, style: style, look: look, pixel: pixel) else { continue }
            ctx.saveGState()
            ctx.setAlpha(CGFloat(m.weight))
            ctx.setBlendMode(.plusLighter)
            applyView(ctx, m)
            ctx.drawUpright(image, in: CGRect(origin: .zero, size: natural))
            ctx.restoreGState()
        }
        if let lead = views.max(by: { $0.weight < $1.weight }), lead.view.visor != nil {
            drawFace(ctx, lead: lead, look: look, pose: pose, mood: mood, place: place, unit: unit, device: device)
        }
        ctx.endTransparencyLayer()
    }

    private static func drawFace(
        _ ctx: CGContext, lead: Mix, look: SeiraLook, pose: SeiraCharacterPose, mood: CharacterMood,
        place: FacePlace, unit: CGFloat, device: CGFloat
    ) {
        guard let natural = SeiraKit.naturalSize(lead.view.file) else { return }
        let pixel = maxPixel(lead.view, unit: unit, device: device, natural: natural)
        guard let mask = visorMask(lead, pixel: pixel) else { return }
        let id = pose.face ?? SeiraFaces.id(expression: look.expression, mood: mood)
        var input = SeiraFaceInput(spec: SeiraFaces.spec(id), ink: SeiraFaceInk(faceInk: look.faceInk))
        input.blink = pose.blink
        input.talk = pose.talk
        input.time = pose.time

        ctx.saveGState()
        ctx.beginTransparencyLayer(auxiliaryInfo: nil)
        ctx.saveGState()
        ctx.translateBy(x: place.cx, y: place.cy)
        ctx.scaleBy(x: place.squeeze, y: 1)
        ctx.scaleBy(x: place.k, y: place.k)
        ctx.translateBy(x: -SeiraFace.width / 2, y: -SeiraFace.height / 2)
        let marginX = SeiraFace.width * 0.2, marginY = SeiraFace.height * 0.3
        ctx.clip(to: CGRect(x: -marginX, y: -marginY, width: SeiraFace.width + 2 * marginX, height: SeiraFace.height + 2 * marginY))
        SeiraFace.draw(ctx, input, pixelsPerUnit: place.k * unit * device)
        ctx.restoreGState()

        ctx.saveGState()
        ctx.setBlendMode(.destinationIn)
        applyView(ctx, lead)
        ctx.drawUpright(mask, in: CGRect(origin: .zero, size: natural))
        ctx.restoreGState()
        ctx.endTransparencyLayer()
        ctx.restoreGState()
    }

    // MARK: Accessories

    private static func anchor(_ name: String, views: [Mix]) -> CGPoint? {
        var x = 0.0, y = 0.0, total = 0.0
        for m in views {
            var key = name
            if m.mirror {
                if name.hasSuffix("L") { key = String(name.dropLast()) + "R" } else if name.hasSuffix("R") { key = String(name.dropLast()) + "L" }
            }
            guard let a = m.view.anchors[key], a.count == 2 else { continue }
            let p = world(m.view, mirror: m.mirror, a[0], a[1])
            x += Double(p.x) * m.weight
            y += Double(p.y) * m.weight
            total += m.weight
        }
        return total > 0 ? CGPoint(x: x / total, y: y / total) : nil
    }

    private static func drawAccessories(
        _ ctx: CGContext, style: SeiraKitStyle, views: [Mix], look: SeiraLook, place: FacePlace,
        yaw: Double, unit: CGFloat, device: CGFloat
    ) {
        guard let placement = SeiraKit.placement else { return }
        var slots = Set<String>()
        for id in look.accessories {
            guard let def = placement.accessories[id], slots.insert(def.slot).inserted else { continue }
            let visible = views.filter { def.views?.contains($0.name) ?? ($0.name == "front") }.reduce(0.0) { $0 + $1.weight }
            if visible < 0.02 { continue }
            let name = def.anchor ?? "top"
            let point: CGPoint?
            if name == "eyes" || name == "face" {
                let eyeY = place.cy + (SeiraFace.centerY - SeiraFace.height / 2) * place.k
                point = CGPoint(x: place.cx, y: name == "eyes" ? eyeY : place.cy)
            } else {
                point = anchor(name, views: views)
            }
            guard let point, def.size.count == 2, def.pivot.count == 2 else { continue }
            let width = CGFloat(def.width ?? 1)
            let scale = width / CGFloat(def.size[0])
            let squeeze: CGFloat = (name == "eyes" || name == "mouth") ? max(0.2, CGFloat(cos(yaw * Double.pi / 180))) : 1
            guard let image = SeiraKit.image("accessories/\(def.file)", maxPixel: SeiraKit.bucket(width * unit * device)) else { continue }
            ctx.saveGState()
            ctx.translateBy(x: point.x, y: point.y)
            ctx.scaleBy(x: scale * squeeze, y: scale)
            ctx.setAlpha(CGFloat(min(1, visible)))
            let origin = CGPoint(x: -CGFloat(def.pivot[0]), y: -CGFloat(def.pivot[1]))
            ctx.drawUpright(image, in: CGRect(origin: origin, size: CGSize(width: def.size[0], height: def.size[1])))
            ctx.restoreGState()
        }
    }
}
