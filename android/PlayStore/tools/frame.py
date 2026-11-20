#!/usr/bin/env python3
"""Build the framed Google Play screenshots and the feature graphics (v2).

Run from anywhere: python3 android/PlayStore/tools/frame.py

Inputs:
  ../frames/captions.json      titles, layout, and mascot placement
  ../raw/*.png                 real app renders (StoreScreenshotTest)
  ../raw/cast/<id>.png         transparent cast portraits (CastPortraitTest)
  ../graphics/source/bg/*.png  decorative backgrounds (gpt-image-2.6, no UI)

Outputs:
  ../frames/<lang>/<id>.png and ../graphics/feature-graphic-<lang>.png

The app renders are only scaled, put in a phone body, and turned a little.
This script draws no app UI. The mascots come from the app's own character
renderer, so they look the same as the avatars in the app.
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
PS = HERE.parent
RAW = PS / "raw"
CAST = RAW / "cast"
FRAMES = PS / "frames"
GRAPHICS = PS / "graphics"
BG = GRAPHICS / "source" / "bg"
FONTS = HERE / "fonts"
LANGS = ["en-US", "id-ID"]

W, H = 1080, 1920
TEXT_W = 960


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def font(name, size):
    p = FONTS / f"Geist-{name}.ttf"
    if not p.is_file():
        # Pillow would quietly load a system font of the same name instead.
        sys.exit(f"ERROR: missing font {p}")
    return ImageFont.truetype(str(p), size)


def cover(img, w, h):
    """Scale to fill w x h, then crop the center."""
    k = max(w / img.width, h / img.height)
    img = img.resize((round(img.width * k), round(img.height * k)), Image.LANCZOS)
    x, y = (img.width - w) // 2, (img.height - h) // 2
    return img.crop((x, y, x + w, y + h))


def gradient(size, top, bottom):
    w, h = size
    col = Image.new("RGB", (1, h))
    t, b = rgb(top), rgb(bottom)
    for y in range(h):
        k = y / (h - 1)
        col.putpixel((0, y), tuple(round(t[i] + (b[i] - t[i]) * k) for i in range(3)))
    return col.resize((w, h))


def background(name, size, fallback):
    p = BG / f"{name}.png"
    if p.is_file():
        return cover(Image.open(p).convert("RGB"), *size)
    print(f"  note: {p.name} is missing, a plain gradient is used")
    return gradient(size, *fallback)


def rounded_mask(size, radius, scale=4):
    w, h = size
    m = Image.new("L", (w * scale, h * scale), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w * scale - 1, h * scale - 1), radius * scale, fill=255)
    return m.resize((w, h), Image.LANCZOS)


def wrap(draw, text, fnt, max_w):
    words = text.split()
    lines, cur = [], ""
    for w in words:
        trial = (cur + " " + w).strip()
        if draw.textlength(trial, font=fnt) <= max_w:
            cur = trial
        else:
            lines.append(cur)
            cur = w
    lines.append(cur)
    if len(lines) == 2 and len(words) > 2:
        # balance the two lines so a single word does not hang on line 2
        best = min(range(1, len(words)),
                   key=lambda i: max(draw.textlength(" ".join(words[:i]), font=fnt),
                                     draw.textlength(" ".join(words[i:]), font=fnt)))
        cand = [" ".join(words[:best]), " ".join(words[best:])]
        if all(draw.textlength(c, font=fnt) <= max_w for c in cand):
            lines = cand
    return lines


def fit(draw, text, face, sizes, max_w, max_lines, what):
    """The largest size in `sizes` that keeps `text` in `max_lines` lines."""
    for s in sizes:
        fnt = font(face, s)
        lines = wrap(draw, text, fnt, max_w)
        if len(lines) <= max_lines and all(draw.textlength(l, font=fnt) <= max_w for l in lines):
            return fnt, s, lines
    sys.exit(f"ERROR: {what} does not fit in {max_lines} lines: {text!r}")


def text_block(img, items, anchor_x, y, align="ma", shadow=0.35):
    """items: list of (lines, font, size, line_height_factor, fill, gap_after)."""
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for lines, fnt, size, lhf, fill, gap in items:
        lh = round(size * lhf)
        for ln in lines:
            d.text((anchor_x, y), ln, font=fnt, fill=fill, anchor=align)
            y += lh
        y += gap - (lh - size)
    # soft shadow so white text stays readable on light parts of the background
    alpha = layer.getchannel("A").filter(ImageFilter.GaussianBlur(10))
    sh = Image.new("RGBA", img.size, (20, 6, 40, 0))
    sh.putalpha(alpha.point(lambda v: int(v * shadow)))
    img.alpha_composite(sh, (0, 6))
    img.alpha_composite(layer)
    return y


def drop_shadow(rgba, blur, opacity, tint=(25, 8, 45)):
    a = rgba.getchannel("A").filter(ImageFilter.GaussianBlur(blur))
    pad = blur * 3
    out = Image.new("RGBA", (rgba.width + 2 * pad, rgba.height + 2 * pad), tint + (0,))
    big = Image.new("L", out.size, 0)
    big.paste(a, (pad, pad))
    out.putalpha(big.filter(ImageFilter.GaussianBlur(blur)).point(lambda v: int(v * opacity)))
    return out, pad


def phone(raw, width, dark):
    """The real render inside a plain phone body. Returns RGBA."""
    bezel, r_out = 16, 76
    sw = width - 2 * bezel
    sh = round(raw.height * sw / raw.width)
    shot = raw.resize((sw, sh), Image.LANCZOS).convert("RGBA")
    size = (width, sh + 2 * bezel)
    body = Image.new("RGBA", size, (0, 0, 0, 0))
    edge = rgb("#4A4A58" if dark else "#34343F") + (255,)
    body.paste(Image.new("RGBA", size, edge), (0, 0), rounded_mask(size, r_out))
    inner = (width - 6, size[1] - 6)
    body.paste(Image.new("RGBA", inner, rgb("#0C0C11") + (255,)), (3, 3), rounded_mask(inner, r_out - 3))
    body.paste(shot, (bezel, bezel), rounded_mask((sw, sh), r_out - bezel))
    return body


def place(img, rgba, cx, cy, angle=0.0, shadow=(36, 0.45), offset=(0, 26)):
    if angle:
        rgba = rgba.rotate(angle, resample=Image.BICUBIC, expand=True)
    if shadow:
        sh, pad = drop_shadow(rgba, *shadow)
        img.alpha_composite(sh, (round(cx - rgba.width / 2 - pad + offset[0]),
                                 round(cy - rgba.height / 2 - pad + offset[1])))
    img.alpha_composite(rgba, (round(cx - rgba.width / 2), round(cy - rgba.height / 2)))


def mascot(m):
    name = m["id"] + ("" if m.get("mood", "happy") == "happy" else f"-{m['mood']}")
    p = CAST / f"{name}.png"
    if not p.is_file():
        sys.exit(f"ERROR: missing cast portrait {p}")
    im = Image.open(p).convert("RGBA")
    # The renderer draws a soft ground shadow under the head. Keep only the
    # opaque head (plus its edge), so place() can add one shadow of its own.
    l, t, r, b = im.getchannel("A").point(lambda v: 255 if v > 200 else 0).getbbox()
    im = im.crop((max(l - 3, 0), max(t - 3, 0), min(r + 3, im.width), min(b + 3, im.height)))
    k = m["size"] / max(im.width, im.height)
    im = im.resize((round(im.width * k), round(im.height * k)), Image.LANCZOS)
    if m.get("flip"):
        im = im.transpose(Image.FLIP_LEFT_RIGHT)
    return im


def build_phone(shot, lang):
    dark = shot["theme"] == "dark"
    img = background(shot["bg"], (W, H), shot["fallback"]).convert("RGBA")
    d = ImageDraw.Draw(img)

    t_font, t_size, t_lines = fit(d, shot["title"][lang], "ExtraBold", range(92, 63, -2), TEXT_W, 2,
                                  f"title {shot['id']}/{lang}")
    s_font, s_size, s_lines = fit(d, shot["sub"][lang], "Medium", range(42, 33, -1), TEXT_W - 40, 2,
                                  f"sub {shot['id']}/{lang}")

    # x in captions.json is for a phone on the right. "side": "left" mirrors it.
    left = shot.get("side", "right") == "left"

    def mx(m):
        return W - m["x"] if left else m["x"]

    def ma(m):
        return -m.get("angle", 0) if left else m.get("angle", 0)

    for m in shot["mascots"]:
        if m.get("z", 1) < 0:
            place(img, mascot(m), mx(m), m["y"], ma(m), shadow=(28, 0.35))

    raw = Image.open(RAW / shot["raw"][lang]).convert("RGB")
    body = phone(raw, shot.get("phone_w", 680), dark)
    dx = shot.get("phone_dx", 105)
    place(img, body, W / 2 + (-dx if left else dx), shot.get("phone_y", 1235), shot.get("tilt", 0),
          shadow=(44, 0.55), offset=(0, 34))

    for m in shot["mascots"]:
        if m.get("z", 1) >= 0:
            place(img, mascot(m), mx(m), m["y"], ma(m))

    text_block(img, [(t_lines, t_font, t_size, 1.08, (255, 255, 255, 255), 22),
                     (s_lines, s_font, s_size, 1.25, (255, 255, 255, 232), 0)], W // 2, 96)
    return img.convert("RGB")


def build_feature(cfg, lang):
    fw, fh = cfg["size"]
    img = background(cfg["bg"], (fw, fh), cfg["fallback"]).convert("RGBA")
    d = ImageDraw.Draw(img)
    for m in sorted(cfg["mascots"], key=lambda m: m.get("z", 0)):
        place(img, mascot(m), m["x"], m["y"], m.get("angle", 0), shadow=(22, 0.4), offset=(0, 16))
    t_font = font("Black", 128)
    s_font, s_size, s_lines = fit(d, cfg["tagline"][lang], "Bold", range(42, 31, -1), 420, 2, f"tagline {lang}")
    block = 128 + 16 + round(s_size * 1.2) * len(s_lines)
    y0 = (fh - block) // 2 - 8
    text_block(img, [(["Seira"], t_font, 128, 1.0, (255, 255, 255, 255), 16),
                     (s_lines, s_font, s_size, 1.2, (255, 255, 255, 240), 0)], 64, y0, align="la", shadow=0.4)
    return img.convert("RGB")


def main():
    cap = json.loads((FRAMES / "captions.json").read_text())
    only = set(sys.argv[1:])
    outs = []
    for lang in LANGS:
        (FRAMES / lang).mkdir(parents=True, exist_ok=True)
        for shot in cap["shots"]:
            if only and shot["id"] not in only:
                continue
            p = FRAMES / lang / f"{shot['id']}.png"
            build_phone(shot, lang).save(p, optimize=True)
            outs.append((p, (W, H), 8 * 1024 * 1024))
    gcfg = cap["feature_graphic"]
    if not only or "feature" in only:
        for lang in LANGS:
            p = GRAPHICS / f"feature-graphic-{lang}.png"
            build_feature(gcfg, lang).save(p, optimize=True)
            outs.append((p, tuple(gcfg["size"]), 15 * 1024 * 1024))

    bad = False
    for p, size, mx in outs:
        with Image.open(p) as im:
            sz, mode = im.size, im.mode
        n = p.stat().st_size
        print(f"{p.relative_to(PS.parent.parent)}  {sz[0]}x{sz[1]}  {mode}  {n / 1024:.0f} KB")
        if sz != size or mode != "RGB" or n > mx:
            print(f"  FAIL: expected {size[0]}x{size[1]} RGB <= {mx // 1048576} MB")
            bad = True
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
