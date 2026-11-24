#!/usr/bin/env python3
"""Make the Seira app icon for Android, iOS and the Play Store.

Usage:
    python3 scripts/export-app-icon.py --render   # remake the foreground master and the monochrome vector
    python3 scripts/export-app-icon.py            # export every icon file from the master
    python3 scripts/export-app-icon.py --check    # verify the exported files and write nothing

The source is shared/icons3d/app-icon/head-tilt.png: the 3D head with the visor
switched off, keyed from a gpt-image-2.6 render (see shared/icons3d/prompts.md).
--render draws the brand face on the visor with the face.js geometry. It writes
shared/icons3d/app-icon/foreground.png and the monochrome vector drawable.

The master is the 108 dp Android layer at 1536 px. Its middle 72 dp (1024 px)
is the visible icon, so the iOS icon is the middle 1024 px of the composite.

Export and check need Pillow and numpy. --render also needs pycairo, scipy and
scikit-image.
"""

import argparse
import base64
import io
import json
import math
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

MASTER = 1536
LAYER_DP = 108
VISIBLE_DP = 72
SAFE_DP = 66
TILE = MASTER * VISIBLE_DP // LAYER_DP
IOS_SIZE = 1024
PLAY_SIZE = 512
DENSITIES = (("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4))

# Direction "Malam": an ink tile with red light from the visor screen.
BASE_COLOR = "#151925"
BG_CENTER, BG_RADIUS = (0.5, 0.42), 0.95
BG_STOPS = ((0.0, "#2B3043"), (0.55, "#151925"), (1.0, "#0A0C11"))
GLOW_CENTER, GLOW_RADIUS, GLOW_COLOR, GLOW_ALPHA = (0.52, 0.55), 0.75, "#FF2A57", 0.42
HEAD_HEIGHT, HEAD_CENTER = 0.80, (0.5, 0.52)
SHADOW_OPACITY, SHADOW_BLUR, SHADOW_DY = 0.55, 0.03, 0.03
FACE_EYES, FACE_MOUTH, FACE_SCALE = ("pill", "wink"), "smile-big", 1.36

# face.js geometry: unit U on a 1024 x 640 face canvas.
RED = (0xFF / 255, 0x2A / 255, 0x57 / 255)
U = 6
FW, FH = 1024, 640
EW, EH = 21 * U, 34 * U
GAP = 2 * 13 * U
CY = FH * 0.45
CX = (FW / 2 - GAP / 2 - EW / 2, FW / 2 + GAP / 2 + EW / 2)

TRACE_TOLERANCE_PX = 1.5
TRACE_MIN_AREA_DP2 = 0.25
SVG_PAYLOAD_RE = re.compile(r"(data:image/png;base64,)[A-Za-z0-9+/=]*")
COLOR_RE = re.compile(r'(<color name="ic_launcher_background">)[^<]*(</color>)')
ADAPTIVE = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""
IOS_FILES = (("icon-1024.png", None), ("icon-1024-dark.png", "dark"), ("icon-1024-tinted.png", "tinted"))


class ExportError(Exception):
    pass


def paths(root):
    res = root / "android" / "app" / "src" / "main" / "res"
    store = root / "android" / "PlayStore" / "assets"
    art = root / "shared" / "icons3d" / "app-icon"
    return {
        "head": art / "head-tilt.png",
        "master": art / "foreground.png",
        "ios": root / "ios" / "App" / "Assets.xcassets" / "AppIcon.appiconset",
        "res": res,
        "color": res / "values" / "ic_launcher_background.xml",
        "monochrome": res / "drawable" / "ic_launcher_monochrome.xml",
        "adaptive": [res / "mipmap-anydpi-v26" / "ic_launcher.xml",
                     res / "mipmap-anydpi-v26" / "ic_launcher_round.xml"],
        "play": store / "play-icon-512.png",
        "svg": store / "app-icon-source.svg",
    }


def hexrgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def bayer(n=8):
    m = np.zeros((1, 1))
    while m.shape[0] < n:
        m = np.block([[4 * m, 4 * m + 2], [4 * m + 3, 4 * m + 1]])
    return (m + 0.5) / m.size


BAYER = bayer()


def quantize(rgb):
    """Round a float image to 8 bits with an ordered dither, so dark gradients do not band."""
    h, w = rgb.shape[:2]
    t = np.tile(BAYER, (h // 8 + 1, w // 8 + 1))[:h, :w]
    if rgb.ndim == 3:
        t = t[..., None]
    return np.clip(np.floor(rgb + t), 0, 255).astype(np.uint8)


def tile_grid(size, extend):
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float64) + 0.5
    return (xx / size - 0.5) * extend + 0.5, (yy / size - 0.5) * extend + 0.5


def background(size, extend):
    """The tile background as float RGB. extend 1.0 is the visible tile, 1.5 is the 108 dp layer."""
    u, v = tile_grid(size, extend)
    d = np.clip(np.hypot(u - BG_CENTER[0], v - BG_CENTER[1]) / BG_RADIUS, 0, 1)
    out = np.empty((size, size, 3))
    for ch in range(3):
        out[..., ch] = np.interp(d, [p for p, _ in BG_STOPS], [hexrgb(c)[ch] for _, c in BG_STOPS])
    g = np.hypot(u - GLOW_CENTER[0], v - GLOW_CENTER[1]) / GLOW_RADIUS
    a = (np.clip(1 - g, 0, 1) ** 2 * GLOW_ALPHA)[..., None]
    return out * (1 - a) + np.array(hexrgb(GLOW_COLOR), dtype=np.float64) * a


def composite(bg, fg):
    fg = np.asarray(fg, dtype=np.float64)
    a = fg[..., 3:4] / 255
    return bg * (1 - a) + fg[..., :3] * a


def save_png(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=True)


def png_bytes(image):
    buffer = io.BytesIO()
    image.save(buffer, format="PNG", optimize=True)
    return buffer.getvalue()


# ---------------------------------------------------------------- render

def visor_of(head, ndimage):
    """Find the dark visor: soft mask, centre, width and tilt in degrees."""
    a = np.asarray(head, dtype=np.float32)
    dark = (a[..., :3].mean(-1) < 60) & (a[..., 3] > 200)
    lab, n = ndimage.label(dark)
    if n == 0:
        raise ExportError("The head render has no dark visor.")
    sizes = ndimage.sum(np.ones_like(lab), lab, range(1, n + 1))
    m = lab == (1 + int(np.argmax(sizes)))
    m = ndimage.binary_fill_holes(m)
    m = ndimage.binary_closing(m, iterations=6)
    m = ndimage.binary_fill_holes(m)
    ys, xs = np.nonzero(m)
    cx, cy = xs.mean(), ys.mean()
    w_, v_ = np.linalg.eigh(np.cov(np.vstack([xs - cx, ys - cy])))
    major = v_[:, np.argmax(w_)]
    ang = math.degrees(math.atan2(major[1], major[0]))
    if ang > 90:
        ang -= 180
    if ang < -90:
        ang += 180
    t = math.radians(ang)
    u = (xs - cx) * math.cos(t) + (ys - cy) * math.sin(t)
    v = -(xs - cx) * math.sin(t) + (ys - cy) * math.cos(t)
    u0, u1 = np.percentile(u, 0.5), np.percentile(u, 99.5)
    v0, v1 = np.percentile(v, 0.5), np.percentile(v, 99.5)
    cxo = cx + math.cos(t) * (u0 + u1) / 2 - math.sin(t) * (v0 + v1) / 2
    cyo = cy + math.sin(t) * (u0 + u1) / 2 + math.cos(t) * (v0 + v1) / 2
    soft = ndimage.gaussian_filter(ndimage.binary_erosion(m, iterations=3).astype(np.float32), 1.5)
    return {"mask": soft, "cx": cxo, "cy": cyo, "w": u1 - u0, "ang": ang}


def unpremultiply(surface):
    a = np.ndarray((surface.get_height(), surface.get_width(), 4), np.uint8, surface.get_data()).copy()
    b, g, r, al = (a[..., i].astype(np.float32) for i in range(4))
    k = np.where(al > 0, 255.0 / np.maximum(al, 1), 0)
    out = np.dstack([np.clip(r * k, 0, 255), np.clip(g * k, 0, 255), np.clip(b * k, 0, 255), al])
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def quad_to_cubic(p0, p1, p2):
    c1 = (p0[0] + 2 / 3 * (p1[0] - p0[0]), p0[1] + 2 / 3 * (p1[1] - p0[1]))
    c2 = (p2[0] + 2 / 3 * (p1[0] - p2[0]), p2[1] + 2 / 3 * (p1[1] - p2[1]))
    return c1[0], c1[1], c2[0], c2[1], p2[0], p2[1]


def draw_face(cairo):
    """Draw the face on a face.js canvas. Returns the red shapes and the white highlights."""
    shapes_surface = cairo.ImageSurface(cairo.FORMAT_ARGB32, FW, FH)
    lights_surface = cairo.ImageSurface(cairo.FORMAT_ARGB32, FW, FH)
    c, h = cairo.Context(shapes_surface), cairo.Context(lights_surface)
    for ctx in (c, h):
        ctx.set_line_cap(cairo.LINE_CAP_ROUND)
        ctx.set_line_join(cairo.LINE_JOIN_ROUND)
    c.set_source_rgb(*RED)
    h.set_source_rgb(1, 1, 1)
    for i, x in enumerate(CX):
        out = -1 if i == 0 else 1
        mode = FACE_EYES[i]
        if mode == "pill":
            r = min(EW, EH) / 2
            x0, y0 = x - EW / 2, CY - EH / 2
            c.new_sub_path()
            c.arc(x0 + EW - r, y0 + r, r, -math.pi / 2, 0)
            c.arc(x0 + EW - r, y0 + EH - r, r, 0, math.pi / 2)
            c.arc(x0 + r, y0 + EH - r, r, math.pi / 2, math.pi)
            c.arc(x0 + r, y0 + r, r, math.pi, 3 * math.pi / 2)
            c.close_path()
            c.fill()
            hr = 8 * U / 2
            hx = x - out * max(0, EW / 2 - 5 * U - hr)
            hy = CY - EH / 2 + 5 * U + hr
            h.arc(hx, hy, hr * 1.05, 0, 2 * math.pi)
            h.fill()
            ex, ey = x + out * EW * 0.36, CY - EH * 0.38
            c.move_to(ex - out * 5 * U, ey + 3 * U)
            c.curve_to(*quad_to_cubic((ex - out * 5 * U, ey + 3 * U), (ex + out * 2 * U, ey - 0.5 * U), (ex + out * 8 * U, ey - U)))
            c.curve_to(*quad_to_cubic((ex + out * 8 * U, ey - U), (ex + out * 3 * U, ey + 5 * U), (ex, ey + 13 * U)))
            c.close_path()
            c.fill()
        elif mode == "wink":
            r = EW * 1.2 * 0.56
            c.set_line_width(3.4 * U)
            c.arc(x, CY + EH * 0.12, r, math.pi * 1.12, math.pi * 1.88)
            c.stroke()
        else:
            raise ExportError(f"Unknown eye mode: {mode}")
    mx, my = FW / 2, CY + EH / 2 + 8 * U
    c.set_line_width(2.4 * U)
    if FACE_MOUTH == "smile-big":
        c.arc(mx, my - 5 * U, 9 * U, math.pi * 0.18, math.pi * 0.82)
    else:
        c.arc(mx, my - 4 * U, 7 * U, math.pi * 0.2, math.pi * 0.8)
    c.stroke()
    return unpremultiply(shapes_surface), unpremultiply(lights_surface)


def face_on_head(head, vis, cairo):
    """Put the face on the visor with a soft red glow, then restore the glass gloss."""
    shapes, lights = draw_face(cairo)
    width, height = head.size
    s = vis["w"] / FW * FACE_SCALE

    def place(img):
        big = img.resize((max(1, int(FW * s)), max(1, int(FH * s))), Image.LANCZOS)
        big = big.rotate(-vis["ang"], resample=Image.BICUBIC, expand=True)
        layer = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        layer.paste(big, (int(vis["cx"] - big.width / 2), int(vis["cy"] - big.height / 2)), big)
        return layer

    shapes, lights = place(shapes), place(lights)
    mask = np.clip(vis["mask"], 0, 1)
    out = head.copy()
    blur = max(2, 30 * s)
    for radius, strength in ((blur * 3, 0.55), (blur, 0.9)):
        g = np.asarray(shapes.filter(ImageFilter.GaussianBlur(radius)), dtype=np.float32).copy()
        g[..., 3] *= strength * mask
        out.alpha_composite(Image.fromarray(g.astype(np.uint8), "RGBA"))
    for layer in (shapes, lights):
        la = np.asarray(layer, dtype=np.float32).copy()
        la[..., 3] *= mask
        out.alpha_composite(Image.fromarray(la.astype(np.uint8), "RGBA"))
    ha = np.asarray(head, dtype=np.float32)
    gloss = np.clip((ha[..., :3].mean(-1) - 40) / 120, 0, 1) * mask * 0.55
    out.alpha_composite(Image.fromarray(np.dstack([ha[..., :3], gloss[..., None] * 255]).astype(np.uint8), "RGBA"))
    return out


def silhouette(face_head, vis):
    """Themed-icon shape: the head without the visor, plus the lit face."""
    a = np.asarray(face_head, dtype=np.float32)
    body = a[..., 3] / 255
    vm = np.clip(vis["mask"] * 1.15, 0, 1)
    face = np.clip(((a[..., 0] - a[..., 1]) - 60) / 80, 0, 1) * vm
    return Image.fromarray((np.maximum(body * (1 - vm), face) * 255).astype(np.uint8), "L")


def place_on_master(img, bbox):
    k = HEAD_HEIGHT * TILE / (bbox[3] - bbox[1])
    big = img.resize((max(1, round(img.width * k)), max(1, round(img.height * k))), Image.LANCZOS)
    ax, ay = (bbox[0] + bbox[2]) / 2 * k, (bbox[1] + bbox[3]) / 2 * k
    ox = (HEAD_CENTER[0] - 0.5) * TILE + MASTER / 2 - ax
    oy = (HEAD_CENTER[1] - 0.5) * TILE + MASTER / 2 - ay
    layer = Image.new(img.mode, (MASTER, MASTER), 0)
    layer.paste(big, (int(round(ox)), int(round(oy))), big if img.mode == "RGBA" else None)
    return layer


def trace(mask, measure):
    """Trace a 0..255 mask into even-odd path data in 108 dp units."""
    m = np.pad(np.asarray(mask, dtype=np.float32) / 255, 1)
    scale = LAYER_DP / MASTER
    parts = []
    for contour in measure.find_contours(m, 0.5):
        poly = measure.approximate_polygon(contour - 1, tolerance=TRACE_TOLERANCE_PX)
        xs, ys = (poly[:, 1] + 0.5) * scale, (poly[:, 0] + 0.5) * scale
        area = 0.5 * abs(np.dot(xs, np.roll(ys, 1)) - np.dot(ys, np.roll(xs, 1)))
        if area < TRACE_MIN_AREA_DP2 or len(xs) < 4:
            continue
        pts = [f"{x:.2f},{y:.2f}" for x, y in zip(xs[:-1], ys[:-1])]
        parts.append(f"M{pts[0]}L{' '.join(pts[1:])}Z")
    if not parts:
        raise ExportError("The monochrome trace is empty.")
    return "".join(parts), len(parts)


def run_render(root):
    import cairo
    from scipy import ndimage
    from skimage import measure

    p = paths(root)
    if not p["head"].is_file():
        raise ExportError(f"The head render is missing: {p['head']}")
    head = Image.open(p["head"]).convert("RGBA")
    vis = visor_of(head, ndimage)
    face_head = face_on_head(head, vis, cairo)
    bbox = face_head.getbbox()

    fg = place_on_master(face_head, bbox)
    alpha = ndimage.gaussian_filter(np.asarray(fg, dtype=np.float32)[..., 3], TILE * SHADOW_BLUR)
    alpha = np.roll(alpha, int(TILE * SHADOW_DY), axis=0) * SHADOW_OPACITY
    shadow = np.zeros((MASTER, MASTER, 4), np.float32)
    shadow[..., 3] = alpha
    master = Image.fromarray(shadow.clip(0, 255).astype(np.uint8), "RGBA")
    master.alpha_composite(fg)
    save_png(master, p["master"])

    path_data, count = trace(place_on_master(silhouette(face_head, vis), bbox), measure)
    p["monochrome"].parent.mkdir(parents=True, exist_ok=True)
    p["monochrome"].write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Made by scripts/export-app-icon.py (render step). Do not edit by hand. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp"\n'
        '    android:height="108dp"\n'
        '    android:viewportWidth="108"\n'
        '    android:viewportHeight="108">\n'
        "    <path\n"
        '        android:fillColor="#FFFFFFFF"\n'
        '        android:fillType="evenOdd"\n'
        f'        android:pathData="{path_data}" />\n'
        "</vector>\n",
        encoding="utf-8",
    )
    print(f"Master: {MASTER} px. Visor tilt: {vis['ang']:.1f} degrees. Monochrome: {count} contours.")
    return 0


# ---------------------------------------------------------------- export

def load_master(p):
    if not p["master"].is_file():
        raise ExportError(f"The master is missing: {p['master']}. Run --render first.")
    master = Image.open(p["master"])
    if master.size != (MASTER, MASTER) or master.mode != "RGBA":
        raise ExportError(f"The master must be {MASTER}x{MASTER} RGBA.")
    return master


def tinted(rgb_on_black):
    """Grayscale art for the iOS tinted icon: a light head and a white face on black."""
    r, g, b = rgb_on_black[..., 0], rgb_on_black[..., 1], rgb_on_black[..., 2]
    lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
    face = np.clip(((r - g) - 60) / 80, 0, 1) * 255
    gray = np.maximum(lum * 0.85, face)
    return np.repeat(gray[..., None], 3, axis=2)


def run_export(root):
    p = paths(root)
    master = load_master(p)

    for name, density in DENSITIES:
        side = round(LAYER_DP * density)
        folder = p["res"] / f"mipmap-{name}"
        save_png(master.resize((side, side), Image.LANCZOS), folder / "ic_launcher_foreground.png")
        bg = quantize(background(side, LAYER_DP / VISIBLE_DP))
        save_png(Image.fromarray(bg, "RGB"), folder / "ic_launcher_background.png")

    for path in p["adaptive"]:
        path.write_text(ADAPTIVE, encoding="utf-8")
    if not p["color"].is_file():
        raise ExportError(f"The background color file is missing: {p['color']}")
    text = p["color"].read_text(encoding="utf-8")
    if not COLOR_RE.search(text):
        raise ExportError("The background color file has no ic_launcher_background color.")
    p["color"].write_text(COLOR_RE.sub(rf"\g<1>{BASE_COLOR}\g<2>", text), encoding="utf-8")

    off = (MASTER - TILE) // 2
    tile_fg = np.asarray(master)[off:off + TILE, off:off + TILE]
    if TILE != IOS_SIZE:
        tile_fg = np.asarray(Image.fromarray(tile_fg, "RGBA").resize((IOS_SIZE, IOS_SIZE), Image.LANCZOS))
    light = Image.fromarray(quantize(composite(background(IOS_SIZE, 1.0), tile_fg)), "RGB")
    gray = Image.fromarray(quantize(tinted(composite(np.zeros((IOS_SIZE, IOS_SIZE, 3)), tile_fg))), "RGB")
    images = {"icon-1024.png": light, "icon-1024-dark.png": light, "icon-1024-tinted.png": gray}
    entries = []
    for filename, appearance in IOS_FILES:
        save_png(images[filename], p["ios"] / filename)
        entry = {"filename": filename, "idiom": "universal", "platform": "ios", "size": "1024x1024"}
        if appearance:
            entry = {"appearances": [{"appearance": "luminosity", "value": appearance}], **entry}
        entries.append(entry)
    contents = {"images": entries, "info": {"author": "xcode", "version": 1}}
    (p["ios"] / "Contents.json").write_text(json.dumps(contents, indent=2) + "\n", encoding="utf-8")

    data = png_bytes(light.resize((PLAY_SIZE, PLAY_SIZE), Image.LANCZOS))
    p["play"].parent.mkdir(parents=True, exist_ok=True)
    p["play"].write_bytes(data)
    if not p["svg"].is_file():
        raise ExportError(f"The SVG is missing: {p['svg']}")
    svg = p["svg"].read_text(encoding="utf-8")
    if not SVG_PAYLOAD_RE.search(svg):
        raise ExportError("The SVG has no embedded PNG payload.")
    payload = base64.b64encode(data).decode("ascii")
    p["svg"].write_text(SVG_PAYLOAD_RE.sub(lambda m: m.group(1) + payload, svg, count=1), encoding="utf-8")

    print(f"Android: {len(DENSITIES)} foreground and {len(DENSITIES)} background layers. "
          f"iOS: {len(IOS_FILES)} icons. Play Store: 1 icon and 1 SVG.")
    return 0


# ---------------------------------------------------------------- check

def check_png(path, size, mode, problems, label):
    if not path.is_file():
        problems.append(f"{label} is missing: {path}")
        return None
    image = Image.open(path)
    if image.size != size:
        problems.append(f"{label} is {image.size[0]}x{image.size[1]}, expected {size[0]}x{size[1]}.")
    if mode and image.mode != mode:
        problems.append(f"{label} has mode {image.mode}, expected {mode}.")
    return image


def face_radius_dp(master):
    """Largest distance from the layer centre to a visor or face pixel, in dp."""
    a = np.asarray(master, dtype=np.float32)
    lum = a[..., :3].mean(-1)
    lit = (a[..., 0] - a[..., 1]) > 60
    ys, xs = np.nonzero((a[..., 3] > 200) & ((lum < 70) | lit))
    if len(xs) == 0:
        return None
    d = np.hypot(xs + 0.5 - MASTER / 2, ys + 0.5 - MASTER / 2)
    return float(d.max()) * LAYER_DP / MASTER


def run_check(root):
    p = paths(root)
    problems = []

    master = check_png(p["master"], (MASTER, MASTER), "RGBA", problems, "The master")
    if master is not None:
        radius = face_radius_dp(master)
        if radius is None:
            problems.append("The master has no visor or face pixels.")
        elif radius > SAFE_DP / 2:
            problems.append(f"The visor reaches {radius:.1f} dp from the centre; the safe zone is {SAFE_DP / 2} dp.")
        else:
            print(f"Safe zone: the visor and face reach {radius:.1f} dp of {SAFE_DP / 2:.0f} dp.")

    for name, density in DENSITIES:
        side = round(LAYER_DP * density)
        folder = p["res"] / f"mipmap-{name}"
        fg = check_png(folder / "ic_launcher_foreground.png", (side, side), "RGBA", problems, f"The {name} foreground")
        if fg is not None and fg.mode == "RGBA" and fg.getchannel("A").getpixel((0, 0)) != 0:
            problems.append(f"The {name} foreground corner is not transparent.")
        check_png(folder / "ic_launcher_background.png", (side, side), "RGB", problems, f"The {name} background")

    for path in p["adaptive"]:
        if not path.is_file():
            problems.append(f"The adaptive icon is missing: {path}")
        elif path.read_text(encoding="utf-8") != ADAPTIVE:
            problems.append(f"{path.name} does not match the expected layers.")

    if p["color"].is_file():
        match = COLOR_RE.search(p["color"].read_text(encoding="utf-8"))
        if not match or BASE_COLOR not in match.group(0):
            problems.append(f"The background color is not {BASE_COLOR}.")
    else:
        problems.append(f"The background color file is missing: {p['color']}")

    if not p["monochrome"].is_file():
        problems.append(f"The monochrome vector is missing: {p['monochrome']}")
    else:
        try:
            tree = ET.parse(p["monochrome"])
            ns = "{http://schemas.android.com/apk/res/android}"
            vector = tree.getroot()
            if vector.get(ns + "viewportWidth") != str(LAYER_DP):
                problems.append("The monochrome vector viewport is not 108.")
            data = "".join(path.get(ns + "pathData", "") for path in vector.iter("path"))
            if data.count("M") < 3:
                problems.append("The monochrome vector needs the head, the visor hole and the face.")
        except ET.ParseError as err:
            problems.append(f"The monochrome vector is not valid XML: {err}")

    for filename, appearance in IOS_FILES:
        image = check_png(p["ios"] / filename, (IOS_SIZE, IOS_SIZE), "RGB", problems, f"The iOS {appearance or 'light'} icon")
        if image is not None and "transparency" in image.info:
            problems.append(f"The iOS {appearance or 'light'} icon has transparency.")
    contents_path = p["ios"] / "Contents.json"
    try:
        contents = json.loads(contents_path.read_text(encoding="utf-8"))
        found = {(i.get("filename"), (i.get("appearances") or [{}])[0].get("value")) for i in contents["images"]}
        if found != set(IOS_FILES):
            problems.append("The iOS Contents.json does not list the light, dark and tinted icons.")
    except (OSError, ValueError, KeyError) as err:
        problems.append(f"The iOS Contents.json is not valid: {err}")

    check_png(p["play"], (PLAY_SIZE, PLAY_SIZE), "RGB", problems, "The Play Store icon")
    if p["svg"].is_file():
        match = SVG_PAYLOAD_RE.search(p["svg"].read_text(encoding="utf-8"))
        if not match:
            problems.append("The SVG has no embedded PNG payload.")
        else:
            try:
                embedded = Image.open(io.BytesIO(base64.b64decode(match.group(0).split(",", 1)[1])))
                if embedded.size != (PLAY_SIZE, PLAY_SIZE):
                    problems.append(f"The SVG image is {embedded.size[0]}x{embedded.size[1]}, expected {PLAY_SIZE}x{PLAY_SIZE}.")
            except Exception as err:
                problems.append(f"The SVG payload is not a valid PNG: {err}")
    else:
        problems.append(f"The SVG is missing: {p['svg']}")

    if problems:
        print("Problems found:", file=sys.stderr)
        for line in problems:
            print(f"- {line}", file=sys.stderr)
        return 1
    print("ok")
    return 0


def main():
    parser = argparse.ArgumentParser(description="Make the Seira app icon.")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent,
                        help="Repository root. Default: the parent of the scripts folder.")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--render", action="store_true",
                      help="Remake the foreground master and the monochrome vector from the head render.")
    mode.add_argument("--check", action="store_true", help="Verify the exported files and write nothing.")
    args = parser.parse_args()
    root = args.root.resolve()
    try:
        if args.render:
            return run_render(root)
        return run_check(root) if args.check else run_export(root)
    except ExportError as err:
        print(f"Error: {err}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
