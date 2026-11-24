# 3D icon prompts

Use these prompts to make a new icon that matches the set.

## Make a new icon

1. Add the icon to `manifest.json` (name, place, size in pt, label).
2. Join the style prompt and the subject line with one space. Send it to the image generator at 1024x1024 with no reference image.
3. Cut out the result: `python3 scripts/icons3d-cutout.py <raw.png> shared/icons3d/src/<name>.png --debug-dir <tmp>`.
4. Look at the debug composites on white and on `#111113`. Reject the icon if it has a halo, a green edge, or a different angle or light.
5. Export: `python3 scripts/export-icons3d.py`. Then check: `python3 scripts/export-icons3d.py --check`.
6. Look at `contact-light.png` and `contact-dark.png` at 30 pt. Reject the icon if it is not readable there.

The generator cannot make a transparent background. The prompt asks for flat chroma green, and the cutout script keys it out.
The palette (off-white, near-black, pink) has no green, so the key does not change icon colours.

Model: gpt-image-2.6 (flare variant). Raw size: 1254x1254 RGB.

## Style prompt (same for every icon)

```text
A single 3D icon for a mobile app. Style: cute collectible vinyl designer-toy render, the same soft matte vinyl plastic as a premium art toy: smooth chunky rounded forms, soft bevelled edges, slightly inflated proportions, simple shapes with few parts. Strict palette of only three colours: soft off-white vinyl #ECEEF1 for the main body, glossy near-black #0C0F14 for screens, lenses, holes and dark parts, and pink-red #FF2A57 as the only accent on one or two small details. No other colours, no gradients of other hues. Lighting: soft studio key light from the top left, gentle fill from the right, soft highlights on top-left edges, soft shading on the lower right. Camera: three-quarter view, object turned about 30 degrees to the right and seen from about 20 degrees above, almost no perspective distortion. Object centred with even margin on all sides, about 80 percent of the frame. A small soft dark contact shadow directly under the object. Background: one perfectly flat solid chroma-key green #00B140 filling the whole image edge to edge, no gradient, no floor line, no horizon, no vignette, and no green reflections or green tint on the object. No text, no letters, no numbers, no logos, no outlines, no line art, no extra objects. Subject:
```

## Subject lines

| Icon | Subject |
|------|---------|
| `camera` | a compact photo camera with a big round glossy black lens on the front, a small round pink shutter button on top, and a small viewfinder window. |
| `photos` | two rounded photo cards standing slightly fanned, one behind the other; the front card has a thick off-white frame around a glossy near-black picture area that shows a small round pink sun and two soft off-white rounded hills. |
| `files` | a chunky rounded document folder, slightly open, with an off-white folder body, a glossy near-black inner back wall, one off-white paper sheet peeking out of the top, and a small pink tab on the front. |
| `live-call` | a round off-white speech bubble with a short rounded tail at its lower left; on its front face, a glossy near-black inset circle holding a pink sound wave made of five rounded vertical bars of different heights, tallest in the middle. |
| `schedule` | a chunky off-white desk calendar block with a glossy near-black top band and two small rings on top, and a small round clock leaning on its lower right corner; the clock has an off-white rim, a glossy near-black face and two short pink hands. |
| `cloud-desktop` | a small computer monitor on a short stand, off-white body with a glossy near-black screen and a small pink light on its lower edge, with a puffy off-white cloud floating just above and slightly behind its top edge. |
| `appearance` | a thick round disc split down the middle into two halves: the left half is off-white with short rounded sun rays around its left edge, the right half is glossy near-black and shaped like a crescent moon, with one small pink star on the dark half. |
| `computer` | an open laptop with an off-white body and keyboard deck, a glossy near-black screen, and a small pink power light on the deck. |
| `notifications` | a rounded notification bell with a small round pink dot badge on its upper right, and a small round clapper at the bottom. |
| `voice` | a classic rounded microphone on a short stand: a glossy near-black rounded grille head, an off-white handle and U-shaped holder, and a small pink ring where the head meets the handle. |
| `privacy` | a rounded protective shield standing upright, with a small padlock on its front; the padlock body is glossy black with a small pink keyhole. |
| `help` | a chunky lifebuoy ring lying slightly tilted toward the viewer, with four alternating segments: two off-white and two pink, and a thin glossy near-black rope loop around it. |
| `empty-chats` | two overlapping rounded speech bubbles: a larger off-white bubble in front at the lower left with three small round pink dots on it, and a smaller glossy near-black bubble behind it at the upper right. |
| `not-connected` | a compact group: an off-white laptop with off-white keys and a glossy near-black screen at the back left, and a smaller off-white phone with a glossy near-black screen at the front right, apart from each other. Between them, in front, floats a very large chunky off-white vinyl chain link broken into two separate halves with a wide visible gap in the middle, each half casting a soft shadow. No pink anywhere. Use the reference image for the exact laptop, phone, material, light, camera angle and background. |
| `offline` | a puffy off-white cloud with one thick rounded pink diagonal slash bar across it from upper left to lower right. |
| `all-done` | a round off-white badge with a soft scalloped edge like a rosette seal, a thick glossy near-black check mark on its face, and a small four-point pink sparkle floating at its upper right. |
| `pair` | a compact group that fits a square: an off-white laptop with off-white keys and a glossy near-black screen on the left, and a smaller off-white phone with a glossy near-black screen standing close in front of its right side, slightly overlapping it. One whole closed pink chain link of two interlocked links joins the phone to the laptop. A small round pink badge with a thick off-white check mark floats above the phone. Use the reference image for the exact laptop, phone, material, light, camera angle and background. |

`pair` and `not-connected` use one reference image: an earlier render of the same laptop and phone on green. This keeps the devices the same in both icons. Use the raw image of the other icon as the reference when you remake one of them.
Pink means "connected". The broken link in `not-connected` is off-white, so it shows on light and dark backgrounds.

## App icon

The app icon is direction "Malam": the 3D head, tilted, with one eye in a wink, on an ink tile with red light from the visor.
The source is `app-icon/head-tilt.png`: the head with the visor switched off.
The export script draws the face on the visor with the `face.js` geometry of the brand kit.

1. Make the head with the prompt below at 1024x1024. Use one reference image: the front base frame of the 3D character (`brand/mascot/art/bases/out/seira/front.png` in `seira-bot/seira`) on flat `#00FF00`.
2. Key out the green. The current head uses `key_green` from `brand/mascot/art/bases/tools/process.py` in `seira-bot/seira`, at the raw size (1254 px).
3. Make the master and the monochrome vector: `python3 scripts/export-app-icon.py --render`.
4. Export the icons: `python3 scripts/export-app-icon.py`. Then check: `python3 scripts/export-app-icon.py --check`.
5. Look at the icon in circle, squircle and themed masks at 48 dp, and on iOS at 29 pt. Reject a head that is not readable there.

Model: gpt-image-2.6. Raw size: 1254x1254 RGB.

```text
App icon hero render of the robot mascot in the reference image: front view of the head only, the head tilted about 10 degrees to its left in a friendly, curious pose, centered, the head fills about 70 percent of the square image. Keep this exact character from the reference image: same white matte head shell, same silver-white hair shell with a straight fringe split into 4 panels and two long smooth side panels, same glossy near-black screen visor. The visor screen is switched off: plain glossy black glass with soft studio reflections, no face, no eyes, no mouth, no light on the screen. Premium soft-touch materials, pearl-white with subtle subsurface glow, soft large studio key light from upper left, gentle rim light. Background: perfectly flat solid pure chroma green #00FF00 over the whole image, no floor, no cast shadow, no gradient, no vignette, no green reflections on the character. No text, no letters, no watermark.
```

## Earlier app icon candidates

The app icon used `candidate-1-light` until 2026-11-24. These candidates stay for reference.

These prompts use two reference images: the flat Seira mark, and the front view of the 3D character.
The candidates are opaque full-bleed squares. The OS applies the corner mask.

### `candidate-1-light`

```text
A square 3D app icon, full-bleed, no rounded corners, no border, no frame. Cute collectible vinyl designer-toy render, soft matte vinyl, soft studio key light from the top left, gentle fill. Strict palette: soft off-white vinyl #ECEEF1, glossy near-black #0C0F14, pink-red #FF2A57 only. No text, no letters, no logos, no extra objects. The first reference is the flat brand mark, the second reference is the 3D character; use them for shape and material only. The icon shows one mascot head built from the flat mark: an arch-shaped head with straight vertical sides, a flat base and a half-round dome top, made of smooth off-white vinyl like sculpted hair, with a short straight fringe split into a few soft vertical sections. Below the fringe, a wide recessed glossy near-black visor screen with a straight top edge and a rounded bottom. On the visor, two glowing pink-red vertical pill eyes, each with one small white highlight dot and a small eyelash flick at the outer top corner, as in the flat mark. Front view, head centred, about 62 percent of the icon width, small soft shadow under it. Background: smooth soft light grey #F4F4F6 with a very subtle lighter glow behind the head.
```

### `candidate-2-dark`

```text
A square 3D app icon, full-bleed, no rounded corners, no border, no frame. Cute collectible vinyl designer-toy render, soft matte vinyl, soft studio key light from the top left, gentle fill. Strict palette: soft off-white vinyl #ECEEF1, glossy near-black #0C0F14, pink-red #FF2A57 only. No text, no letters, no logos, no extra objects. The first reference is the flat brand mark, the second reference is the 3D character; use them for shape and material only. The icon shows one mascot head built from the flat mark: an arch-shaped head with straight vertical sides, a flat base and a half-round dome top, made of smooth off-white vinyl like sculpted hair, with a short straight fringe split into a few soft vertical sections. Below the fringe, a wide recessed glossy near-black visor screen with a straight top edge and a rounded bottom. On the visor, two glowing pink-red vertical pill eyes, each with one small white highlight dot and a small eyelash flick at the outer top corner, as in the flat mark. Front view, head centred, about 62 percent of the icon width, small soft shadow under it. Background: deep near-black #111113 with a very subtle soft pink glow behind the head.
```

### `candidate-3-pink`

```text
A square 3D app icon, full-bleed, no rounded corners, no border, no frame. Cute collectible vinyl designer-toy render, soft matte vinyl, soft studio key light from the top left, gentle fill. Strict palette: soft off-white vinyl #ECEEF1, glossy near-black #0C0F14, pink-red #FF2A57 only. No text, no letters, no logos, no extra objects. The first reference is the flat brand mark, the second reference is the 3D character; use them for shape and material only. The icon shows the head of the 3D character: a round off-white vinyl hair dome with a straight blunt fringe and two straight side panels of hair, and a wide glossy near-black visor face with two glowing pink-red pill eyes with eyelash flicks, as in the flat mark, and a small pink smile. Front view, head centred, about 62 percent of the icon width, small soft shadow under it. Background: flat Seira pink-red #FF2A57 with a subtle lighter glow behind the head.
```
