#!/usr/bin/env node
// Checks and uploads the App Store listing text, screenshots and creative assets in ios/AppStore.
//
//   node ios/AppStore/tools/asc-listing.mjs check
//   node ios/AppStore/tools/asc-listing.mjs status [--platform IOS|MAC_OS]
//   node ios/AppStore/tools/asc-listing.mjs text [--platform IOS|MAC_OS]
//   node ios/AppStore/tools/asc-listing.mjs screenshots [--replace] [--platform IOS|MAC_OS]
//   node ios/AppStore/tools/asc-listing.mjs assets
//   node ios/AppStore/tools/asc-listing.mjs place [--replace]
//
// "assets" uploads the header, the search-results image and the iPhone Duo screenshots
// to the App Asset Library. It does not touch any version.
// "place" attaches those images to the editable version. It refuses when no version is editable.
// --platform picks the version to change. The default is IOS. MAC_OS uses frames/<locale>/mac/
// and the screenshot type APP_DESKTOP. "assets" and "place" work for IOS only.
// "check" needs nothing. The other commands call the App Store Connect API and read:
//   ASC_KEY_ID, ASC_ISSUER_ID   the API key (App Manager or Admin role)
//   ASC_KEY_PATH                path to the AuthKey_<id>.p8 file
//   ASC_APP_ID                  the Apple ID of the app
// The commands change only the version that is in PREPARE_FOR_SUBMISSION (or another
// editable state). No command submits for review, withdraws a submission or releases.
import crypto from "node:crypto";
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { basename, dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { api, env, listAll } from "./asc-api.mjs";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const LOCALES = readdirSync(root).filter((d) => /^[a-z]{2}(-[A-Z]{2})?$/.test(d) && statSync(join(root, d)).isDirectory());

// App Store Connect limits, read on 2026-11-27. Keywords count bytes.
const LIMITS = { name: 30, subtitle: 30, promotional_text: 170, description: 4000, keywords: 100, release_notes: 4000 };
const URL_FIELDS = ["marketing_url", "support_url", "privacy_url"];

// Folder name under frames/<locale>/ -> App Store Connect screenshot display type.
const DISPLAY_TYPES_BY_PLATFORM = {
  IOS: { "iphone-6.9": "APP_IPHONE_67", "ipad-13": "APP_IPAD_PRO_3GEN_129" },
  MAC_OS: { mac: "APP_DESKTOP" },
};
const ALL_FOLDERS = Object.assign({}, ...Object.values(DISPLAY_TYPES_BY_PLATFORM));
const SIZES = {
  "iphone-6.9": [[1320, 2868], [1290, 2796]],
  "ipad-13": [[2064, 2752], [2048, 2732]],
  mac: [[2880, 1800], [2560, 1600], [1440, 900], [1280, 800]],
};
const DUO_SIZES = [[2007, 2853], [1398, 2034], [2853, 2007], [2034, 1398]];
// Path under frames/<locale>/ -> App Asset Library settings (rules read on 2026-11-27).
const LIBRARY = {
  "iphone-duo": {
    folder: true,
    category: "APP_SCREENSHOTS_AND_PREVIEWS",
    placementType: "APP_SCREENSHOT",
    placementGroup: "IPHONE_DUO_PROFILE",
    sizeText: "2007x2853, 1398x2034, 2853x2007 or 2034x1398",
    fits: (w, h) => DUO_SIZES.some(([sw, sh]) => sw === w && sh === h),
  },
  "creative/header.png": {
    category: "CREATIVE_ASSETS",
    placementType: "PRODUCT_PAGE_HEADER_ASSET",
    placementGroup: "DEFAULT_PROFILE",
    sizeText: "3840x1646",
    fits: (w, h) => w === 3840 && h === 1646,
  },
  "creative/search-results.png": {
    category: "CREATIVE_ASSETS",
    placementType: "APP_STORE_SEARCH_RESULTS_ASSET",
    placementGroup: "DEFAULT_PROFILE",
    sizeText: "3:2, 1920 to 3840 wide",
    fits: (w, h) => w * 2 === h * 3 && w >= 1920 && w <= 3840,
  },
};
const READY = new Set(["PREPARE_FOR_SUBMISSION", "READY_FOR_REVIEW", "WAITING_FOR_REVIEW", "IN_REVIEW", "ACCEPTED", "APPROVED"]);
const EDITABLE = new Set(["PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED", "INVALID_BINARY"]);

const read = (locale, field) => {
  const file = join(root, locale, `${field}.txt`);
  return existsSync(file) ? readFileSync(file, "utf8").replace(/\s+$/, "") : null;
};

// Library files of one locale in a stable order: { rel, file, spec }.
function libraryFiles(locale) {
  const frames = join(root, "frames", locale);
  const out = [];
  for (const [path, spec] of Object.entries(LIBRARY)) {
    const full = join(frames, path);
    if (spec.folder) {
      if (!existsSync(full)) continue;
      for (const f of readdirSync(full).filter((x) => x.endsWith(".png")).sort()) out.push({ rel: `${path}/${f.slice(0, -4)}`, file: join(full, f), spec });
    } else if (existsSync(full)) {
      out.push({ rel: path.slice(0, -4), file: full, spec });
    }
  }
  return out;
}

const referenceName = (locale, rel, bytes) => `${locale} ${rel} ${crypto.createHash("sha256").update(bytes).digest("hex").slice(0, 12)}`;

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
    for (const folder of Object.keys(ALL_FOLDERS)) {
      const dir = join(frames, folder);
      const files = existsSync(dir) ? readdirSync(dir).filter((f) => f.endsWith(".png")) : [];
      if (files.length < 3 || files.length > 10) problems.push(`frames/${locale}/${folder} has ${files.length} PNG files, App Store needs 3 to 10`);
      for (const f of files) {
        const [w, h, alpha] = pngInfo(join(dir, f));
        if (!SIZES[folder].some(([sw, sh]) => sw === w && sh === h)) problems.push(`frames/${locale}/${folder}/${f} is ${w}x${h}`);
        if (alpha) problems.push(`frames/${locale}/${folder}/${f} has an alpha channel`);
      }
    }
    const duo = join(frames, "iphone-duo");
    if (existsSync(duo)) {
      const n = readdirSync(duo).filter((f) => f.endsWith(".png")).length;
      if (n < 1 || n > 10) problems.push(`frames/${locale}/iphone-duo has ${n} PNG files, App Store needs 1 to 10`);
    }
    const creative = join(frames, "creative");
    if (existsSync(creative)) {
      for (const f of readdirSync(creative).filter((x) => x.endsWith(".png"))) {
        if (!LIBRARY[`creative/${f}`]) problems.push(`frames/${locale}/creative/${f} is not a known name`);
      }
    }
    for (const { rel, file, spec } of libraryFiles(locale)) {
      const [w, h, alpha] = pngInfo(file);
      if (!spec.fits(w, h)) problems.push(`frames/${locale}/${rel}.png is ${w}x${h}, needs ${spec.sizeText}`);
      if (alpha) problems.push(`frames/${locale}/${rel}.png has an alpha channel`);
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

async function platformVersions(appId, platform) {
  return (await api("GET", `/v1/apps/${appId}/appStoreVersions?filter[platform]=${platform}&limit=50`)).data;
}

async function editableVersion(appId, platform) {
  const v = (await platformVersions(appId, platform)).find((x) => EDITABLE.has(x.attributes.appStoreState));
  if (!v) throw new Error(`No editable ${platform} version. Create the version in App Store Connect first.`);
  return v;
}

async function editableAppInfo(appId, platform) {
  const res = await api("GET", `/v1/apps/${appId}/appInfos`);
  const editable = res.data.find((x) => EDITABLE.has(x.attributes.appStoreState ?? x.attributes.state));
  if (platform === "IOS") return editable ?? res.data[0];
  return editable ?? null;
}

// App Store Connect refuses "What's New" on the first version of a platform.
async function isFirstVersion(appId, platform, version) {
  return (await platformVersions(appId, platform)).every((x) => x.id === version.id);
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

async function pushText(platform) {
  const appId = env("ASC_APP_ID");
  const version = await editableVersion(appId, platform);
  const info = await editableAppInfo(appId, platform);
  const first = await isFirstVersion(appId, platform, version);
  if (!info) console.log("No editable app info. Name, subtitle and privacy URL are left as they are.");
  for (const locale of LOCALES) {
    if (info) {
      await upsert(`/v1/appInfos/${info.id}/appInfoLocalizations`, "appInfoLocalizations", "appInfo", info.id, locale, {
        name: read(locale, "name"),
        subtitle: read(locale, "subtitle"),
        privacyPolicyUrl: read(locale, "privacy_url"),
      });
    }
    const attrs = {
      description: read(locale, "description"),
      keywords: read(locale, "keywords"),
      promotionalText: read(locale, "promotional_text"),
      marketingUrl: read(locale, "marketing_url"),
      supportUrl: read(locale, "support_url"),
      whatsNew: first ? undefined : read(locale, "release_notes"),
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
    console.log(`${locale}: text ok (${platform} version ${version.attributes.versionString}${first ? ", What's New skipped: first version" : ""})`);
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

async function pushScreenshots(replace, platform) {
  const appId = env("ASC_APP_ID");
  const version = await editableVersion(appId, platform);
  const locs = await api("GET", `/v1/appStoreVersions/${version.id}/appStoreVersionLocalizations?limit=50`);
  for (const locale of LOCALES) {
    const loc = locs.data.find((x) => x.attributes.locale === locale);
    if (!loc) throw new Error(`${locale}: run "text" first`);
    const sets = await api("GET", `/v1/appStoreVersionLocalizations/${loc.id}/appScreenshotSets?limit=50`);
    for (const [folder, displayType] of Object.entries(DISPLAY_TYPES_BY_PLATFORM[platform])) {
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

async function libraryId(appId) {
  return (await api("GET", `/v1/apps/${appId}/assetLibrary`)).data.id;
}

async function libraryImages(libId) {
  const map = new Map();
  for (const img of await listAll(`/v1/appAssetLibraries/${libId}/images?limit=200`)) map.set(img.attributes.referenceName, img);
  return map;
}

async function uploadLibraryImage(libId, locale, rel, file, spec, name) {
  const bytes = readFileSync(file);
  const made = await api("POST", "/v1/appAssetLibraryImages", {
    data: {
      type: "appAssetLibraryImages",
      attributes: { fileName: basename(file), fileSize: bytes.length, category: spec.category, referenceName: name },
      relationships: { assetLibrary: { data: { type: "appAssetLibraries", id: libId } } },
    },
  });
  const id = made.data.id;
  for (const op of made.data.attributes.uploadOperations) {
    const headers = Object.fromEntries(op.requestHeaders.map((h) => [h.name, h.value]));
    const res = await fetch(op.url, { method: op.method, headers, body: bytes.subarray(op.offset, op.offset + op.length) });
    if (!res.ok) throw new Error(`${locale} ${rel}: upload part -> ${res.status}`);
  }
  await api("PATCH", `/v1/appAssetLibraryImages/${id}`, { data: { type: "appAssetLibraryImages", id, attributes: { uploaded: true } } });
  for (let i = 0; i < 60; i++) {
    const now = (await api("GET", `/v1/appAssetLibraryImages/${id}`)).data.attributes;
    if (READY.has(now.state)) return { id, state: now.state };
    if (now.state === "FAILED" || now.state === "REJECTED") throw new Error(`${locale} ${rel}: ${now.state} ${now.stateDetails ? JSON.stringify(now.stateDetails) : ""}`.trim());
    await sleep(3000);
  }
  throw new Error(`${locale} ${rel}: still not ready after 3 minutes`);
}

async function pushAssets() {
  const libId = await libraryId(env("ASC_APP_ID"));
  const images = await libraryImages(libId);
  for (const locale of LOCALES) {
    for (const { rel, file, spec } of libraryFiles(locale)) {
      const name = referenceName(locale, rel, readFileSync(file));
      const old = images.get(name);
      if (old && old.attributes.state !== "AWAITING_UPLOAD" && old.attributes.state !== "FAILED") {
        console.log(`${locale} ${rel}: reused ${old.id} ${old.attributes.state}`);
        continue;
      }
      if (old) await api("DELETE", `/v1/appAssetLibraryImages/${old.id}`);
      const done = await uploadLibraryImage(libId, locale, rel, file, spec, name);
      console.log(`${locale} ${rel}: uploaded ${done.id} ${done.state}`);
    }
  }
}

async function placeAssets(replace) {
  const appId = env("ASC_APP_ID");
  const versions = (await api("GET", `/v1/apps/${appId}/appStoreVersions?filter[platform]=IOS&limit=10`)).data;
  const version = versions.find((x) => EDITABLE.has(x.attributes.appStoreState));
  if (!version) {
    const list = versions.map((x) => `${x.attributes.versionString} is ${x.attributes.appStoreState}`).join(", ") || "none found";
    console.log(`No editable iOS version: ${list}. Nothing placed. Placements need a version in an editable state.`);
    process.exitCode = 1;
    return;
  }
  const locs = await api("GET", `/v1/appStoreVersions/${version.id}/appStoreVersionLocalizations?limit=50`);
  const libId = await libraryId(appId);
  const images = await libraryImages(libId);
  for (const locale of LOCALES) {
    const loc = locs.data.find((x) => x.attributes.locale === locale);
    if (!loc) throw new Error(`${locale}: run "text" first`);
    const groups = new Map();
    for (const { rel, file, spec } of libraryFiles(locale)) {
      const img = images.get(referenceName(locale, rel, readFileSync(file)));
      if (!img || !READY.has(img.attributes.state)) throw new Error(`${locale} ${rel}: run "assets" first`);
      const key = `${spec.placementType}|${spec.placementGroup}`;
      if (!groups.has(key)) groups.set(key, { spec, ids: [] });
      groups.get(key).ids.push(img.id);
    }
    for (const { spec, ids } of groups.values()) {
      const label = `${locale} ${spec.placementType} ${spec.placementGroup}`;
      const query = `filter[placementType]=${spec.placementType}&filter[placementGroup]=${spec.placementGroup}&sort=placementGroupPosition&include=image&limit=50`;
      const existing = (await api("GET", `/v1/appStoreVersionLocalizations/${loc.id}/placements?${query}`)).data;
      const have = existing.map((p) => p.relationships?.image?.data?.id);
      if (have.length === ids.length && have.every((id, i) => id === ids[i])) {
        console.log(`${label}: already placed`);
        continue;
      }
      if (existing.length && !replace) {
        console.log(`${label}: has ${existing.length} placement(s), use --replace`);
        continue;
      }
      // Apple does not allow a change to a placement. Delete it and create a new one.
      for (const p of existing) await api("DELETE", `/v1/appAssetLibraryPlacements/${p.id}`);
      const created = [];
      for (const id of ids) {
        const made = await api("POST", "/v1/appAssetLibraryPlacements", {
          data: {
            type: "appAssetLibraryPlacements",
            attributes: { placementType: spec.placementType, placementGroup: spec.placementGroup },
            relationships: {
              image: { data: { type: "appAssetLibraryImages", id } },
              appStoreVersionLocalization: { data: { type: "appStoreVersionLocalizations", id: loc.id } },
            },
          },
        });
        created.push(made.data.id);
      }
      if (created.length > 1) {
        await api("POST", "/v1/appAssetLibraryPlacementOrderingRequests", {
          data: {
            type: "appAssetLibraryPlacementOrderingRequests",
            attributes: { placementGroup: spec.placementGroup },
            relationships: {
              orderedPlacements: { data: created.map((id) => ({ type: "appAssetLibraryPlacements", id })) },
              appStoreVersionLocalization: { data: { type: "appStoreVersionLocalizations", id: loc.id } },
            },
          },
        });
      }
      console.log(`${label}: placed ${created.length} image(s)${existing.length ? `, replaced ${existing.length}` : ""}`);
    }
  }
}

async function status(platform) {
  const appId = env("ASC_APP_ID");
  const version = await editableVersion(appId, platform);
  const info = await editableAppInfo(appId, platform);
  console.log(`${platform} version ${version.attributes.versionString} ${version.attributes.appStoreState}`);
  if (info) {
    for (const x of (await api("GET", `/v1/appInfos/${info.id}/appInfoLocalizations`)).data) {
      const a = x.attributes;
      console.log(`${a.locale} info: name=${a.name ?? "-"} subtitle=${a.subtitle ?? "-"} privacy=${a.privacyPolicyUrl ?? "-"}`);
    }
  }
  const detail = await api("GET", `/v1/appStoreVersions/${version.id}/appStoreReviewDetail`).then((r) => r.data, (e) => { if (e.status === 404) return null; throw e; });
  const detailFields = ["contactFirstName", "contactLastName", "contactPhone", "contactEmail", "demoAccountName", "demoAccountPassword", "notes"];
  console.log(detail ? `review detail: ${detailFields.map((k) => `${k}=${detail.attributes[k] ? "yes" : "no"}`).join(" ")}` : "review detail: none");
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

const [cmd, ...flags] = process.argv.slice(2);
const replace = flags.includes("--replace");
const platformAt = flags.indexOf("--platform");
const platform = platformAt === -1 ? "IOS" : flags[platformAt + 1];
if (!DISPLAY_TYPES_BY_PLATFORM[platform]) {
  console.log("--platform must be IOS or MAC_OS");
  process.exit(2);
}
if ((cmd === "assets" || cmd === "place") && platform !== "IOS") {
  console.log(`"${cmd}" works for IOS only`);
  process.exit(2);
}
const run = {
  check: async () => process.exit(check() ? 0 : 1),
  status: () => status(platform),
  text: () => pushText(platform),
  screenshots: () => pushScreenshots(replace, platform),
  assets: pushAssets,
  place: () => placeAssets(replace),
}[cmd];
if (!run) {
  console.log("usage: asc-listing.mjs check | status | text | screenshots [--replace] | assets | place [--replace]  (--platform IOS|MAC_OS)");
  process.exit(2);
}
run().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
