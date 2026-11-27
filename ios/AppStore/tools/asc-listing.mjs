#!/usr/bin/env node
// Checks and uploads the App Store listing text and screenshots in ios/AppStore.
//
//   node ios/AppStore/tools/asc-listing.mjs check
//   node ios/AppStore/tools/asc-listing.mjs status
//   node ios/AppStore/tools/asc-listing.mjs text
//   node ios/AppStore/tools/asc-listing.mjs screenshots [--replace]
//
// "check" needs nothing. The other commands call the App Store Connect API and read:
//   ASC_KEY_ID, ASC_ISSUER_ID   the API key (App Manager or Admin role)
//   ASC_KEY_PATH                path to the AuthKey_<id>.p8 file
//   ASC_APP_ID                  the Apple ID of the app
// The commands change only the version that is in PREPARE_FOR_SUBMISSION (or another
// editable state). They never submit for review and never release.
import crypto from "node:crypto";
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { basename, dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const LOCALES = readdirSync(root).filter((d) => /^[a-z]{2}(-[A-Z]{2})?$/.test(d) && statSync(join(root, d)).isDirectory());

// App Store Connect limits, read on 2026-11-27. Keywords count bytes.
const LIMITS = { name: 30, subtitle: 30, promotional_text: 170, description: 4000, keywords: 100, release_notes: 4000 };
const URL_FIELDS = ["marketing_url", "support_url", "privacy_url"];

// Folder name under frames/<locale>/ -> App Store Connect screenshot display type.
const DISPLAY_TYPES = { "iphone-6.9": "APP_IPHONE_67", "ipad-13": "APP_IPAD_PRO_3GEN_129" };
const SIZES = { "iphone-6.9": [[1320, 2868], [1290, 2796]], "ipad-13": [[2064, 2752], [2048, 2732]] };
const EDITABLE = new Set(["PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED", "INVALID_BINARY"]);

const read = (locale, field) => {
  const file = join(root, locale, `${field}.txt`);
  return existsSync(file) ? readFileSync(file, "utf8").replace(/\s+$/, "") : null;
};

function check() {
  const problems = [];
  for (const locale of LOCALES) {
    for (const [field, max] of Object.entries(LIMITS)) {
      const text = read(locale, field);
      if (text == null) { problems.push(`${locale}/${field}.txt is missing`); continue; }
      const size = field === "keywords" ? Buffer.byteLength(text) : [...text].length;
      if (size > max) problems.push(`${locale}/${field}.txt has ${size}, limit ${max}`);
      if (field === "keywords" && /,\s|\s,/.test(text)) problems.push(`${locale}/keywords.txt has a space next to a comma`);
    }
    for (const field of URL_FIELDS) {
      const text = read(locale, field);
      if (!text || !/^https:\/\/\S+$/.test(text)) problems.push(`${locale}/${field}.txt must hold one https URL`);
    }
    const frames = join(root, "frames", locale);
    for (const folder of Object.keys(DISPLAY_TYPES)) {
      const dir = join(frames, folder);
      const files = existsSync(dir) ? readdirSync(dir).filter((f) => f.endsWith(".png")) : [];
      if (files.length < 3 || files.length > 10) problems.push(`frames/${locale}/${folder} has ${files.length} PNG files, App Store needs 3 to 10`);
      for (const f of files) {
        const [w, h, alpha] = pngInfo(join(dir, f));
        if (!SIZES[folder].some(([sw, sh]) => sw === w && sh === h)) problems.push(`frames/${locale}/${folder}/${f} is ${w}x${h}`);
        if (alpha) problems.push(`frames/${locale}/${folder}/${f} has an alpha channel`);
      }
    }
  }
  for (const p of problems) console.log(`FAIL ${p}`);
  console.log(problems.length ? `${problems.length} problem(s)` : `ok: ${LOCALES.join(", ")}`);
  return problems.length === 0;
}

function pngInfo(file) {
  const b = readFileSync(file);
  const colorType = b[25];
  return [b.readUInt32BE(16), b.readUInt32BE(20), colorType === 4 || colorType === 6];
}

function env(name) {
  const v = process.env[name];
  if (!v) throw new Error(`Set ${name}`);
  return v;
}

function token() {
  const b64u = (x) => Buffer.from(x).toString("base64url");
  const now = Math.floor(Date.now() / 1000);
  const head = b64u(JSON.stringify({ alg: "ES256", kid: env("ASC_KEY_ID"), typ: "JWT" }));
  const body = b64u(JSON.stringify({ iss: env("ASC_ISSUER_ID"), iat: now, exp: now + 1100, aud: "appstoreconnect-v1" }));
  const key = crypto.createPrivateKey(readFileSync(env("ASC_KEY_PATH")));
  const sig = crypto.sign("sha256", Buffer.from(`${head}.${body}`), { key, dsaEncoding: "ieee-p1363" });
  return `${head}.${body}.${b64u(sig)}`;
}

async function api(method, path, body) {
  const res = await fetch(path.startsWith("http") ? path : `https://api.appstoreconnect.apple.com${path}`, {
    method,
    headers: { Authorization: `Bearer ${token()}`, "Content-Type": "application/json" },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  const json = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const detail = (json?.errors ?? []).map((e) => `${e.code}: ${e.detail ?? e.title}`).join("; ");
    const err = new Error(`${method} ${path.split("?")[0]} -> ${res.status} ${detail}`);
    err.status = res.status;
    throw err;
  }
  return json;
}

async function editableVersion(appId) {
  const res = await api("GET", `/v1/apps/${appId}/appStoreVersions?filter[platform]=IOS&limit=10`);
  const v = res.data.find((x) => EDITABLE.has(x.attributes.appStoreState));
  if (!v) throw new Error("No editable iOS version. Create the version in App Store Connect first.");
  return v;
}

async function editableAppInfo(appId) {
  const res = await api("GET", `/v1/apps/${appId}/appInfos`);
  const info = res.data.find((x) => EDITABLE.has(x.attributes.appStoreState ?? x.attributes.state)) ?? res.data[0];
  return info;
}

async function upsert(listPath, type, parentRel, parentId, locale, attributes) {
  const list = await api("GET", `${listPath}?limit=50`);
  const found = list.data.find((x) => x.attributes.locale === locale);
  if (found) {
    await api("PATCH", `/v1/${type}/${found.id}`, { data: { type, id: found.id, attributes } });
    return found.id;
  }
  const made = await api("POST", `/v1/${type}`, {
    data: { type, attributes: { locale, ...attributes }, relationships: { [parentRel]: { data: { type: parentRel + "s", id: parentId } } } },
  });
  return made.data.id;
}

async function pushText() {
  const appId = env("ASC_APP_ID");
  const version = await editableVersion(appId);
  const info = await editableAppInfo(appId);
  for (const locale of LOCALES) {
    await upsert(`/v1/appInfos/${info.id}/appInfoLocalizations`, "appInfoLocalizations", "appInfo", info.id, locale, {
      name: read(locale, "name"),
      subtitle: read(locale, "subtitle"),
      privacyPolicyUrl: read(locale, "privacy_url"),
    });
    const attrs = {
      description: read(locale, "description"),
      keywords: read(locale, "keywords"),
      promotionalText: read(locale, "promotional_text"),
      marketingUrl: read(locale, "marketing_url"),
      supportUrl: read(locale, "support_url"),
      whatsNew: read(locale, "release_notes"),
    };
    const listPath = `/v1/appStoreVersions/${version.id}/appStoreVersionLocalizations`;
    try {
      await upsert(listPath, "appStoreVersionLocalizations", "appStoreVersion", version.id, locale, attrs);
    } catch (e) {
      // App Store Connect refuses "What's New" on the first version of an app.
      if (e.status !== 409 || !/whatsNew/i.test(e.message)) throw e;
      delete attrs.whatsNew;
      await upsert(listPath, "appStoreVersionLocalizations", "appStoreVersion", version.id, locale, attrs);
      console.log(`${locale}: What's New skipped (first version)`);
    }
    console.log(`${locale}: text ok (version ${version.attributes.versionString})`);
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function uploadScreenshot(setId, file) {
  const bytes = readFileSync(file);
  const made = await api("POST", "/v1/appScreenshots", {
    data: { type: "appScreenshots", attributes: { fileName: basename(file), fileSize: bytes.length }, relationships: { appScreenshotSet: { data: { type: "appScreenshotSets", id: setId } } } },
  });
  const shot = made.data;
  for (const op of shot.attributes.uploadOperations) {
    const headers = Object.fromEntries(op.requestHeaders.map((h) => [h.name, h.value]));
    const res = await fetch(op.url, { method: op.method, headers, body: bytes.subarray(op.offset, op.offset + op.length) });
    if (!res.ok) throw new Error(`upload part of ${basename(file)} -> ${res.status}`);
  }
  const md5 = crypto.createHash("md5").update(bytes).digest("hex");
  await api("PATCH", `/v1/appScreenshots/${shot.id}`, { data: { type: "appScreenshots", id: shot.id, attributes: { uploaded: true, sourceFileChecksum: md5 } } });
  return shot.id;
}

async function pushScreenshots(replace) {
  const appId = env("ASC_APP_ID");
  const version = await editableVersion(appId);
  const locs = await api("GET", `/v1/appStoreVersions/${version.id}/appStoreVersionLocalizations?limit=50`);
  for (const locale of LOCALES) {
    const loc = locs.data.find((x) => x.attributes.locale === locale);
    if (!loc) throw new Error(`${locale}: run "text" first`);
    const sets = await api("GET", `/v1/appStoreVersionLocalizations/${loc.id}/appScreenshotSets?limit=50`);
    for (const [folder, displayType] of Object.entries(DISPLAY_TYPES)) {
      const dir = join(root, "frames", locale, folder);
      if (!existsSync(dir)) continue;
      const files = readdirSync(dir).filter((f) => f.endsWith(".png")).sort();
      let set = sets.data.find((s) => s.attributes.screenshotDisplayType === displayType);
      if (!set) {
        set = (await api("POST", "/v1/appScreenshotSets", {
          data: { type: "appScreenshotSets", attributes: { screenshotDisplayType: displayType }, relationships: { appStoreVersionLocalization: { data: { type: "appStoreVersionLocalizations", id: loc.id } } } },
        })).data;
      }
      const existing = await api("GET", `/v1/appScreenshotSets/${set.id}/appScreenshots?limit=50`);
      if (existing.data.length && !replace) {
        console.log(`${locale} ${folder}: ${existing.data.length} screenshot(s) already there, use --replace`);
        continue;
      }
      for (const old of existing.data) await api("DELETE", `/v1/appScreenshots/${old.id}`);
      const ids = [];
      for (const f of files) ids.push(await uploadScreenshot(set.id, join(dir, f)));
      await api("PATCH", `/v1/appScreenshotSets/${set.id}/relationships/appScreenshots`, { data: ids.map((id) => ({ type: "appScreenshots", id })) });
      for (let i = 0; i < 40; i++) {
        const now = await api("GET", `/v1/appScreenshotSets/${set.id}/appScreenshots?limit=50`);
        const states = now.data.map((s) => s.attributes.assetDeliveryState?.state);
        if (states.every((s) => s === "COMPLETE")) break;
        if (states.includes("FAILED")) throw new Error(`${locale} ${folder}: an upload FAILED`);
        await sleep(3000);
      }
      console.log(`${locale} ${folder}: ${ids.length} screenshot(s) uploaded`);
    }
  }
}

async function status() {
  const appId = env("ASC_APP_ID");
  const version = await editableVersion(appId);
  const info = await editableAppInfo(appId);
  console.log(`version ${version.attributes.versionString} ${version.attributes.appStoreState}`);
  for (const x of (await api("GET", `/v1/appInfos/${info.id}/appInfoLocalizations`)).data) {
    const a = x.attributes;
    console.log(`${a.locale} info: name=${a.name ?? "-"} subtitle=${a.subtitle ?? "-"} privacy=${a.privacyPolicyUrl ?? "-"}`);
  }
  const locs = await api("GET", `/v1/appStoreVersions/${version.id}/appStoreVersionLocalizations?limit=50`);
  for (const x of locs.data) {
    const a = x.attributes;
    const filled = ["description", "keywords", "promotionalText", "marketingUrl", "supportUrl", "whatsNew"].map((k) => `${k}=${a[k] ? "yes" : "no"}`).join(" ");
    console.log(`${a.locale} version: ${filled}`);
    for (const s of (await api("GET", `/v1/appStoreVersionLocalizations/${x.id}/appScreenshotSets?limit=50`)).data) {
      const shots = (await api("GET", `/v1/appScreenshotSets/${s.id}/appScreenshots?limit=50`)).data;
      const states = shots.map((p) => p.attributes.assetDeliveryState?.state ?? "?");
      console.log(`${a.locale} ${s.attributes.screenshotDisplayType}: ${shots.length} [${states.join(", ")}]`);
    }
  }
}

const [cmd, flag] = process.argv.slice(2);
const run = { check: async () => process.exit(check() ? 0 : 1), status, text: pushText, screenshots: () => pushScreenshots(flag === "--replace") }[cmd];
if (!run) {
  console.log("usage: asc-listing.mjs check | status | text | screenshots [--replace]");
  process.exit(2);
}
run().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
