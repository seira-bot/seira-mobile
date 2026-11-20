#!/usr/bin/env python3
"""Cut a 3D icon out of a flat chroma-green background.

Usage:
    python3 scripts/icons3d-cutout.py <raw.png> <out.png> [--size 1024]
        [--padding 0.10] [--lo 0.12] [--hi 0.80] [--debug-dir DIR]

Requires Pillow, numpy and scipy.
"""

import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

BORDER = 12
ALPHA_FLOOR = 0.03
ISLAND_RATIO = 0.005


def smoothstep(lo, hi, x):
    t = np.clip((x - lo) / (hi - lo), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def estimate_background(rgb):
    h, w, _ = rgb.shape
    b = BORDER
    strips = [rgb[:b].reshape(-1, 3), rgb[h - b:].reshape(-1, 3),
              rgb[:, :b].reshape(-1, 3), rgb[:, w - b:].reshape(-1, 3)]
    return np.median(np.concatenate(strips), axis=0)


def key_image(rgb, lo, hi):
    bg = estimate_background(rgb)
    bg_spill = bg[1] - max(bg[0], bg[2])
    if bg_spill <= 0.05:
        raise SystemExit("The background is not green. Check the input image.")
    spill = rgb[..., 1] - np.maximum(rgb[..., 0], rgb[..., 2])
    k = spill / bg_spill
    alpha = 1.0 - smoothstep(lo, hi, k)

    safe = np.maximum(alpha, 1e-6)[..., None]
    fg = (rgb - (1.0 - alpha[..., None]) * bg) / safe
    fg = np.clip(fg, 0.0, 1.0)
    fg[alpha <= 0.004] = 0.0

    fg[..., 1] = np.minimum(fg[..., 1], np.maximum(fg[..., 0], fg[..., 2]))

    alpha[alpha < ALPHA_FLOOR] = 0.0
    return fg, alpha


def drop_islands(alpha):
    labels, count = ndimage.label(alpha > ALPHA_FLOOR)
    if count == 0:
        raise SystemExit("No object found. Check the input image.")
    areas = ndimage.sum(np.ones_like(alpha), labels, index=np.arange(1, count + 1))
    keep = np.flatnonzero(areas >= ISLAND_RATIO * areas.max()) + 1
    mask = np.isin(labels, keep)
    return np.where(mask, alpha, 0.0)


def fit_to_canvas(rgba, size, padding):
    ys, xs = np.nonzero(rgba[..., 3] > ALPHA_FLOOR)
    box = (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)
    img = Image.fromarray(np.round(rgba * 255).astype(np.uint8), "RGBA").crop(box)
    target = size * (1.0 - 2.0 * padding)
    scale = target / max(img.size)
    new = (max(1, round(img.width * scale)), max(1, round(img.height * scale)))
    # Resize in premultiplied alpha so transparent pixels do not bleed colour into edges.
    img = img.convert("RGBa").resize(new, Image.LANCZOS).convert("RGBA")
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.paste(img, ((size - new[0]) // 2, (size - new[1]) // 2))
    arr = np.array(canvas)
    arr[arr[..., 3] == 0, :3] = 0
    return Image.fromarray(arr, "RGBA"), box


def composite(img, hex_color):
    bg = Image.new("RGBA", img.size, hex_color)
    return Image.alpha_composite(bg, img).convert("RGB")


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("raw", type=Path, help="Raw image on a green background.")
    p.add_argument("out", type=Path, help="Output RGBA PNG.")
    p.add_argument("--size", type=int, default=1024, help="Output square size in pixels.")
    p.add_argument("--padding", type=float, default=0.10, help="Padding as a share of the size.")
    p.add_argument("--lo", type=float, default=0.12, help="Lower key threshold.")
    p.add_argument("--hi", type=float, default=0.80, help="Upper key threshold.")
    p.add_argument("--debug-dir", type=Path, help="Write review composites to this folder.")
    args = p.parse_args()

    rgb = np.asarray(Image.open(args.raw).convert("RGB"), dtype=np.float32) / 255.0
    fg, alpha = key_image(rgb, args.lo, args.hi)
    alpha = drop_islands(alpha)
    fg[alpha == 0] = 0.0
    rgba = np.concatenate([fg, alpha[..., None]], axis=-1)

    img, box = fit_to_canvas(rgba, args.size, args.padding)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    img.save(args.out, optimize=True)

    if args.debug_dir:
        args.debug_dir.mkdir(parents=True, exist_ok=True)
        stem = args.out.stem
        composite(img, "#ffffff").save(args.debug_dir / f"{stem}-on-white.png")
        composite(img, "#111113").save(args.debug_dir / f"{stem}-on-dark.png")

    a = np.array(img)[..., 3]
    partial = float(np.mean((a > 0) & (a < 255)))
    print(f"{args.out.name} bbox={box} partial_alpha={partial:.1%}")


if __name__ == "__main__":
    sys.exit(main())
