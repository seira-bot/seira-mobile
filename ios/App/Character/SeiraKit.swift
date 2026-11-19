// Loads the character kit that scripts/sync-seira-character.mjs copies into the SeiraKit resource folder.
// The folder sits in the app bundle and in the widgets extension bundle.
import CoreGraphics
import Foundation
import ImageIO

struct SeiraKitView: Decodable {
    let file: String
    let hair: String?
    let visor: String?
    /// Head pivot in frame pixels. The origin of the world space.
    let pivot: [Double]
    /// Head radius in frame pixels.
    let r: Double
    /// x, y, width, height of the visor in frame pixels.
    let visorBox: [Double]
    let measuredYaw: Double
    let anchors: [String: [Double]]
}

struct SeiraKitStyle: Decodable {
    let label: String
    let recolor: Bool
    let views: [String: SeiraKitView]
}

private struct SeiraKitFile: Decodable {
    let styles: [String: SeiraKitStyle]
}

struct SeiraKitAccessory: Decodable {
    let slot: String
    let anchor: String?
    let file: String
    let pivot: [Double]
    let width: Double?
    let size: [Double]
    let pivots: [String: [Double]]?
    let widths: [String: Double]?
    let views: [String]?
}

struct SeiraKitStylePlacement: Decodable {
    /// Head radius in the 240 unit square of the carved avatar.
    let r: Double
    let anchors: [String: [Double]]
}

struct SeiraKitPlacement: Decodable {
    let styles: [String: SeiraKitStylePlacement]
    let accessories: [String: SeiraKitAccessory]
}

struct SeiraKitEyes: Decodable {
    let cx: Double
    let cy: Double
    let dx: Double
    let w: Double
    let h: Double
    let round: Double
    let ink: String
    let mouth: Bool
    let mouthInk: String
    let mouthY: Double?
}

struct SeiraKitBodySizes: Decodable {
    let front: [Int]
    let hair: [Int]
}

/// The carved avatar head of one hair style.
struct SeiraKitBody: Decodable {
    let name: String
    let paint: String
    let bone: String?
    let eyes: SeiraKitEyes
    /// SVG path of the visor in the 240 unit square.
    let clip: String
    let hairExt: String?
    let sizes: SeiraKitBodySizes
}

enum SeiraKit {
    private static let folder = "SeiraKit"

    static let styles: [String: SeiraKitStyle] = decode("kit.json", as: SeiraKitFile.self)?.styles ?? [:]
    static let placement: SeiraKitPlacement? = decode("placement.json", as: SeiraKitPlacement.self)
    static let bodies: [String: SeiraKitBody] = decode("carved/bodies.json", as: [String: SeiraKitBody].self) ?? [:]

    static func url(_ path: String) -> URL? {
        Bundle.main.resourceURL?.appendingPathComponent(folder).appendingPathComponent(path)
    }

    private static func decode<T: Decodable>(_ path: String, as type: T.Type) -> T? {
        guard let url = url(path), let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }

    private static let images = NSCache<NSString, CGImage>()
    private static let sizeLock = NSLock()
    private static var sizes: [String: CGSize] = [:]

    /// The pixel size of a file in the kit.
    static func naturalSize(_ path: String) -> CGSize? {
        sizeLock.lock()
        defer { sizeLock.unlock() }
        if let known = sizes[path] { return known }
        guard let url = url(path), let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let info = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let width = info[kCGImagePropertyPixelWidth] as? Int,
              let height = info[kCGImagePropertyPixelHeight] as? Int else { return nil }
        let size = CGSize(width: width, height: height)
        sizes[path] = size
        return size
    }

    /// A kit picture, downscaled so its longest side is at most `maxPixel`. It never scales up.
    static func image(_ path: String, maxPixel: Int) -> CGImage? {
        let key = "\(path)@\(maxPixel)" as NSString
        if let cached = images.object(forKey: key) { return cached }
        guard let url = url(path), let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let natural = naturalSize(path) else { return nil }
        let longest = Int(max(natural.width, natural.height))
        let result: CGImage?
        if maxPixel >= longest {
            result = CGImageSourceCreateImageAtIndex(source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)
        } else {
            let options: [CFString: Any] = [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceShouldCacheImmediately: true,
                kCGImageSourceThumbnailMaxPixelSize: maxPixel,
            ]
            result = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary)
        }
        if let result { images.setObject(result, forKey: key) }
        return result
    }

    /// Rounds a pixel size up to a step, so a view that grows by a point does not decode a new picture.
    static func bucket(_ pixels: CGFloat) -> Int {
        let step: CGFloat = 32
        return Int(max(step, (pixels / step).rounded(.up) * step))
    }
}

/// Pixels of a picture in 8 bit RGBA with premultiplied alpha.
struct SeiraBitmap {
    let width: Int
    let height: Int
    var data: [UInt8]

    static let colorSpace: CGColorSpace = CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB()
    private static let info = CGImageAlphaInfo.premultipliedLast.rawValue

    /// Draws `image` into a new bitmap of `width` x `height`. Without a size, the picture keeps its own.
    init?(_ image: CGImage, width: Int? = nil, height: Int? = nil) {
        let w = width ?? image.width
        let h = height ?? image.height
        guard w > 0, h > 0 else { return nil }
        var buffer = [UInt8](repeating: 0, count: w * h * 4)
        let drawn = buffer.withUnsafeMutableBytes { raw -> Bool in
            guard let context = CGContext(
                data: raw.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                space: SeiraBitmap.colorSpace, bitmapInfo: SeiraBitmap.info
            ) else { return false }
            context.interpolationQuality = .high
            context.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
            return true
        }
        guard drawn else { return nil }
        self.width = w
        self.height = h
        self.data = buffer
    }

    func makeImage() -> CGImage? {
        guard let provider = CGDataProvider(data: Data(data) as CFData) else { return nil }
        return CGImage(
            width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: width * 4,
            space: SeiraBitmap.colorSpace, bitmapInfo: CGBitmapInfo(rawValue: SeiraBitmap.info),
            provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent
        )
    }
}

extension CGContext {
    /// Draws a picture upright inside a context whose y axis points down.
    func drawUpright(_ image: CGImage, in rect: CGRect) {
        saveGState()
        translateBy(x: rect.minX, y: rect.maxY)
        scaleBy(x: 1, y: -1)
        draw(image, in: CGRect(x: 0, y: 0, width: rect.width, height: rect.height))
        restoreGState()
    }
}

/// Reads `#RGB` or `#RRGGBB` as red, green and blue in 0...255.
func seiraRGB(_ hex: String) -> (Double, Double, Double)? {
    var digits = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
    if digits.count == 3 { digits = digits.map { "\($0)\($0)" }.joined() }
    guard digits.count == 6, let value = UInt32(digits, radix: 16) else { return nil }
    return (Double((value >> 16) & 255), Double((value >> 8) & 255), Double(value & 255))
}

func seiraCGColor(_ hex: String, alpha: CGFloat = 1) -> CGColor {
    let rgb = seiraRGB(hex) ?? (0, 0, 0)
    return CGColor(
        colorSpace: SeiraBitmap.colorSpace,
        components: [CGFloat(rgb.0 / 255), CGFloat(rgb.1 / 255), CGFloat(rgb.2 / 255), alpha]
    ) ?? CGColor(gray: 0, alpha: alpha)
}
