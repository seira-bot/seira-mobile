#!/usr/bin/env node
// Builds the Android Seira mascot data from a seira-bot/seira clone:
//   - android/app/src/main/kotlin/com/openmausbot/companion/character/SeiraBodies.kt
//   - android/app/src/main/assets/seira-mascot/**  (head, hair and accessory pictures)
//
// Usage: SEIRA_REPO=<seira clone> node scripts/gen-seira-bodies-kotlin.mjs [--check]
//
// --check writes nothing and exits 1 when the committed files differ from the output.

import { copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, "..");
const seira = process.env.SEIRA_REPO;
if (!seira || !existsSync(join(seira, "packages/phone-protocol/src/carved-bodies.ts"))) {
  console.error("Set SEIRA_REPO to a clone of seira-bot/seira (the folder that holds packages/phone-protocol).");
  process.exit(2);
}
const check = process.argv.includes("--check");

const KOTLIN_OUT = join(repo, "android/app/src/main/kotlin/com/openmausbot/companion/character/SeiraBodies.kt");
const ASSETS_OUT = join(repo, "android/app/src/main/assets/seira-mascot");
// The sizes the app draws from. The desktop ships more; two cover every avatar from a row to the call screen.
const SIZES = [88, 256];

const { CARVED_BODIES, BRAND_BODY_IDS } = await import(
  pathToFileURL(join(seira, "packages/phone-protocol/src/carved-bodies.ts")).href
);
const placement = JSON.parse(readFileSync(join(seira, "src/assets/seira-accessory/placement.json"), "utf8"));

const num = (n) => {
  const text = Number.isInteger(n) ? `${n}.0` : String(n);
  return `${text}f`;
};
const argb = (hex) => `0x${(0xff000000 + parseInt(hex.slice(1), 16)).toString(16).toUpperCase()}.toInt()`;
const str = (s) => JSON.stringify(s).replace(/\$/g, "\\$");

const sourceDir = (id) =>
  id === "seira" ? join(seira, "src/assets/seira-body") : join(seira, "src/assets/seira-hair", id);

/** The hair picture of a style: its file extension, or null when the style has no hair picture. */
const hairExtension = (id) => {
  const names = readdirSync(sourceDir(id));
  const hair = names.find((n) => n.startsWith(`hair-${SIZES[SIZES.length - 1]}.`));
  return hair ? hair.slice(hair.lastIndexOf(".") + 1) : null;
};

const copies = [];
const bodyLines = [];
for (const id of BRAND_BODY_IDS) {
  const body = CARVED_BODIES[id];
  const style = placement.styles[id];
  if (!style) throw new Error(`placement.json has no style ${id}`);
  const ext = hairExtension(id);
  for (const px of SIZES) {
    copies.push([join(sourceDir(id), `front-${px}.webp`), join(ASSETS_OUT, id, `front-${px}.webp`)]);
    if (ext) copies.push([join(sourceDir(id), `hair-${px}.${ext}`), join(ASSETS_OUT, id, `hair-${px}.${ext}`)]);
  }
  const e = body.eyes;
  const anchors = Object.entries(style.anchors)
    .map(([name, [x, y]]) => `${str(name)} to SeiraPoint(${num(x)}, ${num(y)})`)
    .join(", ");
  bodyLines.push(`        ${str(id)} to SeiraBody(
            id = ${str(id)},
            name = ${str(body.name)},
            hairPicture = ${ext ? str(ext) : "null"},
            eyes = SeiraEyes(
                cx = ${num(e.cx)}, cy = ${num(e.cy)}, dx = ${num(e.dx)}, w = ${num(e.w)}, h = ${num(e.h)},
                round = ${num(e.round)}, ink = ${argb(e.ink)},
                mouth = ${e.mouth}, mouthInk = ${argb(e.mouthInk)},
                mouthY = ${e.mouthY === null ? "null" : num(e.mouthY)},
            ),
            clip = ${str(body.clip)},
            headRadius = ${num(style.r)},
            anchors = mapOf(${anchors}),
        ),`);
}

const stickerLines = [];
for (const [id, s] of Object.entries(placement.accessories)) {
  copies.push([join(seira, "src/assets/seira-accessory", s.file), join(ASSETS_OUT, "accessory", s.file)]);
  const [px, py] = s.pivot ?? [s.size[0] / 2, s.size[1] / 2];
  stickerLines.push(`        ${str(id)} to SeiraSticker(
            id = ${str(id)}, slot = ${str(s.slot)}, file = ${str(s.file)}, anchor = ${str(s.anchor ?? "top")},
            width = ${num(s.width ?? 1)}, pivotX = ${num(px)}, pivotY = ${num(py)},
            pictureWidth = ${num(s.size[0])}, pictureHeight = ${num(s.size[1])},
        ),`);
}

const kotlin = `// GENERATED FILE. Do not edit by hand.
// Source: packages/phone-protocol/src/carved-bodies.ts and src/assets/seira-accessory/placement.json in seira-bot/seira.
// Command: SEIRA_REPO=<seira clone> node scripts/gen-seira-bodies-kotlin.mjs
package com.openmausbot.companion.character

internal class SeiraPoint(val x: Float, val y: Float)

/** Where the eyes and mouth sit on a body, in the body's 240-unit square. */
internal class SeiraEyes(
    val cx: Float,
    val cy: Float,
    val dx: Float,
    val w: Float,
    val h: Float,
    val round: Float,
    val ink: Int,
    val mouth: Boolean,
    val mouthInk: Int,
    val mouthY: Float?,
)

internal class SeiraBody(
    val id: String,
    val name: String,
    /** File extension of the hair picture, or null when the style has none. */
    val hairPicture: String?,
    val eyes: SeiraEyes,
    /** SVG path data of the visor, where the eyes may show. */
    val clip: String,
    val headRadius: Float,
    val anchors: Map<String, SeiraPoint>,
)

internal class SeiraSticker(
    val id: String,
    val slot: String,
    val file: String,
    val anchor: String,
    /** Width in head radii. */
    val width: Float,
    val pivotX: Float,
    val pivotY: Float,
    val pictureWidth: Float,
    val pictureHeight: Float,
)

internal object SeiraBodies {
    /** The pixel sizes of the bundled head and hair pictures, smallest first. */
    val pictureSizes: List<Int> = listOf(${SIZES.join(", ")})

    /** Every look the picker offers, in order. */
    val ids: List<String> = listOf(${BRAND_BODY_IDS.map(str).join(", ")})

    val all: Map<String, SeiraBody> = mapOf(
${bodyLines.join("\n")}
    )

    val stickers: Map<String, SeiraSticker> = mapOf(
${stickerLines.join("\n")}
    )
}
`;

if (check) {
  const same = existsSync(KOTLIN_OUT) && readFileSync(KOTLIN_OUT, "utf8") === kotlin
    && copies.every(([from, to]) => existsSync(to) && readFileSync(from).equals(readFileSync(to)));
  console.log(same ? "Seira mascot data is current." : "Seira mascot data is out of date. Run the generator.");
  process.exit(same ? 0 : 1);
}

rmSync(ASSETS_OUT, { recursive: true, force: true });
for (const [from, to] of copies) {
  mkdirSync(dirname(to), { recursive: true });
  copyFileSync(from, to);
}
writeFileSync(KOTLIN_OUT, kotlin);
console.log(`Wrote ${KOTLIN_OUT.slice(repo.length + 1)} and ${copies.length} pictures.`);
