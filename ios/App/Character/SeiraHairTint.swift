// Hair colour. The two functions mirror the desktop runtime: frameHair is recolor.js (frame kit), and
// carvedHead is the multiply tint and the grey hair gradient map of CarvedAvatar and seira-hair.ts (avatar heads).
import CoreGraphics
import Foundation

enum SeiraHairTint {
    private static let cache = NSCache<NSString, CGImage>()

    // MARK: Frame kit

    /// The frame with its hair painted in `hex`. Shading and highlights of the source stay.
    /// A recolor style has flat magenta hair. A native style (Seira) has silver hair.
    static func frameHair(_ frame: CGImage, mask: CGImage?, hex: String, key: String) -> CGImage {
        let cacheKey = "frame|\(key)|\(hex)|\(frame.width)" as NSString
        if let hit = cache.object(forKey: cacheKey) { return hit }
        guard let mask, let target = seiraRGB(hex),
              var pixels = SeiraBitmap(frame), let maskPixels = SeiraBitmap(mask, width: frame.width, height: frame.height)
        else { return frame }

        let count = pixels.width * pixels.height
        var shades: [Float] = []
        var i = 0
        while i < count * 4 {
            defer { i += 64 }
            let alpha = pixels.data[i + 3]
            if alpha < 200 { continue }
            let (r, g, b) = straight(pixels.data, i)
            if Float(maskPixels.data[i]) / 255 > 0.9 {
                let magenta = r - g > 60 && b - g > 60
                shades.append(magenta ? max(r, b) : 0.3 * r + 0.59 * g + 0.11 * b)
            }
        }
        let reference = max(1, median(shades))
        let tr = Float(target.0), tg = Float(target.1), tb = Float(target.2)

        pixels.data.withUnsafeMutableBufferPointer { buffer in
            var p = 0
            while p < count * 4 {
                defer { p += 4 }
                let alpha = buffer[p + 3]
                if alpha == 0 { continue }
                let weight = Float(maskPixels.data[p]) / 255
                if weight <= 0 { continue }
                let (r, g, b) = straight(buffer, p)
                let magenta = r - g > 40 && b - g > 40
                let shade: Float
                let spec: Float
                if magenta {
                    shade = max(r, b) / reference
                    spec = min(1, g / max(1, max(r, b))) * 0.9
                } else {
                    let luminance = 0.3 * r + 0.59 * g + 0.11 * b
                    shade = luminance / reference
                    spec = max(0, (luminance - reference) / max(1, 255 - reference)) * 0.55
                }
                let cr = min(255, tr * shade), cg = min(255, tg * shade), cb = min(255, tb * shade)
                let hr = cr + (255 - cr) * spec, hg = cg + (255 - cg) * spec, hb = cb + (255 - cb) * spec
                store(&buffer, p, r + (hr - r) * weight, g + (hg - g) * weight, b + (hb - b) * weight, alpha)
            }
        }
        guard let image = pixels.makeImage() else { return frame }
        cache.setObject(image, forKey: cacheKey)
        return image
    }

    // MARK: Carved avatar

    /// Raises saturation by 15 percent, as CarvedAvatar does for the flat style.
    static func vivid(_ hex: String) -> (Double, Double, Double)? {
        guard let rgb = seiraRGB(hex) else { return nil }
        let r = rgb.0 / 255, g = rgb.1 / 255, b = rgb.2 / 255
        let maxC = max(r, g, b), minC = min(r, g, b)
        let l = (maxC + minC) / 2
        let d = maxC - minC
        if d == 0 { return rgb }
        let sBase = l > 0.5 ? d / (2 - maxC - minC) : d / (maxC + minC)
        let s = min(1, sBase * 1.15)
        var h: Double
        if maxC == r { h = (g - b) / d + (g < b ? 6 : 0) } else if maxC == g { h = (b - r) / d + 2 } else { h = (r - g) / d + 4 }
        h /= 6
        let q = l < 0.5 ? l * (1 + s) : l + s - l * s
        let p = 2 * l - q
        func channel(_ x: Double) -> Double {
            let t = (x + 1).truncatingRemainder(dividingBy: 1)
            if t < 1.0 / 6 { return p + (q - p) * 6 * t }
            if t < 0.5 { return q }
            if t < 2.0 / 3 { return p + (q - p) * (2.0 / 3 - t) * 6 }
            return p
        }
        return (channel(h + 1.0 / 3) * 255, channel(h) * 255, channel(h - 1.0 / 3) * 255)
    }

    /// The carved head with its hair in `hex`. A nil `hex` keeps the hair as rendered (silver and chrome).
    /// Seira's hair mask multiplies the tint over the head. Every other style has a grey hair picture that a
    /// gradient map turns into the tint, with highlights, and that goes over the head.
    static func carvedHead(head: CGImage, hair: CGImage?, hex: String?, seira: Bool, key: String) -> CGImage {
        guard let hex, let hair, let tint = vivid(hex) else { return head }
        let cacheKey = "carved|\(key)|\(hex)|\(head.width)" as NSString
        if let hit = cache.object(forKey: cacheKey) { return hit }
        guard var pixels = SeiraBitmap(head) else { return head }
        let result: CGImage?
        if seira {
            guard let mask = SeiraBitmap(hair, width: head.width, height: head.height) else { return head }
            multiply(&pixels, mask: mask, tint: tint)
            result = pixels.makeImage()
        } else {
            guard var overlay = SeiraBitmap(hair, width: head.width, height: head.height) else { return head }
            gradientMap(&overlay, tint: tint)
            result = composite(&pixels, over: overlay)
        }
        guard let image = result else { return head }
        cache.setObject(image, forKey: cacheKey)
        return image
    }

    private static func multiply(_ pixels: inout SeiraBitmap, mask: SeiraBitmap, tint: (Double, Double, Double)) {
        let tr = Float(tint.0 / 255), tg = Float(tint.1 / 255), tb = Float(tint.2 / 255)
        let count = pixels.width * pixels.height
        for n in 0..<count {
            let p = n * 4
            let weight = Float(mask.data[p]) / 255
            if weight <= 0 || pixels.data[p + 3] == 0 { continue }
            pixels.data[p] = UInt8(clamping: Int(Float(pixels.data[p]) * (1 + (tr - 1) * weight)))
            pixels.data[p + 1] = UInt8(clamping: Int(Float(pixels.data[p + 1]) * (1 + (tg - 1) * weight)))
            pixels.data[p + 2] = UInt8(clamping: Int(Float(pixels.data[p + 2]) * (1 + (tb - 1) * weight)))
        }
    }

    /// The grey hair stores shade / 1.2. Above shade 1 a highlight rises to 0.3, as in recolor.js.
    private static func gradientMap(_ overlay: inout SeiraBitmap, tint: (Double, Double, Double)) {
        let shadeMax: Float = 1.2
        let specMax: Float = 0.3
        let target: [Float] = [Float(tint.0 / 255), Float(tint.1 / 255), Float(tint.2 / 255)]
        let count = overlay.width * overlay.height
        for n in 0..<count {
            let p = n * 4
            let alpha = overlay.data[p + 3]
            if alpha == 0 { continue }
            let (r, g, b) = straight(overlay.data, p)
            let source: [Float] = [r / 255, g / 255, b / 255]
            for c in 0..<3 {
                let shade = source[c] * shadeMax
                let spec = specMax * max(0, min(1, (shade - 1) / (shadeMax - 1)))
                let value = min(1, target[c] * shade) * (1 - spec) + spec
                overlay.data[p + c] = UInt8(clamping: Int((value * Float(alpha)).rounded()))
            }
        }
    }

    private static func composite(_ base: inout SeiraBitmap, over overlay: SeiraBitmap) -> CGImage? {
        let count = base.width * base.height
        for n in 0..<count {
            let p = n * 4
            let inverse = 255 - Int(overlay.data[p + 3])
            for c in 0..<4 {
                let value = Int(overlay.data[p + c]) + Int(base.data[p + c]) * inverse / 255
                base.data[p + c] = UInt8(clamping: value)
            }
        }
        return base.makeImage()
    }

    // MARK: Pixel helpers

    private static func median(_ values: [Float]) -> Float {
        guard !values.isEmpty else { return 1 }
        let sorted = values.sorted()
        return sorted[sorted.count / 2]
    }

    /// Red, green and blue of a premultiplied pixel, as straight 0...255 values.
    private static func straight(_ data: [UInt8], _ i: Int) -> (Float, Float, Float) {
        straight(data[i], data[i + 1], data[i + 2], data[i + 3])
    }

    private static func straight(_ buffer: UnsafeMutableBufferPointer<UInt8>, _ i: Int) -> (Float, Float, Float) {
        straight(buffer[i], buffer[i + 1], buffer[i + 2], buffer[i + 3])
    }

    private static func straight(_ r: UInt8, _ g: UInt8, _ b: UInt8, _ a: UInt8) -> (Float, Float, Float) {
        if a == 255 || a == 0 { return (Float(r), Float(g), Float(b)) }
        let k = 255 / Float(a)
        return (min(255, Float(r) * k), min(255, Float(g) * k), min(255, Float(b) * k))
    }

    private static func store(_ buffer: inout UnsafeMutableBufferPointer<UInt8>, _ i: Int, _ r: Float, _ g: Float, _ b: Float, _ a: UInt8) {
        let k = Float(a) / 255
        buffer[i] = UInt8(clamping: Int((r * k).rounded()))
        buffer[i + 1] = UInt8(clamping: Int((g * k).rounded()))
        buffer[i + 2] = UInt8(clamping: Int((b * k).rounded()))
    }
}
