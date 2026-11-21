#!/usr/bin/env node
// Translation tooling for the Android app. No dependencies, no network, no model.
//
//   export   --out <dir>                       write <dir>/en.json from res/values
//   glossary --seira <clone> --out <file>      derive the desktop term glossary
//   import   --lang <code> --in <file.json>    write res/values-<qualifier>/ from translations
//   check                                      report missing, extra, stale or malformed keys
//
// Text in en.json and in translation files is the logical text: no XML entities and no
// backslash escapes. `import` adds the escaping that aapt needs.

import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const RES = join(ROOT, "android/app/src/main/res");
const L10N = join(ROOT, "android/l10n");
const HASHES = join(L10N, "source-hashes.json");

export const TARGET_LANGUAGES = [
  "de", "es", "fr", "hi", "it", "ja", "ko", "pl", "pt-BR", "ru", "tr", "uk", "vi",
];

const DESKTOP_TAG = { "pt-BR": "pt-br" };

export const REQUIRED_QUANTITIES = {
  ru: ["one", "few", "many", "other"],
  uk: ["one", "few", "many", "other"],
  pl: ["one", "few", "many", "other"],
  de: ["one", "other"],
  es: ["one", "other"],
  fr: ["one", "other"],
  hi: ["one", "other"],
  it: ["one", "other"],
  pt: ["one", "other"],
  tr: ["one", "other"],
  ja: ["other"],
  ko: ["other"],
  vi: ["other"],
  zh: ["other"],
  en: ["one", "other"],
};

const GLOSSARY_TERMS = [
  ["Settings", "settings.title"],
  ["Connected apps", "sidebar.nav.connectedApps"],
  ["Bots", "sidebar.section.bots"],
  ["Routines", "sidebar.nav.routines"],
  ["Routine", "routines.kind.routine"],
  ["Threads", "settings.threadDisplay.title"],
  ["Thread", "task.chip"],
  ["Phone", "computer.tab.android"],
  ["Remote access", "remote.title"],
  ["Files", "files.title"],
  ["Notifications", "notifications.title"],
  ["Search", "sidebar.search"],
  ["Always allow", "approval.action.alwaysAllow"],
  ["Allow once", "approval.action.allowOnce"],
  ["Deny", "approval.action.deny"],
  ["Updates", "settings.updates.title"],
  ["Team", "teamSide.team"],
  ["Apps", "apps.title"],
  ["Voice", "voice.title"],
  ["Webhooks", "botPanel.page.webhooks"],
  ["Approvals", "botPanel.page.approvals"],
  ["Live call", "chat.callSheet.kicker"],
  ["Pair", "phone.pair.step"],
  ["Review", "routines.run.review"],
  ["Computer", "botPanel.tab.computer"],
  ["Library", "botPanel.tab.library"],
  ["Activity", "settings.section.activity"],
  ["Skills", "settings.section.skills"],
  ["Archive", "sidebar.bot.archive"],
  ["Tailscale", "servers.transport.tailscale"],
];

// ---------------------------------------------------------------- text

const ENTITIES = { amp: "&", lt: "<", gt: ">", quot: '"', apos: "'" };

function decodeEntities(text) {
  return text.replace(/&(#x[0-9a-fA-F]+|#\d+|[a-z]+);/g, (whole, name) => {
    if (name[0] === "#") {
      const code = name[1] === "x" ? parseInt(name.slice(2), 16) : parseInt(name.slice(1), 10);
      return String.fromCodePoint(code);
    }
    return ENTITIES[name] ?? whole;
  });
}

/** Resource text as aapt reads it: entities, optional surrounding quotes, backslash escapes. */
export function decodeAndroid(raw) {
  let text = decodeEntities(raw.trim());
  if (text.length >= 2 && text.startsWith('"') && text.endsWith('"')) text = text.slice(1, -1);
  return text.replace(/\\(u[0-9a-fA-F]{4}|.)/g, (_, code) => {
    if (code[0] === "u" && code.length === 5) return String.fromCharCode(parseInt(code.slice(1), 16));
    if (code === "n") return "\n";
    if (code === "t") return "\t";
    return code;
  });
}

/** Logical text as a resource file must hold it. Whitespace runs collapse, as aapt would do. */
export function encodeAndroid(logical) {
  const collapsed = logical.replace(/[ \t]*\n[ \t]*/g, "\n").replace(/[ \t]{2,}/g, " ").trim();
  let out = "";
  for (const char of collapsed) {
    if (char === "&") out += "&amp;";
    else if (char === "<") out += "&lt;";
    else if (char === ">") out += "&gt;";
    else if (char === "\\") out += "\\\\";
    else if (char === "'") out += "\\'";
    else if (char === '"') out += '\\"';
    else if (char === "\n") out += "\\n";
    else if (char === "\t") out += "\\t";
    else out += char;
  }
  return out.startsWith("@") || out.startsWith("?") ? "\\" + out : out;
}

const SPECIFIER = /%(?:\d+\$)?[-#+0,(]*\d*(?:\.\d+)?[a-zA-Z]/g;

export function formatArguments(text) {
  return [...text.replaceAll("%%", "").matchAll(SPECIFIER)].map((match) => match[0]).sort();
}

const sameList = (a, b) => a.length === b.length && a.every((value, index) => value === b[index]);

const hashOf = (value) =>
  createHash("sha256").update(typeof value === "string" ? value : JSON.stringify(value)).digest("hex").slice(0, 16);

// ---------------------------------------------------------------- resources

function attributesOf(source) {
  return Object.fromEntries([...source.matchAll(/([\w:]+)="([^"]*)"/g)].map((match) => [match[1], match[2]]));
}

/** Entries of one resource file in document order. */
export function parseResourceFile(source) {
  const cleaned = source.replace(/<!--[\s\S]*?-->/g, "");
  const entries = [];
  const pattern = /<string\b([^>]*)>([\s\S]*?)<\/string>|<plurals\b([^>]*)>([\s\S]*?)<\/plurals>/g;
  for (const match of cleaned.matchAll(pattern)) {
    if (match[1] !== undefined) {
      const attributes = attributesOf(match[1]);
      entries.push({
        name: attributes.name,
        kind: "string",
        translatable: attributes.translatable !== "false",
        text: decodeAndroid(match[2]),
      });
    } else {
      const attributes = attributesOf(match[3]);
      const quantities = {};
      for (const item of match[4].matchAll(/<item\b([^>]*)>([\s\S]*?)<\/item>/g)) {
        quantities[attributesOf(item[1]).quantity] = decodeAndroid(item[2]);
      }
      entries.push({
        name: attributes.name,
        kind: "plural",
        translatable: attributes.translatable !== "false",
        quantities,
      });
    }
  }
  return entries;
}

function resourceFiles(folder) {
  const directory = join(RES, folder);
  if (!existsSync(directory)) return [];
  return readdirSync(directory).filter((name) => /^strings.*\.xml$/.test(name)).sort();
}

function readFolder(folder) {
  const entries = [];
  for (const file of resourceFiles(folder)) {
    for (const entry of parseResourceFile(readFileSync(join(RES, folder, file), "utf8"))) {
      entries.push({ ...entry, file });
    }
  }
  return entries;
}

/** The English catalog: key to { file, kind, text, quantities?, args, hash }, in document order. */
export function readEnglish() {
  const catalog = new Map();
  for (const entry of readFolder("values")) {
    if (!entry.translatable) continue;
    if (catalog.has(entry.name)) throw new Error(`duplicate English key ${entry.name}`);
    const text = entry.kind === "plural" ? entry.quantities.other : entry.text;
    if (text === undefined) throw new Error(`English plural ${entry.name} has no "other" form`);
    catalog.set(entry.name, {
      file: entry.file,
      kind: entry.kind,
      text,
      ...(entry.kind === "plural" ? { quantities: entry.quantities } : {}),
      args: formatArguments(text),
      hash: hashOf(entry.kind === "plural" ? entry.quantities : entry.text),
    });
  }
  return catalog;
}

export function folderFor(code) {
  const normal = normalizeCode(code);
  if (normal === "zh-Hans") return "values-b+zh+Hans";
  if (normal === "zh-Hant") return "values-b+zh+Hant";
  const parts = normal.split("-");
  if (parts.length === 1) return `values-${normal}`;
  if (parts.length === 2 && parts[1].length === 2) return `values-${parts[0]}-r${parts[1]}`;
  return `values-b+${parts.join("+")}`;
}

export function codeFor(folder) {
  const qualifier = folder.replace(/^values-/, "");
  if (qualifier.startsWith("b+")) return qualifier.slice(2).split("+").join("-");
  const region = qualifier.match(/^([a-z]{2,3})-r([A-Z]{2})$/);
  return region ? `${region[1]}-${region[2]}` : qualifier;
}

function normalizeCode(code) {
  const parts = code.split(/[-_]/);
  return [
    parts[0].toLowerCase(),
    ...parts.slice(1).map((part) =>
      part.length === 2 ? part.toUpperCase() : part[0].toUpperCase() + part.slice(1).toLowerCase()),
  ].join("-");
}

function languageFolders() {
  return readdirSync(RES)
    .filter((name) => name.startsWith("values-") && resourceFiles(name).length > 0)
    .sort();
}

function readHashes() {
  return existsSync(HASHES) ? JSON.parse(readFileSync(HASHES, "utf8")) : {};
}

function sortedObject(object) {
  return Object.fromEntries(Object.entries(object).sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0)));
}

// ---------------------------------------------------------------- commands

function exportEnglish(out) {
  const catalog = readEnglish();
  const body = {};
  for (const [key, entry] of catalog) {
    body[key] = {
      text: entry.text,
      kind: entry.kind,
      ...(entry.quantities ? { quantities: entry.quantities } : {}),
      args: entry.args,
      hash: entry.hash,
    };
  }
  mkdirSync(out, { recursive: true });
  writeFileSync(join(out, "en.json"), JSON.stringify(body, null, 2) + "\n");
  console.log(`wrote ${Object.keys(body).length} keys to ${join(out, "en.json")}`);
}

function buildGlossary(seira, out) {
  const read = (tag) =>
    JSON.parse(execFileSync("git", ["-C", seira, "show", `origin/main:src/locales/${tag}.json`], {
      encoding: "utf8",
      maxBuffer: 64 * 1024 * 1024,
    }));
  const english = read("en");
  const androidCopy = [...readEnglish().values()].map((entry) =>
    entry.kind === "plural" ? Object.values(entry.quantities).join(" ") : entry.text).join("\n");
  const terms = [];
  const unmatched = [];
  for (const [term, key] of GLOSSARY_TERMS) {
    const uses = [...androidCopy.matchAll(new RegExp(`\\b${term}\\b`, "g"))].length;
    if (uses === 0) continue;
    if (english[key] === undefined) unmatched.push(term);
    else terms.push({ term, key, desktop: english[key], uses });
  }
  const languages = {};
  for (const language of TARGET_LANGUAGES) {
    const pack = read(DESKTOP_TAG[language] ?? language);
    const map = {};
    for (const { term, key } of terms) {
      if (typeof pack[key] === "string" && pack[key].trim()) map[term] = pack[key];
    }
    languages[language] = map;
  }
  mkdirSync(dirname(resolve(out)), { recursive: true });
  writeFileSync(
    out,
    JSON.stringify({ source: "desktop locale packs", terms, unmatched, languages }, null, 2) + "\n",
  );
  for (const language of TARGET_LANGUAGES) {
    console.log(`${language}: ${Object.keys(languages[language]).length} of ${terms.length} terms`);
  }
  if (unmatched.length) console.log(`no desktop key: ${unmatched.join(", ")}`);
}

function validateTranslation(language, key, source, value) {
  const problems = [];
  const required = REQUIRED_QUANTITIES[language.split("-")[0]];
  if (!required) return [`${key}: no plural rule for language ${language}`];
  if (source.kind === "string") {
    const text = typeof value === "string" ? value : value?.text;
    if (typeof text !== "string" || (!text.trim() && source.text.trim())) return [`${key}: text is empty`];
    if (!sameList(formatArguments(text), source.args)) {
      problems.push(`${key}: arguments ${formatArguments(text)} differ from English ${source.args}`);
    }
    return problems;
  }
  const quantities = value?.quantities;
  if (!quantities || typeof quantities !== "object") return [`${key}: plural needs a "quantities" object`];
  for (const quantity of required) {
    if (typeof quantities[quantity] !== "string" || !quantities[quantity].trim()) {
      problems.push(`${key}: quantity ${quantity} is missing`);
    }
  }
  for (const [quantity, text] of Object.entries(quantities)) {
    if (!["zero", "one", "two", "few", "many", "other"].includes(quantity)) {
      problems.push(`${key}: unknown quantity ${quantity}`);
    } else if (quantity === "other" && !sameList(formatArguments(text), source.args)) {
      problems.push(`${key}: other uses ${formatArguments(text)} but English uses ${source.args}`);
    } else if (!formatArguments(text).every((argument) => source.args.includes(argument))) {
      problems.push(`${key}: ${quantity} uses an argument that English does not`);
    }
  }
  return problems;
}

function renderEntry(name, source, value) {
  if (source.kind === "string") {
    const text = typeof value === "string" ? value : value.text;
    return `    <string name="${name}">${encodeAndroid(text)}</string>\n`;
  }
  const order = ["zero", "one", "two", "few", "many", "other"];
  const items = order.filter((quantity) => typeof value.quantities[quantity] === "string")
    .map((quantity) => `        <item quantity="${quantity}">${encodeAndroid(value.quantities[quantity])}</item>\n`)
    .join("");
  return `    <plurals name="${name}">\n${items}    </plurals>\n`;
}

function importTranslations(language, file) {
  const code = normalizeCode(language);
  const english = readEnglish();
  const incoming = JSON.parse(readFileSync(file, "utf8"));
  const problems = [];
  for (const [key, value] of Object.entries(incoming)) {
    const source = english.get(key);
    if (!source) problems.push(`${key}: not an English key`);
    else problems.push(...validateTranslation(code, key, source, value));
  }
  if (problems.length) {
    console.error(`${problems.length} problems, nothing written:\n${problems.join("\n")}`);
    process.exit(1);
  }

  const folder = folderFor(code);
  const merged = new Map();
  for (const entry of readFolder(folder)) {
    if (!english.has(entry.name)) continue;
    merged.set(entry.name, entry.kind === "plural" ? { quantities: entry.quantities } : { text: entry.text });
  }
  for (const [key, value] of Object.entries(incoming)) {
    merged.set(key, typeof value === "string" ? { text: value } : value);
  }

  const byFile = new Map();
  for (const [key, source] of english) {
    if (!merged.has(key)) continue;
    if (!byFile.has(source.file)) byFile.set(source.file, []);
    byFile.get(source.file).push(renderEntry(key, source, merged.get(key)));
  }
  const directory = join(RES, folder);
  mkdirSync(directory, { recursive: true });
  for (const [name, entries] of byFile) {
    writeFileSync(
      join(directory, name),
      `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n${entries.join("")}</resources>\n`,
    );
  }

  const hashes = readHashes();
  hashes[code] = sortedObject({
    ...(hashes[code] ?? {}),
    ...Object.fromEntries(Object.keys(incoming).map((key) => [key, english.get(key).hash])),
  });
  mkdirSync(L10N, { recursive: true });
  writeFileSync(HASHES, JSON.stringify(sortedObject(hashes), null, 2) + "\n");
  console.log(`${code}: wrote ${merged.size} keys to ${folder}, recorded ${Object.keys(incoming).length} hashes`);
}

function check() {
  const english = readEnglish();
  const hashes = readHashes();
  const problems = [];
  const folders = languageFolders();
  for (const folder of folders) {
    const code = codeFor(folder);
    const own = new Map(readFolder(folder).filter((entry) => entry.translatable).map((entry) => [entry.name, entry]));
    for (const key of english.keys()) {
      if (!own.has(key)) problems.push(`${code}: missing ${key}`);
    }
    for (const [key, entry] of own) {
      const source = english.get(key);
      if (!source) {
        problems.push(`${code}: extra ${key}`);
        continue;
      }
      if (entry.kind !== source.kind) {
        problems.push(`${code}: ${key} is ${entry.kind} but English is ${source.kind}`);
        continue;
      }
      const text = entry.kind === "plural" ? entry.quantities.other : entry.text;
      if (text === undefined || !sameList(formatArguments(text), source.args)) {
        problems.push(`${code}: ${key} has different format arguments than English`);
      }
      const recorded = hashes[code]?.[key];
      if (recorded !== undefined && recorded !== source.hash) problems.push(`${code}: stale ${key}`);
    }
  }
  for (const problem of problems) console.log(problem);
  console.log(`${folders.length} languages checked, ${problems.length} problems`);
  process.exit(problems.length ? 1 : 0);
}

// ---------------------------------------------------------------- cli

function option(args, name) {
  const index = args.indexOf(name);
  return index >= 0 ? args[index + 1] : undefined;
}

function main(argv) {
  const [command, ...args] = argv;
  const need = (name) => {
    const value = option(args, name);
    if (!value) {
      console.error(`${command} needs ${name}`);
      process.exit(2);
    }
    return value;
  };
  switch (command) {
    case "export":
      return exportEnglish(resolve(need("--out")));
    case "glossary":
      return buildGlossary(resolve(need("--seira")), resolve(need("--out")));
    case "import":
      return importTranslations(need("--lang"), resolve(need("--in")));
    case "check":
      return check();
    default:
      console.error("usage: android-locale.mjs export|glossary|import|check");
      process.exit(2);
  }
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) main(process.argv.slice(2));
