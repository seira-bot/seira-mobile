#!/usr/bin/env node
// Moves an uploaded build to App Review for the iOS and the macOS version of the app.
//
//   node ios/AppStore/tools/asc-release.mjs plan   --version X.Y.Z [--platform IOS|MAC_OS]
//   node ios/AppStore/tools/asc-release.mjs submit --version X.Y.Z --platform IOS|MAC_OS [--build N] [--wait-minutes 90] [--no-submit]
//   node ios/AppStore/tools/asc-release.mjs pending --version X.Y.Z
//   node ios/AppStore/tools/asc-release.mjs review-detail --platform IOS|MAC_OS
//
// "plan" only reads. "submit" waits for the build, puts it on the App Store version of that
// platform and sends the version to App Review. It skips a platform that already has a version
// in review or live. "pending" runs "submit" for both platforms with the builds that already
// exist. "review-detail" copies the review details of the newest iOS version that has them.
// On an update version, "submit" fills an empty What's New from <locale>/release_notes.txt.
// The environment variable APPLE_AUTO_SUBMIT=false works like --no-submit.
// It reads ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_PATH and ASC_APP_ID, as asc-listing.mjs does.
// It never prints review details.
import { appendFileSync, readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";
import { createClient, env } from "./asc-api.mjs";

export const PLATFORMS = ["IOS", "MAC_OS"];
export const DONE_STATES = new Set(["READY_FOR_SALE", "PENDING_DEVELOPER_RELEASE", "PROCESSING_FOR_APP_STORE"]);
export const IN_FLIGHT_STATES = new Set(["WAITING_FOR_REVIEW", "IN_REVIEW", "PENDING_APPLE_RELEASE", "ACCEPTED"]);
export const EDITABLE_STATES = new Set(["PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED", "INVALID_BINARY"]);
export const RELEASED_STATES = new Set([
  ...DONE_STATES,
  "REPLACED_WITH_NEW_VERSION",
  "DEVELOPER_REMOVED_FROM_SALE",
  "REMOVED_FROM_SALE",
]);
export const WHATS_NEW_MAX = 4000;
export const REQUIRED_SCREENSHOTS = {
  IOS: ["APP_IPHONE_67", "APP_IPAD_PRO_3GEN_129"],
  MAC_OS: ["APP_DESKTOP"],
};
const REVIEW_DETAIL_FIELDS = [
  "contactFirstName",
  "contactLastName",
  "contactPhone",
  "contactEmail",
  "demoAccountRequired",
  "demoAccountName",
  "demoAccountPassword",
  "notes",
];
const STATE_WORDS = {
  WAITING_FOR_REVIEW: "waiting for review",
  IN_REVIEW: "in review",
  PENDING_APPLE_RELEASE: "pending Apple release",
  ACCEPTED: "accepted",
  READY_FOR_SALE: "live",
  PENDING_DEVELOPER_RELEASE: "pending developer release",
  PROCESSING_FOR_APP_STORE: "processing for the App Store",
};

// ---- Decisions: pure functions, no network ----

// versions: [{ id, versionString, state }] of one platform.
export function decideVersion(versions, target) {
  const done = versions.find((v) => v.versionString === target && DONE_STATES.has(v.state));
  if (done) return { action: "done", version: done, reason: `${target} is already ${STATE_WORDS[done.state]}` };
  const busy = versions.find((v) => IN_FLIGHT_STATES.has(v.state));
  if (busy) return { action: "skip", version: busy, reason: `${busy.versionString} is ${STATE_WORDS[busy.state]}` };
  const editable = versions.filter((v) => EDITABLE_STATES.has(v.state));
  const chosen = editable.find((v) => v.versionString === target) ?? editable[0];
  if (chosen) return { action: chosen.versionString === target ? "use" : "rename", version: chosen };
  return { action: "create" };
}

// builds: [{ id, number, state, uploaded }] of the app, the version and the platform.
// A build number picks one build. Without it, the newest build counts.
export function pickBuild(builds, number) {
  const sorted = [...builds].sort((a, b) => String(b.uploaded ?? "").localeCompare(String(a.uploaded ?? "")));
  const build = number == null ? sorted[0] : sorted.find((b) => b.number === String(number));
  if (!build) return { status: "none" };
  if (build.state === "VALID") return { status: "valid", build };
  if (build.state === "INVALID" || build.state === "FAILED") return { status: "failed", build };
  return { status: "processing", build };
}

// An update is a version of a platform that already had a released version.
export const isUpdate = (versions, targetId) => versions.some((v) => v.id !== targetId && RELEASED_STATES.has(v.state));

export function readReleaseNotes(locale) {
  return readFileSync(new URL(`../${locale}/release_notes.txt`, import.meta.url), "utf8");
}

// locs: [{ id, locale, attributes }]. Returns [{ id, locale, whatsNew }] for the locales with an empty whatsNew.
export function whatsNewPatches(locs, readNotes) {
  const patches = [];
  for (const loc of locs) {
    if (loc.attributes.whatsNew) continue;
    let text = "";
    try {
      text = String(readNotes(loc.locale) ?? "").trim();
    } catch {
      text = "";
    }
    if (!text) throw new Error(`No release notes for ${loc.locale}. Fill in ios/AppStore/${loc.locale}/release_notes.txt.`);
    if (text.length > WHATS_NEW_MAX) throw new Error(`The release notes for ${loc.locale} have ${text.length} characters. The limit is ${WHATS_NEW_MAX}.`);
    patches.push({ id: loc.id, locale: loc.locale, whatsNew: text });
  }
  return patches;
}

export function reviewDetailAttributes(detail) {
  const out = {};
  for (const key of REVIEW_DETAIL_FIELDS) {
    const value = detail?.attributes?.[key];
    if (value !== null && value !== undefined && value !== "") out[key] = value;
  }
  return out;
}

// locs: [{ locale, attributes, screenshots: { <displayType>: count } }]. Returns the problems.
export function checkComplete(platform, locs, { update = false } = {}) {
  const problems = [];
  if (!locs.length) problems.push("the version has no localization");
  for (const loc of locs) {
    for (const field of ["description", "keywords", "supportUrl"]) {
      if (!loc.attributes[field]) problems.push(`${loc.locale} has no ${field}`);
    }
    if (update && !loc.attributes.whatsNew) problems.push(`${loc.locale} has no whatsNew`);
    for (const type of REQUIRED_SCREENSHOTS[platform]) {
      if (!loc.screenshots[type]) problems.push(`${loc.locale} has no ${type} screenshot`);
    }
  }
  return problems;
}

export const openSubmission = (submissions) => submissions.find((s) => s.state === "READY_FOR_REVIEW") ?? null;

export const autoSubmitOff = (envValue) => String(envValue ?? "").toLowerCase() === "false";

// ---- App Store Connect calls ----

const versionOf = (x) => ({ id: x.id, versionString: x.attributes.versionString, state: x.attributes.appStoreState });
const isNotFound = (e) => e.status === 404;

export async function getVersions(client, appId, platform) {
  const res = await client.listAll(`/v1/apps/${appId}/appStoreVersions?filter[platform]=${platform}&limit=50`);
  return res.map(versionOf);
}

export async function getBuilds(client, appId, platform, version) {
  const query = `filter[app]=${appId}&filter[preReleaseVersion.version]=${version}&filter[preReleaseVersion.platform]=${platform}&sort=-uploadedDate&limit=20`;
  const res = await client.listAll(`/v1/builds?${query}`);
  return res
    .filter((b) => !b.attributes.expired)
    .map((b) => ({ id: b.id, number: b.attributes.version, state: b.attributes.processingState, uploaded: b.attributes.uploadedDate }));
}

async function getReviewDetail(client, versionId) {
  try {
    const res = await client.api("GET", `/v1/appStoreVersions/${versionId}/appStoreReviewDetail`);
    return res.data ?? null;
  } catch (e) {
    if (isNotFound(e)) return null;
    throw e;
  }
}

const compareVersions = (a, b) => {
  const pa = a.split(".").map(Number);
  const pb = b.split(".").map(Number);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pb[i] ?? 0) - (pa[i] ?? 0);
    if (d) return d;
  }
  return 0;
};

// Gives the version review details. Copies them from the newest iOS version that has them.
// Returns "present", "copied" or "missing".
export async function ensureReviewDetail(client, appId, platform, version, log) {
  if (await getReviewDetail(client, version.id)) return "present";
  const iosVersions = (await getVersions(client, appId, "IOS")).filter((v) => v.id !== version.id).sort((a, b) => compareVersions(a.versionString, b.versionString));
  for (const source of iosVersions) {
    const detail = await getReviewDetail(client, source.id);
    if (!detail) continue;
    await client.api("POST", "/v1/appStoreReviewDetails", {
      data: {
        type: "appStoreReviewDetails",
        attributes: reviewDetailAttributes(detail),
        relationships: { appStoreVersion: { data: { type: "appStoreVersions", id: version.id } } },
      },
    });
    log(`${platform}: review details copied from the iOS version ${source.versionString}`);
    return "copied";
  }
  return "missing";
}

async function getLocalizations(client, versionId) {
  const locs = await client.listAll(`/v1/appStoreVersions/${versionId}/appStoreVersionLocalizations?limit=50`);
  const out = [];
  for (const loc of locs) {
    const sets = await client.listAll(`/v1/appStoreVersionLocalizations/${loc.id}/appScreenshotSets?limit=50`);
    const screenshots = {};
    for (const set of sets) {
      const shots = await client.listAll(`/v1/appScreenshotSets/${set.id}/appScreenshots?limit=50`);
      screenshots[set.attributes.screenshotDisplayType] = shots.length;
    }
    out.push({ id: loc.id, locale: loc.attributes.locale, attributes: loc.attributes, screenshots });
  }
  return out;
}

async function submitVersion(client, appId, platform, version, log) {
  const open = openSubmission(await client.listAll(`/v1/apps/${appId}/reviewSubmissions?filter[platform]=${platform}&filter[state]=READY_FOR_REVIEW&limit=20`).then((r) => r.map((x) => ({ id: x.id, state: x.attributes.state }))));
  let submissionId = open?.id;
  if (!submissionId) {
    const made = await client.api("POST", "/v1/reviewSubmissions", {
      data: { type: "reviewSubmissions", attributes: { platform }, relationships: { app: { data: { type: "apps", id: appId } } } },
    });
    submissionId = made.data.id;
  }
  const items = await client.api("GET", `/v1/reviewSubmissions/${submissionId}/items?include=appStoreVersion&limit=50`);
  const has = items.data.some((i) => i.relationships?.appStoreVersion?.data?.id === version.id);
  if (!has) {
    await client.api("POST", "/v1/reviewSubmissionItems", {
      data: {
        type: "reviewSubmissionItems",
        relationships: {
          reviewSubmission: { data: { type: "reviewSubmissions", id: submissionId } },
          appStoreVersion: { data: { type: "appStoreVersions", id: version.id } },
        },
      },
    });
  }
  await client.api("PATCH", `/v1/reviewSubmissions/${submissionId}`, {
    data: { type: "reviewSubmissions", id: submissionId, attributes: { submitted: true } },
  });
  log(`${platform}: version ${version.versionString} sent to App Review`);
}

async function waitForBuild(client, { appId, platform, version, build, waitMinutes, sleep, log }) {
  const polls = Math.max(0, Math.floor(waitMinutes)) + 1;
  for (let i = 0; i < polls; i++) {
    const found = pickBuild(await getBuilds(client, appId, platform, version), build);
    if (found.status === "valid") return found;
    if (found.status === "failed") throw new Error(`${platform}: build ${found.build.number} is ${found.build.state}. Upload a new build.`);
    if (i < polls - 1) {
      log(`${platform}: ${found.status === "none" ? "no build yet" : `build ${found.build.number} is ${found.build.state}`}, waiting`);
      await sleep(60_000);
    }
  }
  return { status: "timeout" };
}

// Runs the steps for one platform. Returns { status, detail }.
// status: done | skipped | no-build | ready | submitted. A failed step throws.
export async function runSubmit(client, opts, deps = {}) {
  const { appId, platform, version, build = null, waitMinutes = 90, submit = true, skipIfNoBuild = false } = opts;
  const log = deps.log ?? ((line) => console.log(line));
  const sleep = deps.sleep ?? ((ms) => new Promise((r) => setTimeout(r, ms)));

  const decision = decideVersion(await getVersions(client, appId, platform), version);
  if (decision.action === "done") {
    log(`${platform}: ${decision.reason}. Nothing to do.`);
    return { status: "done", detail: decision.reason };
  }
  if (decision.action === "skip") {
    log(`${platform}: skipped. ${decision.reason}.`);
    return { status: "skipped", detail: decision.reason };
  }

  const found = await waitForBuild(client, { appId, platform, version, build, waitMinutes, sleep, log });
  if (found.status === "timeout") {
    const detail = `no valid build for ${version} after ${waitMinutes} minutes`;
    if (skipIfNoBuild) {
      log(`${platform}: skipped. ${detail}.`);
      return { status: "no-build", detail };
    }
    throw new Error(`${platform}: ${detail}`);
  }

  let target = decision.version;
  if (decision.action === "create") {
    const made = await client.api("POST", "/v1/appStoreVersions", {
      data: {
        type: "appStoreVersions",
        attributes: { platform, versionString: version, releaseType: "AFTER_APPROVAL" },
        relationships: { app: { data: { type: "apps", id: appId } } },
      },
    });
    target = versionOf(made.data);
    log(`${platform}: created version ${version}`);
  } else if (decision.action === "rename") {
    await client.api("PATCH", `/v1/appStoreVersions/${target.id}`, {
      data: { type: "appStoreVersions", id: target.id, attributes: { versionString: version } },
    });
    log(`${platform}: renamed version ${target.versionString} to ${version}`);
    target = { ...target, versionString: version };
  }

  await client.api("PATCH", `/v1/appStoreVersions/${target.id}`, {
    data: {
      type: "appStoreVersions",
      id: target.id,
      attributes: { releaseType: "AFTER_APPROVAL" },
      relationships: { build: { data: { type: "builds", id: found.build.id } } },
    },
  });
  log(`${platform}: build ${found.build.number} attached to version ${version}`);

  const detail = await ensureReviewDetail(client, appId, platform, target, log);
  if (detail === "missing" && platform !== "IOS") throw new Error(`${platform}: no review details to copy. Fill them in App Store Connect.`);

  const update = isUpdate(await getVersions(client, appId, platform), target.id);
  if (update) {
    const patches = whatsNewPatches(await getLocalizations(client, target.id), deps.readNotes ?? readReleaseNotes);
    for (const p of patches) {
      await client.api("PATCH", `/v1/appStoreVersionLocalizations/${p.id}`, {
        data: { type: "appStoreVersionLocalizations", id: p.id, attributes: { whatsNew: p.whatsNew } },
      });
      log(`${platform}: what's new set for ${p.locale}`);
    }
  }

  const problems = checkComplete(platform, await getLocalizations(client, target.id), { update });
  if (problems.length) throw new Error(`${platform}: the listing is not complete, nothing was submitted: ${problems.join("; ")}`);

  if (!submit) {
    log(`${platform}: version ${version} is ready. It was not submitted.`);
    return { status: "ready", detail: `build ${found.build.number} attached, not submitted` };
  }
  await submitVersion(client, appId, platform, target, log);
  return { status: "submitted", detail: `build ${found.build.number} sent to App Review` };
}

// ---- Plan ----

export async function describePlan(client, appId, platform, version) {
  const decision = decideVersion(await getVersions(client, appId, platform), version);
  if (decision.action === "done") return `${platform}: done, ${decision.reason}`;
  if (decision.action === "skip") return `${platform}: skip, ${decision.reason}`;
  const built = pickBuild(await getBuilds(client, appId, platform, version), null);
  const next = built.status === "valid" ? `attach build ${built.build.number}` : "wait for build";
  const what = { create: `create ${version}`, rename: `rename ${decision.version?.versionString} → ${version}`, use: `use ${version}` }[decision.action];
  return `${platform}: ${what}, ${next}, then submit`;
}

// ---- CLI ----

function parseArgs(argv) {
  const [cmd, ...rest] = argv;
  const flags = { noSubmit: false };
  for (let i = 0; i < rest.length; i++) {
    const a = rest[i];
    if (a === "--no-submit") flags.noSubmit = true;
    else if (a === "--version") flags.version = rest[++i];
    else if (a === "--platform") flags.platform = rest[++i];
    else if (a === "--build") flags.build = rest[++i];
    else if (a === "--wait-minutes") flags.waitMinutes = Number(rest[++i]);
    else throw new Error(`Unknown option ${a}`);
  }
  return { cmd, flags };
}

function summary(platform, result) {
  const file = process.env.GITHUB_STEP_SUMMARY;
  if (!file) return;
  appendFileSync(file, `| ${platform} | ${result.status} | ${result.detail} |\n`);
}

function startSummary(title) {
  const file = process.env.GITHUB_STEP_SUMMARY;
  if (file) appendFileSync(file, `### ${title}\n\n| Platform | Result | Detail |\n|----------|--------|--------|\n`);
}

async function main(argv) {
  const { cmd, flags } = parseArgs(argv);
  const client = createClient();
  const appId = env("ASC_APP_ID");
  const needVersion = () => {
    if (!/^\d+(\.\d+){0,2}$/.test(flags.version ?? "")) throw new Error("Set --version X.Y.Z");
    return flags.version;
  };
  const needPlatform = () => {
    if (!PLATFORMS.includes(flags.platform)) throw new Error("--platform must be IOS or MAC_OS");
    return flags.platform;
  };
  if (flags.build != null && !/^\d+(\.\d+){0,2}$/.test(flags.build)) throw new Error("--build must be 1 to 3 numbers separated by dots");

  if (cmd === "plan") {
    const version = needVersion();
    for (const platform of flags.platform ? [needPlatform()] : PLATFORMS) console.log(await describePlan(client, appId, platform, version));
    return 0;
  }

  if (cmd === "submit") {
    const version = needVersion();
    const platform = needPlatform();
    startSummary(`Apple release ${version}`);
    const result = await runSubmit(client, {
      appId,
      platform,
      version,
      build: flags.build ?? null,
      waitMinutes: flags.waitMinutes ?? 90,
      submit: !flags.noSubmit && !autoSubmitOff(process.env.APPLE_AUTO_SUBMIT),
    });
    summary(platform, result);
    return 0;
  }

  if (cmd === "pending") {
    const version = needVersion();
    startSummary(`Apple release ${version} (daily check)`);
    let failed = false;
    for (const platform of PLATFORMS) {
      try {
        const result = await runSubmit(client, {
          appId,
          platform,
          version,
          waitMinutes: 0,
          skipIfNoBuild: true,
          submit: !autoSubmitOff(process.env.APPLE_AUTO_SUBMIT),
        });
        summary(platform, result);
      } catch (e) {
        failed = true;
        console.error(e.message);
        summary(platform, { status: "failed", detail: e.message });
      }
    }
    return failed ? 1 : 0;
  }

  if (cmd === "review-detail") {
    const platform = needPlatform();
    const versions = await getVersions(client, appId, platform);
    const target = versions.find((v) => EDITABLE_STATES.has(v.state));
    if (!target) throw new Error(`No editable ${platform} version.`);
    const result = await ensureReviewDetail(client, appId, platform, target, (line) => console.log(line));
    console.log(`${platform}: review details ${result}`);
    return result === "missing" ? 1 : 0;
  }

  console.log("usage: asc-release.mjs plan|submit|pending|review-detail  (see the top of the file)");
  return 2;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main(process.argv.slice(2)).then(
    (code) => process.exit(code),
    (e) => {
      console.error(e.message);
      process.exit(1);
    },
  );
}
