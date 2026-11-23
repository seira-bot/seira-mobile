#!/usr/bin/env python3
"""Export the "Night visor" splash assets for Android and iOS.

Usage:
    python3 scripts/export-splash-visor.py --wordmark <seira-wordmark-reversed.svg> [--root DIR] [--preview OUT.png]

The eyes use the flat Seira mark geometry (viewBox units). The wordmark comes
from the brand folder of the `seira` repo (`brand/logo/svg/`).

Writes:
  android/app/src/main/res/drawable/ic_seira_splash.xml           static, eyes open
  android/app/src/main/res/drawable/ic_seira_splash_animated.xml  Android 12+ splash icon
  android/app/src/main/res/drawable/seira_splash_branding.xml     wordmark, 200x80 dp
  android/app/src/main/res/values/colors_splash.xml
  ios/App/Assets.xcassets/LaunchEyes.imageset, LaunchBackground.colorset,
  LaunchWordmark.imageset

Vector drawables cannot blur, so the glow is radial gradients. The iOS PNG
renders the same gradients, so both platforms show the same eyes.

Requires pycairo, numpy and Pillow.
"""

import argparse
import json
import math
import re
from pathlib import Path

import cairo
import numpy as np
from PIL import Image

RED = "#FF2A57"
RED_RGB = (1.0, 42 / 255, 87 / 255)
INK = "#151925"
INK_RGB = (21 / 255, 25 / 255, 37 / 255)

# Mark units to dp (pt). The eye pair centre (89, 93) lands on the centre of
# the 288 dp splash icon. Android masks that icon to a 192 dp circle.
SCALE = 1.8
CENTRE = (89, 93)
LID_PIVOT_Y = 96.4  # 60 % down the eye, the same origin as the design canvas
TX = 144 - CENTRE[0] * SCALE
TY = 144 - CENTRE[1] * SCALE

EYES = (
    dict(x0=55, x1=76, wing=((60, 79), (55, 89), (47, 76)), highlight=(67, 85), centre=(65.5, 93)),
    dict(x0=102, x1=123, wing=((118, 79), (131, 76), (123, 89)), highlight=(111, 85), centre=(112.5, 93)),
)
EYE_RADIUS = 10.5
HIGHLIGHT_RADIUS = 3.5
EYE_PATH = ("M55,86.5A10.5,10.5 0,0 1,76,86.5V99.5A10.5,10.5 0,0 1,55,99.5Z M60,79L55,89L47,76Z "
            "M102,86.5A10.5,10.5 0,0 1,123,86.5V99.5A10.5,10.5 0,0 1,102,99.5Z M118,79L131,76L123,89Z")

AMBIENT = dict(centres=(CENTRE,), rx=60, ry=36, stops=((0, .12), (.6, .04), (1, 0)))
BLOOM = dict(centres=tuple(e["centre"] for e in EYES), rx=22, ry=30,
             stops=((0, .9), (.5, .55), (.75, .18), (1, 0)))
GLOWS = (AMBIENT, BLOOM)

# Lid keyframes for the 800 ms Android 12+ splash: (fraction, scaleY, interpolator).
# The launch overlay in the app repeats this timeline.
LID_DURATION_MS = 800
LID_KEYFRAMES = (
    (0, .1, None), (.1875, .1, None), (.525, 1.05, "decelerate_cubic"),
    (.625, 1, "fast_out_slow_in"), (.8, 1, None), (.9, .1, "accelerate_quad"), (1, 1, "decelerate_quad"),
)

WORDMARK_WIDTH_DP = 112
BRANDING_W, BRANDING_H = 200, 80  # Android 12+ branding image box
IOS_EYES_PT = (224, 144)
NS = ('xmlns:android="http://schemas.android.com/apk/res/android"\n'
      '    xmlns:aapt="http://schemas.android.com/aapt"')


def alpha_hex(a):
    return "%02X" % round(a * 255)


def circle_path(cx, cy, r):
    return f"M{cx - r:g},{cy:g}a{r:g},{r:g} 0,1 0,{2 * r:g},0a{r:g},{r:g} 0,1 0,{-2 * r:g},0Z"


def glow_xml(cx, cy, glow, indent):
    items = "\n".join(
        f'{indent}                <item android:offset="{o:g}" android:color="#{alpha_hex(a)}{RED[1:]}" />'
        for o, a in glow["stops"])
    return f"""{indent}<group android:pivotX="{cx:g}" android:pivotY="{cy:g}" android:scaleY="{glow['ry'] / glow['rx']:.4f}">
{indent}    <path android:pathData="{circle_path(cx, cy, glow['rx'])}">
{indent}        <aapt:attr name="android:fillColor">
{indent}            <gradient android:type="radial" android:centerX="{cx:g}" android:centerY="{cy:g}" android:gradientRadius="{glow['rx']:g}">
{items}
{indent}            </gradient>
{indent}        </aapt:attr>
{indent}    </path>
{indent}</group>"""


def eyes_vector(lid_scale, indent=""):
    inner = indent + "            "
    glows = "\n".join(glow_xml(cx, cy, g, inner) for g in GLOWS for cx, cy in g["centres"])
    highlights = " ".join(circle_path(*e["highlight"], HIGHLIGHT_RADIUS) for e in EYES)
    return f"""{indent}<vector
{indent}    android:width="288dp"
{indent}    android:height="288dp"
{indent}    android:viewportWidth="288"
{indent}    android:viewportHeight="288">
{indent}    <group android:translateX="{TX:.1f}" android:translateY="{TY:.1f}" android:scaleX="{SCALE:g}" android:scaleY="{SCALE:g}">
{indent}        <group android:name="lids" android:pivotX="{CENTRE[0]:g}" android:pivotY="{LID_PIVOT_Y:g}" android:scaleY="{lid_scale:g}">
{glows}
{inner}<path android:fillColor="{RED}" android:pathData="{EYE_PATH}" />
{inner}<path android:fillColor="#FFFFFF" android:pathData="{highlights}" />
{indent}        </group>
{indent}    </group>
{indent}</vector>"""


def animated_vector():
    keyframes = "\n".join(
        f'                        <keyframe android:fraction="{f:g}" android:value="{v:g}"'
        + (f' android:interpolator="@android:interpolator/{ip}"' if ip else "") + " />"
        for f, v, ip in LID_KEYFRAMES)
    return f"""<?xml version="1.0" encoding="utf-8"?>
<animated-vector {NS}>
    <aapt:attr name="android:drawable">
{eyes_vector(LID_KEYFRAMES[0][1], '        ')}
    </aapt:attr>
    <target android:name="lids">
        <aapt:attr name="android:animation">
            <objectAnimator android:duration="{LID_DURATION_MS}">
                <propertyValuesHolder android:propertyName="scaleY" android:valueType="floatType">
{keyframes}
                </propertyValuesHolder>
            </objectAnimator>
        </aapt:attr>
    </target>
</animated-vector>
"""


def branding_vector(svg):
    paths = re.findall(r'<path fill="(#[0-9A-Fa-f]+)" d="([^"]+)"', svg)
    dot = re.search(r'<circle cx="([\d.]+)" cy="([\d.]+)" r="([\d.]+)" fill="(#[0-9A-Fa-f]+)"', svg)
    view = [float(v) for v in re.search(r'viewBox="([^"]+)"', svg).group(1).split()]
    if not paths or not dot:
        raise SystemExit("wordmark SVG: expected <path fill d> and one <circle>")
    scale = WORDMARK_WIDTH_DP / view[2]
    height = view[3] * scale
    body = "\n".join(f'            <path android:fillColor="{c}" android:pathData="{d}" />' for c, d in paths)
    cx, cy, r, colour = dot.groups()
    body += f'\n            <path android:fillColor="{colour}" android:pathData="{circle_path(float(cx), float(cy), float(r))}" />'
    # Bottom-aligned, so the letters sit near the bottom of the screen like the design.
    return f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{BRANDING_W}dp"
    android:height="{BRANDING_H}dp"
    android:viewportWidth="{BRANDING_W}"
    android:viewportHeight="{BRANDING_H}">
    <group android:translateX="{(BRANDING_W - WORDMARK_WIDTH_DP) / 2:g}" android:translateY="{BRANDING_H - height:.1f}" android:scaleX="{scale:g}" android:scaleY="{scale:g}">
        <group android:translateX="{-view[0]:g}" android:translateY="{-view[1]:g}">
{body}
        </group>
    </group>
</vector>
"""


def draw_eyes(ctx):
    for g in GLOWS:
        for cx, cy in g["centres"]:
            ctx.save()
            ctx.translate(cx, cy)
            ctx.scale(1, g["ry"] / g["rx"])
            gradient = cairo.RadialGradient(0, 0, 0, 0, 0, g["rx"])
            for offset, a in g["stops"]:
                gradient.add_color_stop_rgba(offset, *RED_RGB, a)
            ctx.arc(0, 0, g["rx"], 0, 2 * math.pi)
            ctx.set_source(gradient)
            ctx.fill()
            ctx.restore()
    for e in EYES:
        r = EYE_RADIUS
        ctx.new_sub_path()
        ctx.arc(e["x0"] + r, 86.5, r, math.pi, 2 * math.pi)
        ctx.line_to(e["x1"], 99.5)
        ctx.arc(e["x0"] + r, 99.5, r, 0, math.pi)
        ctx.close_path()
        ctx.move_to(*e["wing"][0])
        for p in e["wing"][1:]:
            ctx.line_to(*p)
        ctx.close_path()
    ctx.set_source_rgb(*RED_RGB)
    ctx.fill()
    for e in EYES:
        ctx.new_sub_path()
        ctx.arc(*e["highlight"], HIGHLIGHT_RADIUS, 0, 2 * math.pi)
    ctx.set_source_rgb(1, 1, 1)
    ctx.fill()


def render_eyes(px_per_unit, width, height, background=None):
    surface = cairo.ImageSurface(cairo.FORMAT_ARGB32, width, height)
    ctx = cairo.Context(surface)
    if background:
        ctx.set_source_rgb(*background)
        ctx.paint()
    ctx.translate(width / 2, height / 2)
    ctx.scale(px_per_unit, px_per_unit)
    ctx.translate(-CENTRE[0], -CENTRE[1])
    draw_eyes(ctx)
    surface.flush()
    bgra = np.ndarray((height, width, 4), np.uint8, surface.get_data()).astype(np.float32)
    rgba = bgra[..., [2, 1, 0, 3]]
    alpha = rgba[..., 3:4]
    # Cairo stores premultiplied colour; PNG wants straight colour.
    rgba[..., :3] = np.where(alpha > 0, np.clip(rgba[..., :3] * 255 / np.maximum(alpha, 1), 0, 255), 0)
    return Image.fromarray(rgba.round().astype(np.uint8), "RGBA")


def write_json(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--wordmark", required=True, type=Path, help="seira-wordmark-reversed.svg")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--preview", type=Path, help="write a 390x844 pt phone frame at 3x")
    args = parser.parse_args()

    svg = args.wordmark.read_text()
    res = args.root / "android/app/src/main/res"
    static = eyes_vector(1).replace("<vector\n", f"<vector {NS}\n", 1)
    (res / "drawable/ic_seira_splash.xml").write_text('<?xml version="1.0" encoding="utf-8"?>\n' + static + "\n")
    (res / "drawable/ic_seira_splash_animated.xml").write_text(animated_vector())
    (res / "drawable/seira_splash_branding.xml").write_text(branding_vector(svg))
    (res / "values/colors_splash.xml").write_text(
        f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        f'    <color name="seira_visor_ink">{INK}</color>\n</resources>\n')

    assets = args.root / "ios/App/Assets.xcassets"
    info = {"author": "xcode", "version": 1}
    eyes_dir = assets / "LaunchEyes.imageset"
    eyes_dir.mkdir(exist_ok=True)
    images = [{"idiom": "universal", "scale": "1x"}]
    for scale in (2, 3):
        name = f"LaunchEyes@{scale}x.png"
        w, h = IOS_EYES_PT
        render_eyes(SCALE * scale, w * scale, h * scale).save(eyes_dir / name, optimize=True)
        images.append({"filename": name, "idiom": "universal", "scale": f"{scale}x"})
    write_json(eyes_dir / "Contents.json", {"images": images, "info": info})
    colour_dir = assets / "LaunchBackground.colorset"
    colour_dir.mkdir(exist_ok=True)
    components = {"alpha": "1.000", "red": "0x" + INK[1:3], "green": "0x" + INK[3:5], "blue": "0x" + INK[5:7]}
    write_json(colour_dir / "Contents.json", {
        "colors": [{"color": {"color-space": "srgb", "components": components}, "idiom": "universal"}],
        "info": info})
    word_dir = assets / "LaunchWordmark.imageset"
    word_dir.mkdir(exist_ok=True)
    (word_dir / "seira-wordmark-reversed.svg").write_text(svg)
    write_json(word_dir / "Contents.json", {
        "images": [{"filename": "seira-wordmark-reversed.svg", "idiom": "universal"}],
        "info": info, "properties": {"preserves-vector-representation": True}})

    if args.preview:
        render_eyes(SCALE * 3, 390 * 3, 844 * 3, INK_RGB).save(args.preview)


if __name__ == "__main__":
    main()
