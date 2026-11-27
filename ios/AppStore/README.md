# App Store listing images

`ios/AppStore/frames/` is the only source of store images. Every image is RGB with no alpha channel.
Each set exists in two locales (`en-US`, `id`).

| Folder in `frames/<locale>/` | Size | Files | Upload path |
|------------------------------|------|-------|-------------|
| `iphone-6.9/` | 1320x2868 | 5 | `screenshots` |
| `ipad-13/` | 2064x2752 | 5 | `screenshots` |
| `iphone-duo/` | 2007x2853 | 4 | `assets`, then `place` |
| `creative/header.png` | 3840x1646 | 1 | `assets`, then `place` |
| `creative/search-results.png` | 3840x2560 | 1 | `assets`, then `place` |

The header shows at the top of the product page. The search results image replaces the
screenshots in the search results card. Both show on iOS and iPadOS 27 or later.

## 1. Capture the raw screenshots

1. Run the `iOS` workflow (`.github/workflows/ios.yml`) with `workflow_dispatch`.
   Set the input `store_screenshots` to `true`. The input `store_runner` picks the runner label.
   The input `store_devices` picks the simulators: `both` (iPhone and iPad), `iphone`, `ipad`, `duo`, or `all`.
2. When the job "App Store screenshots" ends, download the artifact `store-screenshots`.
3. Copy the PNG files to `ios/AppStore/raw/`. The names are `<device>-<lang>-<shot>.png`:
   - device: `iphone`, `ipad` or `duo`
   - lang: `en` or `id`
   - shot: `01-home`, `02-approval`, `03-glance-island` (iPhone only), `03-glance-lock`,
     `04-live`, `05-look`, `06-pair`

The iPhone Duo simulator photographs the outer display at 1398x2034. The app uses the left
1146 pixels, and the status bar sits in a strip on the right. The Duo layout keeps only the app area.
The Duo lock screen shot shows the home screen, so the Duo set has no glance frame.

`ios/AppStore/raw/` is gitignored. Only `ios/AppStore/frames/` is committed.

## 2. Compose and render

- `frames/shots.json` holds the captions, the layouts and the raw file for each screenshot frame.
  `tools/compose.mjs` writes one HTML page per frame.
- `frames/creatives.json` holds the header and the search results image: crops of the iPhone raw
  screenshots, cast heads, the red thread, and the Apple art safe area. `tools/compose-creative.mjs`
  writes their pages.
- `tools/render-store.sh` draws each page with headless Chromium and flattens it to RGB.

The look comes from the Google Play frames. The scripts read the night plates, the cast heads
and the Geist fonts from `android/PlayStore/`. They do not copy them.

Run this from the repository root on a build host, inside a memory cap:

```bash
systemd-run --user --scope -q -p MemoryMax=4G -p MemorySwapMax=0 bash ios/AppStore/tools/render-store.sh [filter]
```

- A filter draws only the pages whose name holds it, for example `iphone-duo` or `creative`.
- `SAFE=<dir>` draws the header and the search results image with the safe area into `<dir>`.
  Keep the key art and all text inside the dashed box.
- The host needs node, chromium (or `CHROMIUM=<path>`), and python3 with Pillow.
- The script prints `ok` or `FAIL` for each image and exits 1 if one fails.
- If the title of a caption is too wide, the page shrinks the font. Change the words only in the JSON files.

Apple rules for the header and the search results image: a short phrase is allowed, but no price,
URL, copyright sign, unproven claim, other platform logo or Apple badge.

## 3. Upload

```bash
export ASC_KEY_ID=... ASC_ISSUER_ID=... ASC_KEY_PATH=/path/to/AuthKey_<id>.p8 ASC_APP_ID=...
node ios/AppStore/tools/asc-listing.mjs check
node ios/AppStore/tools/asc-listing.mjs status
node ios/AppStore/tools/asc-listing.mjs text
node ios/AppStore/tools/asc-listing.mjs screenshots --replace
node ios/AppStore/tools/asc-listing.mjs assets
node ios/AppStore/tools/asc-listing.mjs place --replace
```

- `check` needs no credentials. The other commands call the App Store Connect API.
- `screenshots` uploads the iPhone 6.9" and iPad 13" sets to the editable version.
- `assets` uploads the Duo screenshots, the header and the search results image to the
  App Asset Library. It reuses an image that has the same content. It changes no version.
- `place` attaches those images to the editable version. It refuses when no version is editable.
- No command submits for review, withdraws, or releases a version.

Apple accepts creative assets for the first version of an app only with that version's submission.
After the first version is live, attach them to the next version, or submit them alone from the
asset library.
