#!/usr/bin/env python3
"""Export the Seira app icon for iOS, Android and the Play Store.

Usage:
    python3 scripts/export-app-icon.py [--root DIR] [--source PATH] [--check]

Reads one 1024 px opaque square. Writes the iOS icon, the Android adaptive
foreground layers and background color, and the Play Store icon.

Requires Pillow and numpy.
"""

import argparse
import base64
import io
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import numpy as np
from PIL import Image

DEFAULT_SOURCE = "shared/icons3d/app-icon-candidates/candidate-1-light.png"
SOURCE_SIZE = 1024
PLAY_SIZE = 512
BORDER_PX = 12
DENSITIES = (("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4))
LAYER_DP = 108
VISIBLE_DP = 72
MONOCHROME_REF = "@drawable/ic_launcher_monochrome"
SVG_PAYLOAD_RE = re.compile(r"(data:image/png;base64,)[A-Za-z0-9+/=]*")
COLOR_RE = re.compile(r'(<color name="ic_launcher_background">)[^<]*(</color>)')


class ExportError(Exception):
    pass


def paths(root):
    res = root / "android" / "app" / "src" / "main" / "res"
    store = root / "android" / "PlayStore" / "assets"
    return {
        "ios": root / "ios" / "App" / "Assets.xcassets" / "AppIcon.appiconset" / "icon-1024.png",
        "res": res,
        "background": res / "values" / "ic_launcher_background.xml",
        "monochrome": res / "drawable" / "ic_launcher_monochrome.xml",
        "adaptive": [res / "mipmap-anydpi-v26" / "ic_launcher.xml",
                     res / "mipmap-anydpi-v26" / "ic_launcher_round.xml"],
        "play": store / "play-icon-512.png",
        "svg": store / "app-icon-source.svg",
    }


def load_source(path):
    if not path.is_file():
        raise ExportError(f"The source image is missing: {path}")
    image = Image.open(path)
    if image.size != (SOURCE_SIZE, SOURCE_SIZE):
        raise ExportError(f"The source must be {SOURCE_SIZE}x{SOURCE_SIZE}, not {image.size[0]}x{image.size[1]}.")
    return image.convert("RGB")


def edge_color(image):
    data = np.asarray(image)
    border = np.concatenate([
        data[:BORDER_PX].reshape(-1, 3),
        data[-BORDER_PX:].reshape(-1, 3),
        data[:, :BORDER_PX].reshape(-1, 3),
        data[:, -BORDER_PX:].reshape(-1, 3),
    ])
    return tuple(int(round(v)) for v in np.median(border, axis=0))


def to_hex(color):
    return "#{:02X}{:02X}{:02X}".format(*color)


def png_bytes(image):
    buffer = io.BytesIO()
    image.save(buffer, format="PNG", optimize=True)
    return buffer.getvalue()


def foreground(image, color, density):
    canvas = round(LAYER_DP * density)
    icon = round(VISIBLE_DP * density)
    layer = Image.new("RGBA", (canvas, canvas), color + (255,))
    scaled = image.resize((icon, icon), Image.LANCZOS).convert("RGBA")
    offset = (canvas - icon) // 2
    layer.paste(scaled, (offset, offset))
    return layer


def run_export(root, source):
    p = paths(root)
    image = load_source(source)
    color = edge_color(image)
    hex_color = to_hex(color)

    p["ios"].parent.mkdir(parents=True, exist_ok=True)
    image.save(p["ios"], format="PNG", optimize=True)

    for name, density in DENSITIES:
        target = p["res"] / f"mipmap-{name}" / "ic_launcher_foreground.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        foreground(image, color, density).save(target, format="PNG", optimize=True)

    if not p["background"].is_file():
        raise ExportError(f"The background color file is missing: {p['background']}")
    text = p["background"].read_text(encoding="utf-8")
    if not COLOR_RE.search(text):
        raise ExportError("The background color file has no ic_launcher_background color.")
    p["background"].write_text(COLOR_RE.sub(rf"\g<1>{hex_color}\g<2>", text), encoding="utf-8")

    play = image.resize((PLAY_SIZE, PLAY_SIZE), Image.LANCZOS)
    p["play"].parent.mkdir(parents=True, exist_ok=True)
    data = png_bytes(play)
    p["play"].write_bytes(data)

    if not p["svg"].is_file():
        raise ExportError(f"The SVG is missing: {p['svg']}")
    svg = p["svg"].read_text(encoding="utf-8")
    payload = base64.b64encode(data).decode("ascii")
    if not SVG_PAYLOAD_RE.search(svg):
        raise ExportError("The SVG has no embedded PNG payload.")
    p["svg"].write_text(SVG_PAYLOAD_RE.sub(lambda m: m.group(1) + payload, svg, count=1), encoding="utf-8")

    print(f"Edge color: {hex_color}")
    print(f"iOS: 1 icon. Android: {len(DENSITIES)} foreground layers. Play Store: 1 icon and 1 SVG.")
    return 0


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


def run_check(root, source):
    p = paths(root)
    problems = []
    expected_hex = to_hex(edge_color(load_source(source)))

    check_png(p["ios"], (SOURCE_SIZE, SOURCE_SIZE), "RGB", problems, "The iOS icon")

    for name, density in DENSITIES:
        side = round(LAYER_DP * density)
        path = p["res"] / f"mipmap-{name}" / "ic_launcher_foreground.png"
        image = check_png(path, (side, side), "RGBA", problems, f"The {name} foreground")
        if image is not None and image.mode == "RGBA" and image.getchannel("A").getextrema()[0] != 255:
            problems.append(f"The {name} foreground has transparent pixels.")
        if list(p["res"].glob(f"mipmap-{name}/ic_launcher_monochrome.png")):
            problems.append(f"The {name} monochrome PNG must not exist.")

    if p["background"].is_file():
        match = COLOR_RE.search(p["background"].read_text(encoding="utf-8"))
        found = re.search(r">([^<]*)<", match.group(0)).group(1) if match else None
        if found != expected_hex:
            problems.append(f"The background color is {found}, expected {expected_hex}.")
    else:
        problems.append(f"The background color file is missing: {p['background']}")

    for path in p["adaptive"]:
        if not path.is_file():
            problems.append(f"The adaptive icon is missing: {path}")
        elif MONOCHROME_REF not in path.read_text(encoding="utf-8"):
            problems.append(f"{path.name} does not reference {MONOCHROME_REF}.")

    if not p["monochrome"].is_file():
        problems.append(f"The monochrome vector is missing: {p['monochrome']}")
    else:
        try:
            ET.parse(p["monochrome"])
        except ET.ParseError as err:
            problems.append(f"The monochrome vector is not valid XML: {err}")

    check_png(p["play"], (PLAY_SIZE, PLAY_SIZE), None, problems, "The Play Store icon")

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
    parser = argparse.ArgumentParser(description="Export the Seira app icon.")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent,
                        help="Repository root. Default: the parent of the scripts folder.")
    parser.add_argument("--source", type=Path, default=Path(DEFAULT_SOURCE),
                        help="Source image, relative to the root.")
    parser.add_argument("--check", action="store_true",
                        help="Verify the exported files and write nothing.")
    args = parser.parse_args()
    root = args.root.resolve()
    source = args.source if args.source.is_absolute() else root / args.source
    try:
        return run_check(root, source) if args.check else run_export(root, source)
    except ExportError as err:
        print(f"Error: {err}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
