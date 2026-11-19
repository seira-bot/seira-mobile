#!/usr/bin/env python3
"""Build framed Google Play screenshots and feature graphics.

Run from anywhere: python3 android/PlayStore/tools/frame.py
Reads ../frames/captions.json and ../raw/*.png. Writes ../frames/<lang>/<id>.png
and ../graphics/feature-graphic-<lang>.png. The app renders are only scaled and
given rounded corners. No UI is drawn here.
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
PS = HERE.parent
RAW = PS / "raw"
FRAMES = PS / "frames"
GRAPHICS = PS / "graphics"
FONT_REG = HERE / "fonts" / "Geist-Regular.ttf"
FONT_SEMI = HERE / "fonts" / "Geist-SemiBold.ttf"
HEAD = GRAPHICS / "source" / "seira-head.png"
LANGS = ["en-US", "id-ID"]

W, H = 1080, 1920
MAX_TEXT_W = 920

THEMES = {
    "light": dict(top="#FBF7F8", bottom="#F1ECEE", glow=0.10, title="#141417",
                  sub="#5B5B66", border="#E4E1E3", shadow=0.18),
    "dark": dict(top="#111113", bottom="#1A1A1E", glow=0.18, title="#F2F2F3",
                 sub="#A6A6B0", border="#2A2A30", shadow=0.50),
}


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def font(path, size):
    return ImageFont.truetype(str(path), size)


def vgradient(size, top, bottom):
    w, h = size
    col = Image.new("RGB", (1, h))
    t, b = rgb(top), rgb(bottom)
    for y in range(h):
        k = y / (h - 1)
        col.putpixel((0, y), tuple(round(t[i] + (b[i] - t[i]) * k) for i in range(3)))
    return col.resize((w, h))


def add_glow(img, color, opacity, cx, cy, radius):
    """Smooth radial glow: full strength at center, zero at radius."""
    g = Image.radial_gradient("L").resize((2 * radius, 2 * radius), Image.BICUBIC)
    # radial_gradient reaches 128 at the square's edge midpoint: map 0..128 to full..zero
    g = g.point(lambda v: int(max(0, 255 - v * 2) * opacity))
    g = g.filter(ImageFilter.GaussianBlur(radius / 8))
    layer = Image.new("RGB", g.size, rgb(color))
    img.paste(layer, (cx - radius, cy - radius), g)


def wrap(draw, text, fnt, max_w, max_lines, what):
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
    if len(lines) == 2:
        # balance the two lines so no single word is left alone on line 2
        best = min(range(1, len(words)),
                   key=lambda i: max(draw.textlength(" ".join(words[:i]), font=fnt),
                                     draw.textlength(" ".join(words[i:]), font=fnt)))
        lines = [" ".join(words[:best]), " ".join(words[best:])]
    if len(lines) > max_lines:
        sys.exit(f"ERROR: {what} needs {len(lines)} lines (max {max_lines}): {text!r}")
    for ln in lines:
        if draw.textlength(ln, font=fnt) > max_w:
            sys.exit(f"ERROR: {what} line too wide: {ln!r}")
    return lines


def draw_lines(draw, lines, fnt, fill, y, line_h, cx=None, x=None):
    for ln in lines:
        if cx is not None:
            draw.text((cx, y), ln, font=fnt, fill=fill, anchor="ma")
        else:
            draw.text((x, y), ln, font=fnt, fill=fill, anchor="la")
        y += line_h
    return y


def rounded_mask(size, radius, scale=4):
    w, h = size
    m = Image.new("L", (w * scale, h * scale), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w * scale - 1, h * scale - 1),
                                        radius * scale, fill=255)
    return m.resize((w, h), Image.LANCZOS)


def build_phone(shot, lang):
    th = THEMES[shot["theme"]]
    img = vgradient((W, H), th["top"], th["bottom"])
    add_glow(img, "#FF3D64", th["glow"], W // 2, 700, 700)
    d = ImageDraw.Draw(img)

    f_t, f_s = font(FONT_SEMI, 68), font(FONT_REG, 36)
    t_lines = wrap(d, shot["title"][lang], f_t, MAX_TEXT_W, 2, f"title {shot['id']}/{lang}")
    s_lines = wrap(d, shot["sub"][lang], f_s, MAX_TEXT_W, 2, f"sub {shot['id']}/{lang}")
    t_lh, s_lh = round(68 * 1.15), round(36 * 1.3)

    y = draw_lines(d, t_lines, f_t, th["title"], 110, t_lh, cx=W // 2)
    # last title line occupies t_lh; sub starts 22 px below its bottom
    sub_top = y + 22
    sub_bottom = draw_lines(d, s_lines, f_s, th["sub"], sub_top, s_lh, cx=W // 2)
    sub_bottom -= (s_lh - 36)  # trim trailing leading to the glyph box

    raw = Image.open(RAW / shot["raw"][lang]).convert("RGB")
    top = max(400, sub_bottom + 56)
    sw = 820
    while True:
        sh = round(raw.height * sw / raw.width)
        if top + sh <= H - 16:
            break
        sw -= 4
    shot_img = raw.resize((sw, sh), Image.LANCZOS)
    x = (W - sw) // 2

    mask = rounded_mask((sw, sh), 44)

    # shadow
    sh_layer = Image.new("L", (W, H), 0)
    sh_layer.paste(mask.point(lambda v: int(v * th["shadow"])), (x, top + 16))
    sh_layer = sh_layer.filter(ImageFilter.GaussianBlur(40 / 2))
    img.paste(Image.new("RGB", (W, H), (0, 0, 0)), (0, 0), sh_layer)

    # border: rounded rect 2 px larger-in-ring, following the shape
    bw = 2
    outer = rounded_mask((sw + 2 * bw, sh + 2 * bw), 44 + bw)
    img.paste(Image.new("RGB", outer.size, rgb(th["border"])), (x - bw, top - bw), outer)
    # screenshot on top, inset so the ring shows
    inner_mask = mask
    img.paste(shot_img, (x, top), inner_mask)
    return img, dict(shot_w=sw, top=top, bottom=top + sh)


def build_feature(cfg, lang):
    fw, fh = cfg["size"]
    img = Image.new("RGB", (fw, fh), rgb("#0E0E10"))
    add_glow(img, "#FF3D64", 0.22, 260, 250, 380)
    add_glow(img, "#4F8DFF", 0.08, 900, 120, 380)
    d = ImageDraw.Draw(img)

    head = Image.open(HEAD).convert("RGBA").resize((300, 300), Image.LANCZOS)
    img.paste(head, (80, (fh - 300) // 2), head)

    f_t, f_s = font(FONT_SEMI, 104), font(FONT_REG, 40)
    t_lh, s_lh = round(104 * 1.15), round(40 * 1.3)
    lines = wrap(d, cfg["tagline"][lang], f_s, 540, 2, f"tagline {lang}")
    block = t_lh + 18 + len(lines) * s_lh - (s_lh - 40)
    y0 = (fh - block) // 2
    d.text((430, y0), cfg["title"], font=f_t, fill=rgb("#F2F2F3"), anchor="la")
    draw_lines(d, lines, f_s, rgb("#C9C9D1"), y0 + t_lh + 18, s_lh, x=430)
    return img


def main():
    cap = json.loads((FRAMES / "captions.json").read_text())
    outs = []  # (path, expected size, max bytes)
    for lang in LANGS:
        (FRAMES / lang).mkdir(parents=True, exist_ok=True)
        for shot in cap["shots"]:
            img, info = build_phone(shot, lang)
            p = FRAMES / lang / f"{shot['id']}.png"
            img.save(p, optimize=True)
            outs.append((p, (W, H), 8 * 1024 * 1024))
    gcfg = cap["feature_graphic"]
    for lang in LANGS:
        img = build_feature(gcfg, lang)
        p = GRAPHICS / f"feature-graphic-{lang}.png"
        img.save(p, optimize=True)
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
