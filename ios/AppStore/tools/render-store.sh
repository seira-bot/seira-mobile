#!/usr/bin/env bash
# Draws the App Store screenshots (frames/shots.json) and creative assets (frames/creatives.json)
# from raw/. Run from the repository root on a build host, inside a memory cap:
#   systemd-run --user --scope -q -p MemoryMax=4G -p MemorySwapMax=0 bash ios/AppStore/tools/render-store.sh [filter]
# A filter draws only the pages whose name holds it, for example "creative" or "iphone-duo".
# SAFE=<dir> draws the creative assets with their art safe area into <dir> instead of frames/.
# Needs node, chromium (or CHROMIUM=<path>), and python3 with Pillow.
# Prints ok or FAIL for each image and exits 1 when one fails.
set -euo pipefail
cd "$(dirname "$0")/.."
CHROMIUM=${CHROMIUM:-chromium}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

FILTER=${1:-}
SAFE=${SAFE:-}
if [ -n "$SAFE" ]; then
  node tools/compose-creative.mjs "$work/pages" --safe >/dev/null
  echo "[]" > "$work/pages/pages.json"
else
  node tools/compose.mjs "$work/pages" >/dev/null
  node tools/compose-creative.mjs "$work/pages" >/dev/null
fi
python3 -I - "$work" "$CHROMIUM" "$FILTER" "$SAFE" <<'PY'
import json, os, subprocess, sys
from PIL import Image

work, chromium, only, safe = sys.argv[1:5]
pages = []
for list_name in ("pages.json", "creative-pages.json"):
    pages += json.load(open(os.path.join(work, "pages", list_name)))
pages = [p for p in pages if only in p["name"]]
if safe:
    for p in pages:
        p["target"] = os.path.join(safe, p["name"] + ".png")
if not pages:
    print("FAIL no page matches the filter")
    sys.exit(1)
failed = False
for p in pages:
    html = os.path.join(work, "pages", p["name"] + ".html")
    shot = os.path.join(work, p["name"] + ".png")
    try:
        subprocess.run([
            chromium, "--headless=new", "--no-sandbox", "--disable-gpu", "--hide-scrollbars",
            "--allow-file-access-from-files", "--force-device-scale-factor=1",
            "--virtual-time-budget=4000", f"--window-size={p['width']},{p['height']}",
            f"--screenshot={shot}", "file://" + html,
        ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=180)
        im = Image.open(shot).convert("RGBA")
        # App Store Connect wants a PNG with no alpha channel.
        flat = Image.new("RGB", im.size, (20, 10, 22))
        flat.paste(im, mask=im.split()[-1])
        os.makedirs(os.path.dirname(p["target"]), exist_ok=True)
        flat.save(p["target"], optimize=True)
        size = os.path.getsize(p["target"])
        ok = flat.size == (p["width"], p["height"]) and flat.mode == "RGB"
        detail = f"{flat.size[0]}x{flat.size[1]} {flat.mode} {size // 1024} KB"
    except Exception as e:
        ok, detail = False, f"error: {e}"
    failed |= not ok
    print(f"{'ok  ' if ok else 'FAIL'} {p['target']} {detail}")
sys.exit(1 if failed else 0)
PY
