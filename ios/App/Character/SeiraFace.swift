// The screen face of the character. It ports the shapes and the proportions of the desktop runtime
// (brand/mascot/runtime/face.js): one face unit U is 6 px on a 1024 by 640 canvas, eye width to
// height is 21 to 34, the gap between the eyes is 2 x 13, and each eye has a highlight and a lash wing.
// Only the eye and mouth shapes that the moods and the pickable expressions use are ported.
import CoreGraphics
import Foundation

/// Screen and feature colours. `.dark` is the default: a dark screen with pink features.
/// `.light` is the "black" face ink: a light screen with dark features.
enum SeiraFaceInk {
    case dark
    case light

    init(faceInk: String?) {
        self = faceInk == "black" ? .light : .dark
    }

    var feature: String { self == .dark ? "#FF2A57" : "#0C0F14" }
    var screen: String { self == .dark ? "#0C0F14" : "#ECEEF1" }
}

struct SeiraFaceSpec {
    var eyes = "base"
    var eyesLeft: String?
    var eyesRight: String?
    var lidInner = 0.0
    var lidOuter = 0.0
    var lidLow = 0.0
    var scaleLeft = 1.0
    var scaleRight = 1.0
    var look = CGPoint.zero
    var mouth = "smile"
    var brow = 0.0
    var blush = 0.0
    var tears = 0.0
    var sweat = 0.0
    var dots = false
}

/// Everything one drawing of the face needs.
struct SeiraFaceInput {
    var spec: SeiraFaceSpec
    var ink: SeiraFaceInk
    /// 0 is open, 1 is shut.
    var blink: Double = 0
    /// Mouth opening from the voice, 0...1.
    var talk: Double = 0
    /// Seconds. Drives the dots of the thinking face.
    var time: Double = 0
}

enum SeiraFaces {
    private static func face(
        eyes: String = "base", left: String? = nil, right: String? = nil,
        lid: (Double, Double, Double) = (0, 0, 0), scale: Double = 1, look: (Double, Double) = (0, 0),
        mouth: String = "smile", brow: Double = 0, blush: Double = 0, tears: Double = 0, sweat: Double = 0, dots: Bool = false
    ) -> SeiraFaceSpec {
        var spec = SeiraFaceSpec()
        spec.eyes = eyes
        spec.eyesLeft = left
        spec.eyesRight = right
        spec.lidInner = lid.0
        spec.lidOuter = lid.1
        spec.lidLow = lid.2
        spec.scaleLeft = scale
        spec.scaleRight = scale
        spec.look = CGPoint(x: look.0, y: look.1)
        spec.mouth = mouth
        spec.brow = brow
        spec.blush = blush
        spec.tears = tears
        spec.sweat = sweat
        spec.dots = dots
        return spec
    }

    /// The faces that moods and expressions use. The numbers are the desktop catalog.js entries.
    static let table: [String: SeiraFaceSpec] = {
        var t: [String: SeiraFaceSpec] = [:]
        t["calm"] = face()
        t["happy"] = face(eyes: "arc-up", mouth: "open")
        t["laugh"] = face(eyes: "chevron", mouth: "open-big")
        t["giggle"] = face(eyes: "arc-up", mouth: "open-small", blush: 0.5)
        t["love"] = face(eyes: "heart", mouth: "smile", blush: 0.6)
        t["shy"] = face(scale: 0.9, look: (0.6, 0.65), mouth: "wavy-small", blush: 1)
        t["proud"] = face(eyes: "arc-up", mouth: "smile")
        t["surprised"] = face(eyes: "round-big", mouth: "small-o")
        t["confused"] = confused()
        t["thinking"] = face(look: (0.75, -0.85), mouth: "flat-side")
        t["sad"] = face(lid: (0, 0.34, 0), look: (0, 0.35), mouth: "frown", brow: -1)
        t["cry"] = face(eyes: "arc-down", mouth: "frown", brow: -1, tears: 1)
        t["pout"] = face(lid: (0.3, 0.3, 0), look: (-0.6, 0), mouth: "flat", blush: 0.4)
        t["angry"] = face(lid: (0.44, 0.06, 0), mouth: "frown", brow: 1)
        t["sleepy"] = face(lid: (0.56, 0.56, 0), look: (0, 0.3), mouth: "tiny-o")
        t["asleep"] = face(eyes: "arc-down", mouth: "tiny-o")
        t["wink"] = face(right: "arc-up", mouth: "smile")
        t["bored"] = face(lid: (0.5, 0.5, 0), look: (0.55, 0.1), mouth: "flat")
        t["determined"] = face(lid: (0.3, 0.12, 0), mouth: "firm", brow: 0.7)
        t["smug"] = face(lid: (0.44, 0.44, 0), look: (0.35, 0), mouth: "smirk")
        t["scared"] = face(eyes: "small", mouth: "wavy", sweat: 1)
        t["cheer"] = face(eyes: "chevron", mouth: "open-big")
        t["listening"] = face(scale: 1.06, look: (0, -0.1), mouth: "smile")
        t["think-state"] = face(look: (0.75, -0.85), mouth: "flat", dots: true)
        t["working"] = face(lid: (0.22, 0.08, 0), look: (0, 0.35), mouth: "firm", brow: 0.4)
        t["error"] = errorFace()
        return t
    }()

    private static func confused() -> SeiraFaceSpec {
        var spec = face(look: (0.2, -0.2), mouth: "wavy", brow: -0.5)
        spec.scaleLeft = 0.68
        spec.scaleRight = 1.05
        return spec
    }

    private static func errorFace() -> SeiraFaceSpec {
        var spec = face(mouth: "wavy", brow: -0.6, sweat: 1)
        spec.scaleLeft = 0.8
        return spec
    }

    /// The face for a look and a mood. A pinned expression wins. It can name a face or a mood.
    static func id(expression: String?, mood: CharacterMood) -> String {
        if let name = expression?.lowercased(), !name.isEmpty {
            if table[name] != nil { return name }
            if let pinned = CharacterMood.normalize(name) { return pinned.faceId }
        }
        return mood.faceId
    }

    static func spec(_ id: String) -> SeiraFaceSpec {
        table[id] ?? SeiraFaceSpec()
    }
}

enum SeiraFace {
    static let width: CGFloat = 1024
    static let height: CGFloat = 640

    private static let fib: [CGFloat] = [0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144]
    private static let u: CGFloat = 6
    private static let eyeWidth: CGFloat = 21 * 6
    private static let eyeHeight: CGFloat = 34 * 6
    private static let gap: CGFloat = 2 * 13 * 6
    private static let top: CGFloat = 640 * 0.45 - 34 * 6 / 2
    private static let pi = CGFloat.pi

    /// Eye centre line.
    static var centerY: CGFloat { top + eyeHeight / 2 }
    static var centerXs: [CGFloat] {
        [width / 2 - gap / 2 - eyeWidth / 2, width / 2 + gap / 2 + eyeWidth / 2]
    }
    private static var mouthY: CGFloat { top + eyeHeight + fib[6] * u }

    /// Draws the face on the 1024 by 640 canvas. The caller sets the transform and the clip.
    /// `pixelsPerUnit` is the device pixels of one canvas pixel; it sizes the glow.
    static func draw(_ c: CGContext, _ input: SeiraFaceInput, pixelsPerUnit: CGFloat) {
        c.saveGState()
        drawScreen(c, input.ink)
        let feature = seiraCGColor(input.ink.feature)
        c.setFillColor(feature)
        c.setStrokeColor(feature)
        c.setLineCap(.round)
        c.setLineJoin(.round)
        if input.ink == .dark {
            c.setShadow(offset: .zero, blur: fib[5] * u * pixelsPerUnit, color: seiraCGColor(input.ink.feature, alpha: 0.85))
        }
        let ex = CGFloat(input.spec.look.x * 48)
        let ey = CGFloat(input.spec.look.y * 36)
        for i in 0..<2 {
            drawEye(c, input, index: i, ex: ex, ey: ey)
        }
        if input.spec.sweat > 0.02 {
            drawSweat(c, x: centerXs[1] + eyeWidth * 0.95 + ex, y: top + 4 * u + ey, scale: CGFloat(input.spec.sweat))
        }
        let mouthX = width / 2 + ex * 0.6
        drawMouth(c, input, x: mouthX, y: mouthY + ey * 0.4)
        c.restoreGState()
        drawGloss(c)
    }

    // MARK: Screen

    private static func mix(_ hex: String, _ other: (Double, Double, Double), _ k: Double) -> CGColor {
        let a = seiraRGB(hex) ?? (0, 0, 0)
        let r = (a.0 * (1 - k) + other.0 * k) / 255
        let g = (a.1 * (1 - k) + other.1 * k) / 255
        let b = (a.2 * (1 - k) + other.2 * k) / 255
        return CGColor(colorSpace: SeiraBitmap.colorSpace, components: [CGFloat(r), CGFloat(g), CGFloat(b), 1]) ?? seiraCGColor(hex)
    }

    private static func drawScreen(_ c: CGContext, _ ink: SeiraFaceInk) {
        let area = CGRect(x: -width, y: -height, width: width * 3, height: height * 3)
        let white: (Double, Double, Double) = (255, 255, 255)
        let black: (Double, Double, Double) = (0, 0, 0)
        let topColor = mix(ink.screen, white, 0.04)
        let bottomColor = mix(ink.screen, black, ink == .dark ? 0.55 : 0.12)
        let space = SeiraBitmap.colorSpace
        if let gradient = CGGradient(colorsSpace: space, colors: [topColor, bottomColor] as CFArray, locations: [0, 1]) {
            c.saveGState()
            c.clip(to: area)
            c.drawLinearGradient(gradient, start: CGPoint(x: 0, y: 0), end: CGPoint(x: 0, y: height), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            c.restoreGState()
        }
        let clear = CGColor(colorSpace: space, components: [0, 0, 0, 0]) ?? CGColor(gray: 0, alpha: 0)
        let shade = CGColor(colorSpace: space, components: [0, 0, 0, ink == .dark ? 0.5 : 0.1]) ?? CGColor(gray: 0, alpha: 0.5)
        if let vignette = CGGradient(colorsSpace: space, colors: [clear, shade] as CFArray, locations: [0, 1]) {
            c.saveGState()
            c.clip(to: area)
            c.drawRadialGradient(
                vignette, startCenter: CGPoint(x: width / 2, y: height * 0.45), startRadius: height * 0.32,
                endCenter: CGPoint(x: width / 2, y: height * 0.5), endRadius: width * 0.62,
                options: [.drawsAfterEndLocation]
            )
            c.restoreGState()
        }
    }

    private static func drawGloss(_ c: CGContext) {
        let space = SeiraBitmap.colorSpace
        let top = CGColor(colorSpace: space, components: [1, 1, 1, 0.10]) ?? CGColor(gray: 1, alpha: 0.1)
        let bottom = CGColor(colorSpace: space, components: [1, 1, 1, 0]) ?? CGColor(gray: 1, alpha: 0)
        guard let gradient = CGGradient(colorsSpace: space, colors: [top, bottom] as CFArray, locations: [0, 1]) else { return }
        c.saveGState()
        c.addPath(CGPath(ellipseIn: CGRect(x: width * 0.08, y: height * 0.06 - height * 0.24, width: width * 0.84, height: height * 0.48), transform: nil))
        c.clip()
        c.drawLinearGradient(gradient, start: CGPoint(x: 0, y: height * 0.02), end: CGPoint(x: 0, y: height * 0.3), options: [])
        c.restoreGState()
    }

    // MARK: Paths

    private static func fillPath(_ c: CGContext, _ path: CGPath) {
        c.addPath(path)
        c.fillPath()
    }

    private static func strokePath(_ c: CGContext, _ path: CGPath, width lineWidth: CGFloat) {
        c.setLineWidth(lineWidth)
        c.addPath(path)
        c.strokePath()
    }

    /// A circular arc. `anticlockwise` follows the canvas arc: false turns toward increasing angles.
    private static func arc(_ p: CGMutablePath, _ x: CGFloat, _ y: CGFloat, _ r: CGFloat, _ a0: CGFloat, _ a1: CGFloat, anticlockwise: Bool = false) {
        p.addArc(center: CGPoint(x: x, y: y), radius: r, startAngle: a0, endAngle: a1, clockwise: anticlockwise)
    }

    private static func roundRect(_ rect: CGRect, radius: CGFloat) -> CGPath {
        let r = min(radius, rect.width / 2, rect.height / 2)
        return CGPath(roundedRect: rect, cornerWidth: r, cornerHeight: r, transform: nil)
    }

    private static func heartPath(_ x: CGFloat, _ y: CGFloat, _ s: CGFloat) -> CGPath {
        let p = CGMutablePath()
        p.move(to: CGPoint(x: x, y: y + s * 0.62))
        p.addCurve(to: CGPoint(x: x - s * 0.62, y: y - s * 0.24), control1: CGPoint(x: x - s * 0.15, y: y + s * 0.5), control2: CGPoint(x: x - s * 0.72, y: y + s * 0.12))
        p.addCurve(to: CGPoint(x: x, y: y - s * 0.26), control1: CGPoint(x: x - s * 0.52, y: y - s * 0.62), control2: CGPoint(x: x - s * 0.08, y: y - s * 0.6))
        p.addCurve(to: CGPoint(x: x + s * 0.62, y: y - s * 0.24), control1: CGPoint(x: x + s * 0.08, y: y - s * 0.6), control2: CGPoint(x: x + s * 0.52, y: y - s * 0.62))
        p.addCurve(to: CGPoint(x: x, y: y + s * 0.62), control1: CGPoint(x: x + s * 0.72, y: y + s * 0.12), control2: CGPoint(x: x + s * 0.15, y: y + s * 0.5))
        p.closeSubpath()
        return p
    }

    /// A rounded mouth with a flat top: the open mouth and the talking mouth.
    private static func dShape(_ x: CGFloat, _ y: CGFloat, hw: CGFloat, top: CGFloat, bottom: CGFloat) -> CGPath {
        let p = CGMutablePath()
        p.move(to: CGPoint(x: x - hw, y: y + top))
        p.addLine(to: CGPoint(x: x + hw, y: y + top))
        p.addCurve(
            to: CGPoint(x: x, y: y + bottom),
            control1: CGPoint(x: x + hw, y: y + top + (bottom - top) * 0.75),
            control2: CGPoint(x: x + hw * 0.55, y: y + bottom)
        )
        p.addCurve(
            to: CGPoint(x: x - hw, y: y + top),
            control1: CGPoint(x: x - hw * 0.55, y: y + bottom),
            control2: CGPoint(x: x - hw, y: y + top + (bottom - top) * 0.75)
        )
        p.closeSubpath()
        return p
    }

    private static func wave(_ x0: CGFloat, _ x1: CGFloat, _ y: CGFloat, amp: CGFloat, periods: CGFloat) -> CGPath {
        let p = CGMutablePath()
        for k in 0...48 {
            let t = CGFloat(k) / 48
            let point = CGPoint(x: x0 + (x1 - x0) * t, y: y + sin(t * periods * pi * 2) * amp)
            if k == 0 { p.move(to: point) } else { p.addLine(to: point) }
        }
        return p
    }

    // MARK: Eyes

    private static func drawEye(_ c: CGContext, _ input: SeiraFaceInput, index i: Int, ex: CGFloat, ey: CGFloat) {
        let spec = input.spec
        let out: CGFloat = i == 0 ? -1 : 1
        let s = CGFloat(i == 0 ? spec.scaleLeft : spec.scaleRight)
        let x = centerXs[i] + ex + out * (s - 1) * eyeWidth * 0.15
        let cy = centerY + ey
        let named = (i == 0 ? spec.eyesLeft : spec.eyesRight) ?? spec.eyes
        let mode = named == "base" ? "pill" : named
        let topEdge: CGFloat
        if mode == "pill" {
            topEdge = baseEye(c, input, out: out, x: x, cy: cy, scale: s)
        } else {
            topEdge = modeEye(c, mode, out: out, x: x, cy: cy, scale: s)
        }
        if spec.brow != 0 {
            drawBrow(c, out: out, x: x, top: topEdge, width: eyeWidth * s, tilt: CGFloat(spec.brow))
        }
        if spec.blush > 0.02 {
            drawBlush(c, x: centerXs[i] + out * eyeWidth * 0.25 + ex * 0.5, y: centerY + eyeHeight * 0.56 + ey * 0.5, amount: CGFloat(spec.blush))
        }
        if spec.tears > 0.02 {
            drawTears(c, x: x, y: centerY + eyeHeight * 0.12 + ey, out: out, amount: CGFloat(spec.tears), time: input.time)
        }
    }

    /// The pill eye with lids, blink, highlight and lash. Returns the top edge for the brow.
    private static func baseEye(_ c: CGContext, _ input: SeiraFaceInput, out: CGFloat, x: CGFloat, cy: CGFloat, scale s: CGFloat) -> CGFloat {
        let spec = input.spec
        let w = eyeWidth * s
        let fullHeight = eyeHeight * s
        let open = CGFloat(1 - input.blink * 0.92)
        let h = max(fib[4] * u, fullHeight * open)
        let lidIn = CGFloat(spec.lidInner), lidOut = CGFloat(spec.lidOuter), lidLow = CGFloat(spec.lidLow)
        let closedish = open < 0.35
        let top = cy - h / 2
        let yi = top + (closedish ? 0 : lidIn * h)
        let yo = top + (closedish ? 0 : lidOut * h)
        let xi = x - out * w / 2
        let xo = x + out * w / 2
        let cut = !closedish && (lidIn > 0 || lidOut > 0 || lidLow > 0)

        c.saveGState()
        if cut {
            let bottom = cy + h / 2
            let edge = bottom - lidLow * h * 0.25
            let mid = bottom - lidLow * h * 1.9
            let lineY: (CGFloat) -> CGFloat = { px in yi + (yo - yi) * (px - xi) / (xo - xi) }
            let clip = CGMutablePath()
            clip.move(to: CGPoint(x: x - w, y: lineY(x - w)))
            clip.addLine(to: CGPoint(x: x + w, y: lineY(x + w)))
            clip.addLine(to: CGPoint(x: x + w, y: edge))
            clip.addQuadCurve(to: CGPoint(x: x - w, y: edge), control: CGPoint(x: x, y: mid))
            clip.closeSubpath()
            c.addPath(clip)
            c.clip()
        }
        fillPath(c, roundRect(CGRect(x: x - w / 2, y: cy - h / 2, width: w, height: h), radius: min(w, h) / 2))
        if !closedish {
            let visibleTop = min(yi, yo) + abs(yi - yo) * 0.5
            highlight(c, x: x, top: max(top, visibleTop - fib[3] * u), width: w, out: out, radius: fib[6] * u / 2)
        }
        c.restoreGState()

        if closedish {
            lashFlick(c, x: xo, y: cy, out: out, up: 0.6)
        } else {
            lashWing(c, x: xo, y: yo, out: out)
        }
        return cut ? min(yi, yo) : top
    }

    private static func highlight(_ c: CGContext, x: CGFloat, top: CGFloat, width w: CGFloat, out: CGFloat, radius r: CGFloat) {
        c.saveGState()
        c.setShadow(offset: .zero, blur: 0, color: nil)
        c.setFillColor(CGColor(gray: 1, alpha: 1))
        let cx = x - out * max(0, w / 2 - fib[5] * u - r)
        let cy = top + fib[5] * u + r
        c.fillEllipse(in: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2))
        c.restoreGState()
    }

    private static func lashWing(_ c: CGContext, x: CGFloat, y: CGFloat, out: CGFloat) {
        let p = CGMutablePath()
        p.move(to: CGPoint(x: x - out * 5 * u, y: y + 3 * u))
        p.addQuadCurve(to: CGPoint(x: x + out * 8 * u, y: y - 1 * u), control: CGPoint(x: x + out * 2 * u, y: y - 0.5 * u))
        p.addQuadCurve(to: CGPoint(x: x, y: y + fib[7] * u), control: CGPoint(x: x + out * 3 * u, y: y + 5 * u))
        p.closeSubpath()
        fillPath(c, p)
    }

    private static func lashFlick(_ c: CGContext, x: CGFloat, y: CGFloat, out: CGFloat, up: CGFloat) {
        let p = CGMutablePath()
        p.move(to: CGPoint(x: x, y: y))
        p.addQuadCurve(to: CGPoint(x: x + out * 5 * u, y: y - up * 4 * u), control: CGPoint(x: x + out * 3 * u, y: y - up * 1 * u))
        strokePath(c, p, width: 2 * u)
    }

    /// An eye drawn as a mark. Returns the top edge for the brow.
    private static func modeEye(_ c: CGContext, _ mode: String, out: CGFloat, x: CGFloat, cy: CGFloat, scale s: CGFloat) -> CGFloat {
        let w = eyeWidth * s
        let h = eyeHeight * s
        c.saveGState()
        switch mode {
        case "arc-up":
            let r = w * 0.56
            let yc = cy + h * 0.12
            let p = CGMutablePath()
            arc(p, x, yc, r, pi * 1.12, pi * 1.88)
            strokePath(c, p, width: 3 * u)
            lashFlick(c, x: x + out * r * cos(pi * 0.12), y: yc - r * sin(pi * 0.12), out: out, up: 1)
        case "arc-down":
            let r = w * 0.56
            let yc = cy - h * 0.08
            let p = CGMutablePath()
            arc(p, x, yc, r, pi * 0.12, pi * 0.88)
            strokePath(c, p, width: 3 * u)
            lashFlick(c, x: x + out * r * cos(pi * 0.12), y: yc + r * sin(pi * 0.12), out: out, up: 0.8)
        case "chevron":
            let a = w * 0.42
            let b = h * 0.26
            let p = CGMutablePath()
            p.move(to: CGPoint(x: x + out * a, y: cy - b))
            p.addLine(to: CGPoint(x: x - out * a * 0.7, y: cy))
            p.addLine(to: CGPoint(x: x + out * a, y: cy + b))
            strokePath(c, p, width: 3 * u)
        case "heart":
            fillPath(c, heartPath(x, cy, w * 1.15))
            c.setShadow(offset: .zero, blur: 0, color: nil)
            c.setFillColor(CGColor(gray: 1, alpha: 1))
            let dot = fib[5] * u / 2 + 3
            c.fillEllipse(in: CGRect(x: x - out * w * 0.28 - dot, y: cy - w * 0.2 - dot, width: dot * 2, height: dot * 2))
        case "round-big":
            let r = w * 0.66
            c.fillEllipse(in: CGRect(x: x - r, y: cy - r, width: r * 2, height: r * 2))
            let hr = fib[6] * u / 2
            highlight(c, x: x + (-out) * r * 0.22 + out * max(0, r - fib[5] * u - hr), top: cy - r + r * 0.12, width: r * 2, out: out, radius: hr)
            lashWing(c, x: x + out * r * 0.72, y: cy - r * 0.7, out: out)
        case "small":
            let sw = fib[6] * u
            let sh = fib[7] * u
            fillPath(c, roundRect(CGRect(x: x - sw / 2, y: cy - sh / 2, width: sw, height: sh), radius: sw / 2))
            highlight(c, x: x, top: cy - sh / 2 - fib[4] * u, width: sw, out: out, radius: fib[5] * u / 2 - 2)
        case "line":
            let p = CGMutablePath()
            p.move(to: CGPoint(x: x - w * 0.5, y: cy))
            p.addLine(to: CGPoint(x: x + w * 0.5, y: cy))
            strokePath(c, p, width: 3 * u)
        default:
            c.fillEllipse(in: CGRect(x: x - fib[5] * u * 0.7, y: cy - fib[5] * u * 0.7, width: fib[5] * u * 1.4, height: fib[5] * u * 1.4))
        }
        c.restoreGState()
        return cy - h / 2
    }

    // MARK: Mouth

    private static func drawMouth(_ c: CGContext, _ input: SeiraFaceInput, x: CGFloat, y: CGFloat) {
        let spec = input.spec
        c.setLineWidth(2 * u)
        if spec.dots {
            let active = Int(floor(input.time * 3)) % 3
            for k in 0..<3 {
                let on = active == k
                let radius = (on ? 1.5 : 1.2) * u
                c.saveGState()
                c.setAlpha(on ? 1 : 0.35)
                c.fillEllipse(in: CGRect(x: x + CGFloat(k - 1) * 4 * u - radius, y: y - radius, width: radius * 2, height: radius * 2))
                c.restoreGState()
            }
            return
        }
        let lineMouths: Set<String> = ["smile", "cat", "flat", "smirk", "firm", "flat-side", "wavy", "wavy-small", "frown", "tiny-o", "small-o"]
        if input.talk > 0.06 && lineMouths.contains(spec.mouth) {
            let k = CGFloat(input.talk)
            fillPath(c, dShape(x, y, hw: (3.5 + 2.5 * k) * u, top: -1.5 * u, bottom: (1 + 7 * k) * u))
            return
        }
        let p = CGMutablePath()
        switch spec.mouth {
        case "flat":
            p.move(to: CGPoint(x: x - 5 * u, y: y))
            p.addLine(to: CGPoint(x: x + 5 * u, y: y))
            strokePath(c, p, width: 2 * u)
        case "firm":
            p.move(to: CGPoint(x: x - 4 * u, y: y))
            p.addLine(to: CGPoint(x: x + 4 * u, y: y))
            strokePath(c, p, width: 2.5 * u)
        case "flat-side":
            p.move(to: CGPoint(x: x + 0.5 * u, y: y + 0.6 * u))
            p.addLine(to: CGPoint(x: x + 7 * u, y: y - 0.6 * u))
            strokePath(c, p, width: 2 * u)
        case "small-o":
            arc(p, x, y + 0.5 * u, 2.6 * u, 0, pi * 2)
            strokePath(c, p, width: 2 * u)
        case "smirk":
            p.move(to: CGPoint(x: x - 5 * u, y: y + 1.5 * u))
            p.addQuadCurve(to: CGPoint(x: x + 5.5 * u, y: y - 2 * u), control: CGPoint(x: x + 1 * u, y: y + 3.5 * u))
            strokePath(c, p, width: 2 * u)
        case "open":
            fillPath(c, dShape(x, y, hw: 5 * u, top: -2 * u, bottom: 5 * u))
        case "open-big":
            fillPath(c, dShape(x, y, hw: 8 * u, top: -3 * u, bottom: 9 * u))
        case "open-small":
            fillPath(c, dShape(x, y, hw: 3.2 * u, top: -1 * u, bottom: 3.5 * u))
        case "frown":
            arc(p, x, y + 6 * u, 6 * u, pi * 1.24, pi * 1.76)
            strokePath(c, p, width: 2 * u)
        case "wavy":
            strokePath(c, wave(x - 6 * u, x + 6 * u, y, amp: 1.3 * u, periods: 2), width: 2 * u)
        case "wavy-small":
            strokePath(c, wave(x - 4 * u, x + 4 * u, y, amp: 1 * u, periods: 1.5), width: 2 * u)
        case "tiny-o":
            c.fillEllipse(in: CGRect(x: x - 1.5 * u, y: y + 1 * u - 1.5 * u, width: 3 * u, height: 3 * u))
        case "cat":
            arc(p, x - 3 * u, y - 1.5 * u, 3 * u, pi * 0.08, pi * 0.95)
            p.move(to: CGPoint(x: x + 3 * u + 3 * u * cos(pi * 0.05), y: y - 1.5 * u + 3 * u * sin(pi * 0.05)))
            arc(p, x + 3 * u, y - 1.5 * u, 3 * u, pi * 0.05, pi * 0.92)
            strokePath(c, p, width: 2 * u)
        default:
            arc(p, x, y - 4 * u, 7 * u, pi * 0.2, pi * 0.8)
            strokePath(c, p, width: 2 * u)
        }
    }

    // MARK: Marks

    private static func drawBrow(_ c: CGContext, out: CGFloat, x: CGFloat, top: CGFloat, width w: CGFloat, tilt: CGFloat) {
        let yb = top - 5 * u
        let xi = x - out * w * 0.6
        let xo = x + out * w * 0.6
        let p = CGMutablePath()
        p.move(to: CGPoint(x: xi, y: yb + tilt * 3.5 * u))
        p.addQuadCurve(to: CGPoint(x: xo, y: yb - tilt * 2.5 * u), control: CGPoint(x: x, y: yb - 1.5 * u))
        c.saveGState()
        strokePath(c, p, width: 1.5 * u)
        c.restoreGState()
    }

    private static func drawBlush(_ c: CGContext, x: CGFloat, y: CGFloat, amount: CGFloat) {
        c.saveGState()
        c.setAlpha(min(1, amount))
        c.setShadow(offset: .zero, blur: 0, color: nil)
        let space = SeiraBitmap.colorSpace
        let center = CGColor(colorSpace: space, components: [1, 110.0 / 255, 150.0 / 255, 0.32]) ?? CGColor(gray: 1, alpha: 0.3)
        let edge = CGColor(colorSpace: space, components: [1, 110.0 / 255, 150.0 / 255, 0]) ?? CGColor(gray: 1, alpha: 0)
        if let gradient = CGGradient(colorsSpace: space, colors: [center, edge] as CFArray, locations: [0, 1]) {
            c.drawRadialGradient(gradient, startCenter: CGPoint(x: x, y: y), startRadius: 0, endCenter: CGPoint(x: x, y: y), endRadius: 8 * u, options: [])
        }
        c.setStrokeColor(seiraCGColor("#FF7FA3"))
        c.setLineWidth(1.2 * u)
        for n in -1...1 {
            let bx = x + CGFloat(n) * 2.6 * u
            c.move(to: CGPoint(x: bx - 1 * u, y: y + 1.6 * u))
            c.addLine(to: CGPoint(x: bx + 1 * u, y: y - 1.6 * u))
            c.strokePath()
        }
        c.restoreGState()
    }

    private static func drawTears(_ c: CGContext, x: CGFloat, y: CGFloat, out: CGFloat, amount: CGFloat, time: Double) {
        let length = fib[7] * u * amount
        let tx = x + out * 1.5 * u
        c.saveGState()
        c.setAlpha(0.9)
        c.setLineWidth(2.2 * u)
        c.move(to: CGPoint(x: tx, y: y))
        c.addLine(to: CGPoint(x: tx, y: y + length))
        c.strokePath()
        let drift = CGFloat((time * 1.7).truncatingRemainder(dividingBy: 1)) * fib[7] * u
        let radius = 1.6 * u
        c.fillEllipse(in: CGRect(x: tx - radius, y: y + length * 0.4 + drift - radius, width: radius * 2, height: radius * 2))
        c.restoreGState()
    }

    private static func drawSweat(_ c: CGContext, x: CGFloat, y: CGFloat, scale s: CGFloat) {
        c.saveGState()
        c.setFillColor(seiraCGColor("#9FE7FF"))
        let p = CGMutablePath()
        p.move(to: CGPoint(x: x, y: y - 2.5 * u * s))
        p.addQuadCurve(to: CGPoint(x: x, y: y + 1.5 * u * s), control: CGPoint(x: x + 1.8 * u * s, y: y + 0.2 * u * s))
        p.addQuadCurve(to: CGPoint(x: x, y: y - 2.5 * u * s), control: CGPoint(x: x - 1.8 * u * s, y: y + 0.2 * u * s))
        fillPath(c, p)
        c.restoreGState()
    }
}
