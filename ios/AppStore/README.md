# App Store listing screenshots

`ios/AppStore/frames/` is the only source of store screenshots. The uploader reads
`frames/<locale>/<iphone-6.9|ipad-13>/*.png` and nothing else.

Each set has 5 frames in two locales (`en-US`, `id`). The sizes are 1320x2868 (iPhone 6.9")
and 2064x2752 (iPad 13"). Every frame is RGB with no alpha channel.

## 1. Capture the raw screenshots

1. Run the `iOS` workflow (`.github/workflows/ios.yml`) with `workflow_dispatch`.
   Set the input `store_screenshots` to `true`. The input `store_runner` picks the runner label.
2. When the job "App Store screenshots" ends, download the artifact `store-screenshots`.
3. Copy the PNG files to `ios/AppStore/raw/`. The names are `<device>-<lang>-<shot>.png`:
   - device: `iphone` or `ipad`
   - lang: `en` or `id`
   - shot: `01-home`, `02-approval`, `03-glance-island` (iPhone only), `03-glance-lock` (iPad only),
     `04-live`, `05-look`

`ios/AppStore/raw/` is gitignored. Only `ios/AppStore/frames/` is committed.

## 2. Compose and render

Captions, layout and the raw file for each frame are in `frames/shots.json`.
`tools/compose.mjs` writes one HTML page per frame. `tools/render-store.sh` draws each page
with headless Chromium and flattens it to RGB.

The look comes from the Google Play frames. The scripts read the night plates, the cast heads
and the Geist fonts from `android/PlayStore/`. They do not copy them.

Run this from the repository root on a build host, inside a memory cap:

```bash
systemd-run --user --scope -q -p MemoryMax=4G -p MemorySwapMax=0 bash ios/AppStore/tools/render-store.sh
```

The host needs node, chromium (or `CHROMIUM=<path>`), and python3 with Pillow.
The script prints `ok` or `FAIL` for each of the 20 frames and exits 1 if one fails.
If the title of a caption is too wide, the page shrinks the font. Change the words only in `shots.json`.

## 3. Upload

```bash
export ASC_KEY_ID=... ASC_ISSUER_ID=... ASC_KEY_PATH=/path/to/AuthKey_<id>.p8 ASC_APP_ID=...
node ios/AppStore/tools/asc-listing.mjs check
node ios/AppStore/tools/asc-listing.mjs status
node ios/AppStore/tools/asc-listing.mjs text
node ios/AppStore/tools/asc-listing.mjs screenshots --replace
```

`check` needs no credentials. The other commands call the App Store Connect API.
