#!/usr/bin/env node
// Writes the animated Seira character kit for Android from a seira-bot/seira clone:
//   android/app/src/main/assets/seira-character/{kit.json,placement.json,cast.json,frames/<style>/*.webp}
//
// Usage: SEIRA_REPO=<seira clone> node scripts/sync-seira-character-android.mjs [--check]
//
// --check writes nothing and exits 1 when the committed files differ from the output.

import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, "..");
const seira = process.env.SEIRA_REPO;
if (!seira || !existsSync(join(seira, "src/assets/seira-character/kit.json"))) {
  console.error("Set SEIRA_REPO to a clone of seira-bot/seira (the folder that holds src/assets).");
  process.exit(2);
}
const check = process.argv.includes("--check");

const OUT = join(repo, "android/app/src/main/assets/seira-character");
const VIEWS = ["front", "yaw20"];
const VIEW_FIELDS = ["file", "pivot", "r", "visorBox", "measuredYaw", "anchors", "hair", "visor"];
const CAST_COUNT = 25;

const kitSource = JSON.parse(readFileSync(join(seira, "src/assets/seira-character/kit.json"), "utf8"));
const placementSource = JSON.parse(readFileSync(join(seira, "src/assets/seira-accessory/placement.json"), "utf8"));

/** @type {Map<string, Buffer | string>} */
const outputs = new Map();
const frameFile = (path) => {
  const source = join(seira, "src/assets/seira-character", path);
  if (!existsSync(source)) throw new Error(`The kit names a missing file: ${path}`);
  outputs.set(`${path}`, readFileSync(source));
  return path;
};

const styles = {};
for (const [id, style] of Object.entries(kitSource.styles)) {
  const views = {};
  for (const name of VIEWS) {
    const view = style.views?.[name];
    if (!view) throw new Error(`Style ${id} has no ${name} view.`);
    const trimmed = {};
    for (const field of VIEW_FIELDS) {
      if (view[field] === undefined || view[field] === null) continue;
      trimmed[field] = field === "file" || field === "hair" || field === "visor" ? frameFile(view[field]) : view[field];
    }
    views[name] = trimmed;
  }
  styles[id] = { recolor: Boolean(style.recolor), views };
}
outputs.set("kit.json", `${JSON.stringify({ styles })}\n`);

outputs.set("placement.json", `${JSON.stringify({ accessories: placementSource.accessories })}\n`);

const castText = readFileSync(join(seira, "shared/cast-characters.ts"), "utf8");
const entry = /\{\s*id:\s*"([^"]+)",\s*name:\s*"([^"]+)",\s*mascotBody:\s*"([^"]+)",\s*mascotAccessories:\s*\[([^\]]*)\],\s*color:\s*"([^"]+)"\s*\}/g;
const cast = [{ id: "seira", name: "Seira", mascotBody: "seira", mascotAccessories: [], color: "chrome" }];
for (const m of castText.matchAll(entry)) {
  cast.push({
    id: m[1],
    name: m[2],
    mascotBody: m[3],
    mascotAccessories: [...m[4].matchAll(/"([^"]+)"/g)].map((a) => a[1]),
    color: m[5],
  });
}
if (cast.length !== CAST_COUNT + 1) {
  throw new Error(`Expected ${CAST_COUNT} cast characters in shared/cast-characters.ts, found ${cast.length - 1}.`);
}
for (const c of cast) {
  if (!styles[c.mascotBody]) throw new Error(`Cast character ${c.id} wears an unknown hair style: ${c.mascotBody}`);
  for (const a of c.mascotAccessories) {
    if (!placementSource.accessories[a]) throw new Error(`Cast character ${c.id} wears an unknown accessory: ${a}`);
  }
}
outputs.set("cast.json", `${JSON.stringify(cast)}\n`);

const existing = [];
const walk = (dir) => {
  if (!existsSync(dir)) return;
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) walk(path);
    else existing.push(relative(OUT, path).split("\\").join("/"));
  }
};
walk(OUT);

const same = (a, b) => (typeof a === "string" ? Buffer.from(a) : a).equals(typeof b === "string" ? Buffer.from(b) : b);
const drift = [];
for (const [path, data] of outputs) {
  const target = join(OUT, path);
  if (!existsSync(target) || !same(readFileSync(target), data)) drift.push(path);
}
for (const path of existing) if (!outputs.has(path)) drift.push(`${path} (stale)`);

if (check) {
  if (drift.length) {
    console.error(`Out of date, run node scripts/sync-seira-character-android.mjs:\n  ${drift.join("\n  ")}`);
    process.exit(1);
  }
  console.log("Seira character assets are up to date.");
  process.exit(0);
}

for (const [path, data] of outputs) {
  const target = join(OUT, path);
  mkdirSync(dirname(target), { recursive: true });
  writeFileSync(target, data);
}
for (const path of existing) if (!outputs.has(path)) rmSync(join(OUT, path));
console.log(`Wrote ${outputs.size} files, ${drift.length} changed.`);
