#!/usr/bin/env python3
"""Export the Seira 3D icon set for iOS and Android and render review sheets.

Usage:
    python3 scripts/export-icons3d.py [--root DIR] [--only NAME ...] [--check]

Reads <root>/shared/icons3d/manifest.json and the 1024 px RGBA masters in
<root>/shared/icons3d/src/. Writes the iOS image sets, the Android WebP
drawables, two contact sheets and, when candidates exist, an app icon preview.

Requires Pillow with WebP support.
"""

import argparse
import json
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

NAME_RE = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")
IOS_SCALES = (1, 2, 3)
ANDROID_SCALE = 4
SHEET_COLUMNS = 3
SHEET_ICON_PT = (120, 44, 30)
SHEET_PX_PER_PT = 2
APP_ICON_RADIUS = 0.2237
SHEETS = (
    ("contact-light.png", "#ffffff", (0x14, 0x14, 0x17)),
    ("contact-dark.png", "#111113", (0xF2, 0xF2, 0xF3)),
)
IOS_NAMESPACE = {"info": {"author": "xcode", "version": 1},
                 "properties": {"provides-namespace": True}}


class ExportError(Exception):
    pass


def paths(root):
    shared = root / "shared" / "icons3d"
    return {
        "manifest": shared / "manifest.json",
        "src": shared / "src",
        "shared": shared,
        "candidates": shared / "app-icon-candidates",
        "ios": root / "ios" / "App" / "Assets.xcassets" / "Icons3D",
        "android": root / "android" / "app" / "src" / "main" / "res" / "drawable-nodpi",
    }


def load_manifest(path):
    if not path.is_file():
        raise ExportError(f"The manifest is missing: {path}")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as err:
        raise ExportError(f"The manifest is not valid JSON: {err}")
    master = data.get("masterSize")
    if not isinstance(master, int) or master <= 0:
        raise ExportError("The manifest needs a positive integer masterSize.")
    icons = data.get("icons")
    if not isinstance(icons, list) or not icons:
        raise ExportError("The manifest needs a non-empty icons list.")
    seen = set()
    problems = []
    for entry in icons:
        name = entry.get("name", "")
        if not NAME_RE.match(name):
            problems.append(f"Icon name '{name}' is not kebab-case.")
            continue
        if name in seen:
            problems.append(f"Icon name '{name}' appears more than once.")
        seen.add(name)
        if entry.get("iosAsset") != f"Icons3D/{name}":
            problems.append(f"{name}: iosAsset must be 'Icons3D/{name}'.")
        expected = "icon3d_" + name.replace("-", "_")
        if entry.get("androidDrawable") != expected:
            problems.append(f"{name}: androidDrawable must be '{expected}'.")
        size = entry.get("sizePt")
        if not isinstance(size, int) or size <= 0:
            problems.append(f"{name}: sizePt must be a positive integer.")
    if problems:
        raise ExportError("\n".join(problems))
    return data


def resize(master, px):
    # Premultiplied alpha stops dark RGB under transparent pixels from bleeding into edges.
    return master.convert("RGBa").resize((px, px), Image.LANCZOS).convert("RGBA")


def load_masters(icons, src, master_size):
    masters, problems = {}, []
    for entry in icons:
        name = entry["name"]
        path = src / f"{name}.png"
        if not path.is_file():
            problems.append(f"{name}: master is missing: {path}")
            continue
        img = Image.open(path)
        if img.mode != "RGBA":
            problems.append(f"{name}: master is {img.mode}, not RGBA.")
        elif img.size != (master_size, master_size):
            problems.append(f"{name}: master is {img.size[0]}x{img.size[1]}, "
                            f"not {master_size}x{master_size}.")
        else:
            masters[name] = img
    if problems:
        raise ExportError("\n".join(problems))
    return masters


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def imageset_contents(name):
    suffix = {1: "", 2: "@2x", 3: "@3x"}
    return {
        "images": [{"filename": f"{name}{suffix[s]}.png", "idiom": "universal",
                    "scale": f"{s}x"} for s in IOS_SCALES],
        "info": {"author": "xcode", "version": 1},
        "properties": {"template-rendering-intent": "original"},
    }


def export_ios(entry, master, ios_dir):
    name, pt = entry["name"], entry["sizePt"]
    folder = ios_dir / f"{name}.imageset"
    folder.mkdir(parents=True, exist_ok=True)
    for scale, suffix in zip(IOS_SCALES, ("", "@2x", "@3x")):
        resize(master, pt * scale).save(folder / f"{name}{suffix}.png", optimize=True)
    write_json(folder / "Contents.json", imageset_contents(name))


def export_android(entry, master, android_dir):
    android_dir.mkdir(parents=True, exist_ok=True)
    out = android_dir / f"{entry['androidDrawable']}.webp"
    # Lossless WebP keeps alpha. Android 4.3 (API 18) is the first release that decodes it.
    resize(master, entry["sizePt"] * ANDROID_SCALE).save(
        out, "WEBP", lossless=True, quality=100, method=6)


def blend(fg, bg_hex, opacity):
    bg = tuple(int(bg_hex[i:i + 2], 16) for i in (1, 3, 5))
    return tuple(round(f * opacity + b * (1 - opacity)) for f, b in zip(fg, bg))


def render_sheet(icons, masters, bg_hex, ink):
    k = SHEET_PX_PER_PT
    sizes = [pt * k for pt in SHEET_ICON_PT]
    gap, pad, gutter, margin = 16 * k, 20 * k, 16 * k, 24 * k
    label_gap, label_h = 12 * k, 22 + 8
    cell_w = sum(sizes) + gap * (len(sizes) - 1) + pad * 2
    cell_h = max(sizes) + label_gap + label_h + pad * 2
    rows = -(-len(icons) // SHEET_COLUMNS)
    width = margin * 2 + cell_w * SHEET_COLUMNS + gutter * (SHEET_COLUMNS - 1)
    height = margin * 2 + cell_h * rows + gutter * (rows - 1)
    sheet = Image.new("RGBA", (width, height), bg_hex)
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default(size=22)
    label_color = blend(ink, bg_hex, 0.6)
    for i, entry in enumerate(icons):
        col, row = i % SHEET_COLUMNS, i // SHEET_COLUMNS
        x0 = margin + col * (cell_w + gutter)
        y0 = margin + row * (cell_h + gutter)
        baseline = y0 + pad + max(sizes)
        x = x0 + pad
        for px in sizes:
            sheet.alpha_composite(resize(masters[entry["name"]], px), (x, baseline - px))
            x += px + gap
        draw.text((x0 + pad, baseline + label_gap), entry["name"], font=font, fill=label_color)
    return sheet.convert("RGB")


def rounded_mask(px):
    scale = 4
    big = Image.new("L", (px * scale, px * scale), 0)
    ImageDraw.Draw(big).rounded_rectangle(
        (0, 0, px * scale - 1, px * scale - 1), radius=px * scale * APP_ICON_RADIUS, fill=255)
    return big.resize((px, px), Image.LANCZOS)


def render_candidates(files):
    large, small = 180, 60
    gap, pad, label_h = 20, 28, 40
    col_w = large + gap + small
    width = pad + len(files) * (col_w + pad)
    band_h = large + pad * 2
    height = band_h * 2 + label_h + pad
    sheet = Image.new("RGB", (width, height), "#f4f4f6")
    draw = ImageDraw.Draw(sheet)
    draw.rectangle((0, band_h, width, band_h * 2), fill="#111113")
    font = ImageFont.load_default(size=22)
    for i, path in enumerate(files):
        src = Image.open(path).convert("RGB")
        x = pad + i * (col_w + pad)
        for band, top in ((0, 0), (1, band_h)):
            for px in (large, small):
                tile = src.resize((px, px), Image.LANCZOS)
                dx = x if px == large else x + large + gap
                sheet.paste(tile, (dx, top + pad + large - px), rounded_mask(px))
        draw.text((x, band_h * 2 + 12), path.stem, font=font, fill="#141417")
    return sheet


def candidate_files(candidates_dir):
    if not candidates_dir.is_dir():
        return []
    return sorted(p for p in candidates_dir.glob("*.png") if p.name != "preview.png")


def run_export(root, only):
    p = paths(root)
    manifest = load_manifest(p["manifest"])
    icons = manifest["icons"]
    masters = load_masters(icons, p["src"], manifest["masterSize"])
    chosen = [e for e in icons if not only or e["name"] in only]
    write_json(p["ios"] / "Contents.json", IOS_NAMESPACE)
    for entry in chosen:
        export_ios(entry, masters[entry["name"]], p["ios"])
        export_android(entry, masters[entry["name"]], p["android"])
    for filename, bg, ink in SHEETS:
        render_sheet(icons, masters, bg, ink).save(p["shared"] / filename, optimize=True)
    files = candidate_files(p["candidates"])
    if files:
        render_candidates(files).save(p["candidates"] / "preview.png", optimize=True)
    print(f"iOS: {len(chosen)} image sets ({len(chosen) * len(IOS_SCALES)} PNG files)")
    print(f"Android: {len(chosen)} WebP drawables")
    print(f"Sheets: {len(SHEETS)} contact sheets"
          + (f", 1 app icon preview ({len(files)} candidates)" if files else ""))


def check_size(path, expected, problems):
    try:
        with Image.open(path) as img:
            if img.size != (expected, expected):
                problems.append(f"{path.name}: size is {img.size[0]}x{img.size[1]}, "
                                f"expected {expected}x{expected}.")
    except OSError as err:
        problems.append(f"{path.name}: cannot read the image ({err}).")


def run_check(root, only):
    p = paths(root)
    manifest = load_manifest(p["manifest"])
    icons = [e for e in manifest["icons"] if not only or e["name"] in only]
    problems = []
    try:
        load_masters(manifest["icons"], p["src"], manifest["masterSize"])
    except ExportError as err:
        problems.extend(str(err).split("\n"))
    ios_root = p["ios"] / "Contents.json"
    if not ios_root.is_file():
        problems.append(f"Missing file: {ios_root}")
    else:
        try:
            if json.loads(ios_root.read_text(encoding="utf-8")) != IOS_NAMESPACE:
                problems.append(f"Unexpected content: {ios_root}")
        except json.JSONDecodeError as err:
            problems.append(f"Invalid JSON in {ios_root}: {err}")
    for entry in icons:
        name, pt = entry["name"], entry["sizePt"]
        folder = p["ios"] / f"{name}.imageset"
        contents = folder / "Contents.json"
        if not contents.is_file():
            problems.append(f"Missing file: {contents}")
        else:
            try:
                if json.loads(contents.read_text(encoding="utf-8")) != imageset_contents(name):
                    problems.append(f"Unexpected content: {contents}")
            except json.JSONDecodeError as err:
                problems.append(f"Invalid JSON in {contents}: {err}")
        for scale, suffix in zip(IOS_SCALES, ("", "@2x", "@3x")):
            png = folder / f"{name}{suffix}.png"
            if png.is_file():
                check_size(png, pt * scale, problems)
            else:
                problems.append(f"Missing file: {png}")
        webp = p["android"] / f"{entry['androidDrawable']}.webp"
        if webp.is_file():
            check_size(webp, pt * ANDROID_SCALE, problems)
        else:
            problems.append(f"Missing file: {webp}")
    for filename, _, _ in SHEETS:
        if not (p["shared"] / filename).is_file():
            problems.append(f"Missing file: {p['shared'] / filename}")
    if candidate_files(p["candidates"]) and not (p["candidates"] / "preview.png").is_file():
        problems.append(f"Missing file: {p['candidates'] / 'preview.png'}")
    if problems:
        print("Problems found:", file=sys.stderr)
        for line in problems:
            print(f"- {line}", file=sys.stderr)
        return 1
    print("ok")
    return 0


def main():
    parser = argparse.ArgumentParser(description="Export the Seira 3D icon set.")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent,
                        help="Repository root. Default: the repository of this script.")
    parser.add_argument("--only", nargs="+", metavar="NAME",
                        help="Export or check only these icon names.")
    parser.add_argument("--check", action="store_true",
                        help="Write nothing. Verify that all expected files exist.")
    args = parser.parse_args()
    root = args.root.resolve()
    try:
        if args.check:
            return run_check(root, args.only)
        run_export(root, args.only)
    except ExportError as err:
        print(f"Error: {err}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
