#!/usr/bin/env bash
# Draws the Play screenshots and feature graphics from frames/shots.json.
# Run from the repository root on a build host, inside a memory cap:
#   systemd-run --user --scope -q -p MemoryMax=4G -p MemorySwapMax=0 bash android/PlayStore/tools/render-store.sh
# Needs node, chromium (or CHROMIUM=<path>), and python3 with Pillow.
set -euo pipefail
cd "$(dirname "$0")/.."
CHROMIUM=${CHROMIUM:-chromium}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

node tools/compose.mjs "$work/pages" >/dev/null
python3 -I - "$work" "$CHROMIUM" <<'EOF'
import json, os, subprocess, sys
from PIL import Image

work, chromium = sys.argv[1], sys.argv[2]
pages = json.load(open(os.path.join(work, "pages", "pages.json")))
failed = False
for p in pages:
    html = os.path.join(work, "pages", p["name"] + ".html")
    shot = os.path.join(work, p["name"] + ".png")
    subprocess.run([
        chromium, "--headless=new", "--no-sandbox", "--disable-gpu", "--hide-scrollbars",
        "--allow-file-access-from-files", "--force-device-scale-factor=1",
        "--virtual-time-budget=4000", f"--window-size={p['width']},{p['height']}",
        f"--screenshot={shot}", "file://" + html,
    ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    im = Image.open(shot)
    # Play wants 24-bit PNG with no alpha.
    flat = Image.new("RGB", im.size, (20, 10, 22))
    flat.paste(im.convert("RGBA"), mask=im.convert("RGBA").split()[-1])
    os.makedirs(os.path.dirname(p["target"]), exist_ok=True)
    flat.save(p["target"], optimize=True)
    size = os.path.getsize(p["target"])
    ok = flat.size == (p["width"], p["height"]) and flat.mode == "RGB" and size <= 8 * 1024 * 1024
    failed |= not ok
    print(f"{'ok  ' if ok else 'FAIL'} {p['target']} {flat.size[0]}x{flat.size[1]} {flat.mode} {size // 1024} KB")
sys.exit(1 if failed else 0)
EOF
