#!/usr/bin/env node
// Copies the Seira character kit from a checkout of the desktop project into ios/SeiraKit/.
//
//   SEIRA_REPO=<path to a seira checkout> node scripts/sync-seira-character.mjs
//
// The output is deterministic. The script removes only files inside ios/SeiraKit/ that an earlier run wrote.
import { copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const source = process.env.SEIRA_REPO;
if (!source || !existsSync(join(source, 'src/assets/seira-character/kit.json'))) {
  console.error('Set SEIRA_REPO to a checkout of the Seira desktop project.');
  process.exit(1);
}
const out = join(root, 'ios/SeiraKit');
const OWNED = ['kit.json', 'placement.json', 'cast.json', 'frames', 'accessories', 'carved'];
for (const name of OWNED) rmSync(join(out, name), { recursive: true, force: true });
mkdirSync(out, { recursive: true });

const readJson = (path) => JSON.parse(readFileSync(path, 'utf8'));
const writeJson = (path, value) => {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`);
};
const copy = (from, to) => {
  mkdirSync(dirname(to), { recursive: true });
  copyFileSync(from, to);
};
const sorted = (object) => Object.fromEntries(Object.entries(object).sort(([a], [b]) => (a < b ? -1 : 1)));

// Frame kit: the front and yaw20 views of every hair style.
const kitDir = join(source, 'src/assets/seira-character');
const kit = readJson(join(kitDir, 'kit.json'));
const VIEWS = ['front', 'yaw20'];
const styles = {};
let frames = 0;
for (const [id, style] of Object.entries(kit.styles)) {
  const views = {};
  for (const name of VIEWS) {
    const view = style.views[name];
    if (!view) throw new Error(`Style ${id} has no ${name} view.`);
    const files = { file: view.file, hair: view.hair, visor: view.visor };
    const next = { ...view };
    for (const [key, file] of Object.entries(files)) {
      if (!file) { delete next[key]; continue; }
      const target = `frames/${id}/${file.split('/').pop()}`;
      copy(join(kitDir, file), join(out, target));
      next[key] = target;
      frames += 1;
    }
    views[name] = sorted(next);
  }
  styles[id] = { label: style.label, recolor: style.recolor, views };
}
writeJson(join(out, 'kit.json'), { styles });

// Accessory sprites: front files only.
const accDir = join(source, 'src/assets/seira-accessory');
const placement = readJson(join(accDir, 'placement.json'));
const accessories = {};
for (const [id, def] of Object.entries(placement.accessories)) {
  const next = { ...def };
  delete next.files;
  if (next.pivots) { next.pivots = { ...next.pivots }; delete next.pivots.yaw45; if (!Object.keys(next.pivots).length) delete next.pivots; }
  if (next.widths) { next.widths = { ...next.widths }; delete next.widths.yaw45; if (!Object.keys(next.widths).length) delete next.widths; }
  next.views = (def.views ?? []).filter((view) => view !== 'yaw45');
  copy(join(accDir, def.file), join(out, 'accessories', def.file));
  accessories[id] = sorted(next);
}
writeJson(join(out, 'placement.json'), { styles: placement.styles, accessories });

// Carved avatar heads: one picture per size for every hair style, plus the face geometry.
const { CARVED_BODIES, BRAND_BODY_IDS } = await import(pathToFileURL(join(source, 'shared/carved-bodies.ts')).href);
const bodies = {};
let carved = 0;
for (const id of BRAND_BODY_IDS) {
  const dir = id === 'seira' ? join(source, 'src/assets/seira-body') : join(source, 'src/assets/seira-hair', id);
  const sizes = { front: [], hair: [] };
  let hairExt = null;
  for (const file of readdirSync(dir).sort()) {
    const match = /^(front|hair)-(\d+)\.(webp|png)$/.exec(file);
    if (!match) continue;
    sizes[match[1]].push(Number(match[2]));
    if (match[1] === 'hair') hairExt = match[3];
    copy(join(dir, file), join(out, 'carved', id, file));
    carved += 1;
  }
  sizes.front.sort((a, b) => a - b);
  sizes.hair.sort((a, b) => a - b);
  if (!sizes.front.length) throw new Error(`Carved style ${id} has no head picture.`);
  const body = CARVED_BODIES[id];
  bodies[id] = {
    name: body.name,
    paint: body.paint,
    bone: body.bone,
    eyes: sorted(body.eyes),
    clip: body.clip,
    hairExt,
    sizes,
  };
}
writeJson(join(out, 'carved/bodies.json'), bodies);

// Cast: Seira first, then every desktop cast character.
const mascotSource = readFileSync(join(source, 'src/lib/mascot.ts'), 'utf8');
const table = /MAUS_COLORS\s*=\s*\{([^}]*)\}/.exec(mascotSource)?.[1] ?? '';
const colors = Object.fromEntries([...table.matchAll(/(\w+):\s*"(#[0-9A-Fa-f]{6})"/g)].map((m) => [m[1], m[2]]));
if (!Object.keys(colors).length) throw new Error('Could not read the colour table.');
const channels = (hex) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
const distance = (a, b) => channels(a).reduce((sum, v, i) => sum + (v - channels(b)[i]) ** 2, 0);
const seiraColor = Object.keys(colors).sort().reduce((best, name) => (distance(colors[name], '#FF2A57') < distance(colors[best], '#FF2A57') ? name : best));
const { CAST_CHARACTERS } = await import(pathToFileURL(join(source, 'shared/cast-characters.ts')).href);
const cast = [
  { id: 'seira', name: 'Seira', mascotBody: 'seira', mascotAccessories: [], color: seiraColor },
  ...CAST_CHARACTERS.map((c) => ({ id: c.id, name: c.name, mascotBody: c.mascotBody, mascotAccessories: [...c.mascotAccessories], color: c.color })),
];
const deskCast = readJson(join(source, 'brand/mascot/cast/cast.json'));
if (cast.length !== deskCast.length) throw new Error(`Cast has ${cast.length} entries, the desktop file has ${deskCast.length}.`);
writeJson(join(out, 'cast.json'), cast);

console.log(`Wrote ${relative(root, out)}: ${Object.keys(styles).length} styles, ${frames} frame files, ${carved} carved pictures, ${Object.keys(accessories).length} accessories, ${cast.length} cast.`);
