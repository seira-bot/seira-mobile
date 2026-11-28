#!/usr/bin/env node
// Moves the Android Kotlin packages from com.openmausbot.companion to bot.seira.companion.
// Safe to run again: a tree that has no old name stays unchanged.
//
//   node scripts/seira-package-move.mjs
import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, readdirSync, renameSync, rmdirSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const OLD_DIR = "com/openmausbot/companion";
const NEW_DIR = "bot/seira/companion";
const SELF = new Set(["scripts/seira-package-move.mjs", "scripts/check-legacy-names.mjs", "scripts/check-legacy-names.test.mjs"]);

const git = (...args) => execFileSync("git", args, { cwd: root, encoding: "utf8", maxBuffer: 1 << 28 });

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    if (name === "build" || name === ".gradle" || name === ".git" || name === "node_modules") continue;
    const path = join(dir, name);
    if (statSync(path).isDirectory()) walk(path, out);
    else out.push(path);
  }
  return out;
}

const rel = (path) => relative(root, path).split("\\").join("/");
const tracked = new Set(git("ls-files", "-z").split("\0").filter(Boolean));
const moved = [];

for (const file of walk(join(root, "android")).map(rel)) {
  const match = /^(android\/.*\/src\/[^/]+\/(?:kotlin|java)\/)com\/openmausbot\/companion\/(.*)$/.exec(file);
  if (!match) continue;
  let target = `${match[1]}${NEW_DIR}/${match[2]}`;
  if (target.endsWith("/OpenMausApp.kt")) target = target.replace(/OpenMausApp\.kt$/, "SeiraApp.kt");
  mkdirSync(dirname(join(root, target)), { recursive: true });
  if (tracked.has(file)) git("mv", file, target);
  else renameSync(join(root, file), join(root, target));
  moved.push([file, target]);
}

function pruneEmpty(dir) {
  if (!existsSync(dir)) return;
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) pruneEmpty(path);
  }
  if (readdirSync(dir).length === 0) rmdirSync(dir);
}
for (const base of ["main", "test", "debug", "release", "preview", "androidTest"]) {
  for (const module of ["app", "core"]) {
    for (const lang of ["kotlin", "java"]) {
      const old = join(root, "android", module, "src", base, lang, "com", "openmausbot");
      pruneEmpty(old);
      const com = join(root, "android", module, "src", base, lang, "com");
      if (existsSync(com) && readdirSync(com).length === 0) rmdirSync(com);
    }
  }
}

const TEXT_EXT = /\.(?:kt|kts|pro|txt|md)$/;
const edits = [];
const candidates = [
  ...walk(join(root, "android")).map(rel).filter((f) => TEXT_EXT.test(f) || /^android\/.*\/res\/xml\/[^/]+\.xml$/.test(f)),
  ...readdirSync(join(root, "scripts")).filter((f) => f.endsWith(".mjs")).map((f) => `scripts/${f}`),
  ...(existsSync(join(root, "docs/verification")) ? readdirSync(join(root, "docs/verification")).filter((f) => f.endsWith(".md")).map((f) => `docs/verification/${f}`) : []),
].filter((f) => !SELF.has(f));

// Launchers keep icons and pinned shortcuts by component name, so the launcher alias keeps the pre-1.2.2 name.
const LEGACY_LAUNCHER = "com.openmausbot.companion.MainActivity";
const KEPT = "\u0000legacy-launcher\u0000";

for (const file of candidates) {
  const before = readFileSync(join(root, file), "utf8");
  let after = before
    .replaceAll(LEGACY_LAUNCHER, KEPT)
    .replaceAll("com.openmausbot.companion", "bot.seira.companion")
    .replaceAll("com/openmausbot/companion", "bot/seira/companion")
    .replaceAll(KEPT, LEGACY_LAUNCHER);
  if (file.startsWith("android/")) after = after.replace(/\bOpenMausApp\b/g, "SeiraApp").replaceAll("Theme.OpenMausMobile", "Theme.SeiraMobile");
  if (after !== before) {
    writeFileSync(join(root, file), after);
    edits.push(file);
  }
}

const resources = walk(join(root, "android")).map(rel).filter((f) => /\/res\/[^/]+\/[^/]+\.xml$/.test(f) && !/\/res\/xml\//.test(f));
for (const file of resources) {
  const before = readFileSync(join(root, file), "utf8");
  const after = before.replaceAll("Theme.OpenMausMobile", "Theme.SeiraMobile");
  if (after !== before) {
    writeFileSync(join(root, file), after);
    edits.push(file);
  }
}

const manifests = walk(join(root, "android")).map(rel).filter((f) => f.endsWith("/AndroidManifest.xml"));
for (const file of manifests) {
  const before = readFileSync(join(root, file), "utf8");
  const after = before.replace(/\bOpenMausApp\b/g, "SeiraApp").replaceAll("Theme.OpenMausMobile", "Theme.SeiraMobile");
  if (after !== before) {
    writeFileSync(join(root, file), after);
    edits.push(file);
  }
}

console.log(`moved ${moved.length} files`);
for (const [from, to] of moved.slice(0, 5)) console.log(`  ${from} -> ${to}`);
if (moved.length > 5) console.log(`  ... ${moved.length - 5} more`);
console.log(`edited ${edits.length} files`);
for (const file of edits) console.log(`  ${file}`);
