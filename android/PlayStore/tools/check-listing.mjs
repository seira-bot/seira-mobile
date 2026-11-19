#!/usr/bin/env node
// Checks the Play listing texts in ../listing/*.md against the Console limits
// and against words the listing must not use. Exits 1 on any failure.
import { readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const listingDir = join(here, "..", "listing");

// Play Console limits, read from the Console store listing form.
const limits = {
  "App name": 30,
  "Short description": 80,
  "Full description": 4000,
  "Release notes": 500,
};

// The release does not ship these, and user copy must not describe internals.
const banned = [
  /\bcall(s|ing)?\b/i,
  /\btalk\b/i,
  /voice key/i,
  /panggilan/i,
  /\bcoming soon\b/i,
  /\byet\b/i,
  /segera hadir/i,
  /\bendpoint\b/i,
  /\bapi\b/i,
  /—/, // em dash
  /!/,
];

let failed = false;
for (const file of readdirSync(listingDir).filter((f) => f.endsWith(".md")).sort()) {
  const text = readFileSync(join(listingDir, file), "utf8");
  const fields = new Map();
  const re = /^## (.+)\n+```text\n([\s\S]*?)\n```/gm;
  for (const match of text.matchAll(re)) fields.set(match[1].trim(), match[2]);
  for (const [field, max] of Object.entries(limits)) {
    const value = fields.get(field);
    if (value === undefined) {
      console.log(`FAIL ${file}: missing "${field}"`);
      failed = true;
      continue;
    }
    const length = [...value].length;
    const ok = length <= max && length > 0;
    if (!ok) failed = true;
    console.log(`${ok ? "ok  " : "FAIL"} ${file}: ${field} ${length}/${max}`);
    for (const pattern of banned) {
      if (pattern.test(value)) {
        console.log(`FAIL ${file}: ${field} matches ${pattern}`);
        failed = true;
      }
    }
  }
}
process.exit(failed ? 1 : 0);
