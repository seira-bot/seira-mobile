#!/usr/bin/env node
// Fails when a tracked file or path uses a legacy OpenMaus code name:
// the com.openmausbot package, Theme.OpenMaus styles, and OpenMaus* identifiers.
// The OpenMausBot product name and the wire ids stay allowed.
//
//   node scripts/check-legacy-names.mjs                      scan every tracked file
//   node scripts/check-legacy-names.mjs --staged             scan the added lines and paths in the index
//   node scripts/check-legacy-names.mjs --path android ...   limit the scan to these folders
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const SCOPE = ["android", "ios", "scripts", "docs", ".github"];
const SELF = new Set(["scripts/check-legacy-names.mjs", "scripts/check-legacy-names.test.mjs", "scripts/seira-package-move.mjs"]);
const RULES = [
  { label: "com.openmausbot package", re: /com\.openmausbot/ },
  { label: "com/openmausbot path", re: /com\/openmausbot/ },
  { label: "Theme.OpenMaus style", re: /Theme\.OpenMaus/ },
  { label: "OpenMaus* identifier", re: /\bOpenMaus(?!Bot)[A-Z][A-Za-z0-9_]*/ },
];
const ALLOWED = [
  { path: /(?:^|\/)AndroidManifest\.xml$/, text: /android:name="com\.openmausbot\.companion\.MainActivity"/ },
  { path: /^android\/app\/src\/test\/.*ManifestTest\.kt$/, text: /"com\.openmausbot\.companion\.MainActivity"/ },
  { path: /^android\/app\/build\.gradle\.kts$/, text: /^\s*const val LEGACY_LAUNCHER = "com\.openmausbot\.companion\.MainActivity"$/ },
];

export function checkLine(path, text) {
  if (ALLOWED.some((a) => a.path.test(path) && a.text.test(text))) return [];
  return RULES.filter((r) => r.re.test(text)).map((r) => r.label);
}

export function checkPath(path) {
  return RULES.filter((r) => r.re.test(path)).map((r) => r.label);
}

const inScope = (path, dirs) => dirs.some((d) => path === d || path.startsWith(`${d.replace(/\/$/, "")}/`)) && !SELF.has(path);
const git = (...args) => execFileSync("git", args, { cwd: root, encoding: "utf8", maxBuffer: 1 << 28 });

function stagedAdded() {
  const out = [];
  let path = null;
  let line = 0;
  for (const raw of git("diff", "--cached", "--unified=0", "--no-color", "--no-ext-diff").split("\n")) {
    if (raw.startsWith("+++ ")) path = raw === "+++ /dev/null" ? null : raw.slice(6);
    else if (raw.startsWith("@@")) line = Number(/\+(\d+)/.exec(raw)?.[1] ?? 0);
    else if (path && raw.startsWith("+")) out.push({ path, line: line++, text: raw.slice(1) });
  }
  return out;
}

function main(argv) {
  const dirs = [];
  let staged = false;
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === "--staged") staged = true;
    else if (argv[i] === "--path") dirs.push(argv[++i].replace(/\/$/, ""));
  }
  const scope = dirs.length ? dirs : SCOPE;
  const problems = [];

  if (staged) {
    const paths = git("diff", "--cached", "--name-only", "--diff-filter=ACMR", "-z").split("\0").filter(Boolean);
    for (const path of paths) if (inScope(path, scope)) for (const label of checkPath(path)) problems.push(`${path}: path has ${label}`);
    for (const { path, line, text } of stagedAdded()) {
      if (!inScope(path, scope)) continue;
      for (const label of checkLine(path, text)) problems.push(`${path}:${line}: ${label}`);
    }
  } else {
    for (const path of git("ls-files", "-z").split("\0").filter(Boolean)) {
      if (!inScope(path, scope)) continue;
      for (const label of checkPath(path)) problems.push(`${path}: path has ${label}`);
      let body;
      try {
        body = readFileSync(join(root, path));
      } catch {
        continue;
      }
      if (body.includes(0)) continue;
      body.toString("utf8").split("\n").forEach((text, index) => {
        for (const label of checkLine(path, text)) problems.push(`${path}:${index + 1}: ${label}`);
      });
    }
  }

  if (problems.length) {
    console.error(problems.join("\n"));
    console.error(`\ncheck-legacy-names: ${problems.length} legacy name(s). Use bot.seira.companion and Seira* names.`);
    return 1;
  }
  console.log("check-legacy-names: no legacy names");
  return 0;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) process.exit(main(process.argv.slice(2)));
