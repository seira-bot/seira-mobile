import { createSign } from "node:crypto";
import fs from "node:fs";
import http from "node:http";
import https from "node:https";
import path from "node:path";
import { pathToFileURL } from "node:url";
import { parseArgs } from "node:util";

const SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const DEFAULT_OAUTH_ENDPOINT = "https://oauth2.googleapis.com/token";
const DEFAULT_API_BASE = "https://androidpublisher.googleapis.com";
const DEFAULT_NOTES_ROOT = "android/PlayStore/release-notes";
const NOTES_LIMIT = 500;
// A bundle upload can take minutes. Node's global fetch cuts a response that
// starts later than 300 seconds (undici headersTimeout), so this uses node:https.
const SOCKET_TIMEOUT_MS = 15 * 60 * 1000;

const base64url = (value) => Buffer.from(value).toString("base64url");

export function signJwt({ clientEmail, privateKey, tokenUri, now = Math.floor(Date.now() / 1000) }) {
  const header = { alg: "RS256", typ: "JWT" };
  const claims = { iss: clientEmail, scope: SCOPE, aud: tokenUri, iat: now, exp: now + 3600 };
  const unsigned = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(claims))}`;
  const signature = createSign("RSA-SHA256").update(unsigned).sign(privateKey);
  return `${unsigned}.${signature.toString("base64url")}`;
}

export function readReleaseNotes({ notesRoot, versionName, track, log = () => {} }) {
  const folder = path.join(notesRoot, versionName);
  if (!fs.existsSync(folder) || !fs.statSync(folder).isDirectory()) {
    if (track === "production") {
      throw new Error(`Release notes folder not found: ${folder}. The production track needs it.`);
    }
    log(`warning: release notes folder not found: ${folder}. The release has no notes.`);
    return [];
  }
  const files = fs.readdirSync(folder).filter((name) => name.endsWith(".txt")).sort();
  if (files.length === 0 && track === "production") {
    throw new Error(`Release notes folder has no .txt file: ${folder}. The production track needs one.`);
  }
  return files.map((name) => {
    const file = path.join(folder, name);
    const text = fs.readFileSync(file, "utf8").trim();
    if (text.length === 0) throw new Error(`Release notes file is empty: ${file}`);
    const length = [...text].length;
    if (length > NOTES_LIMIT) {
      throw new Error(`Release notes file is longer than ${NOTES_LIMIT} characters (${length}): ${file}`);
    }
    return { language: name.slice(0, -".txt".length), text };
  });
}

export function buildRelease({ versionName, versionCode, status = "completed", rollout, notes = [] }) {
  if (status !== "completed" && status !== "draft") {
    throw new Error(`Status must be completed or draft, not ${status}.`);
  }
  const release = { name: versionName, versionCodes: [String(versionCode)], status };
  if (rollout !== undefined && rollout !== null) {
    if (!Number.isInteger(rollout) || rollout < 1 || rollout > 100) {
      throw new Error("Rollout must be a whole number from 1 to 100.");
    }
    if (rollout < 100) {
      if (status === "draft") throw new Error("A rollout below 100 cannot use the draft status.");
      release.status = "inProgress";
      release.userFraction = rollout / 100;
    }
  }
  if (notes.length > 0) {
    release.releaseNotes = notes.map(({ language, text }) => ({ language, text }));
  }
  return release;
}

function request(urlString, { method = "GET", headers = {}, body } = {}) {
  const url = new URL(urlString);
  const transport = url.protocol === "http:" ? http : https;
  const payload = body === undefined ? undefined : Buffer.isBuffer(body) ? body : Buffer.from(body);
  const finalHeaders = { ...headers };
  // Google front ends answer HTTP 411 to a POST without Content-Length.
  if (method !== "GET") finalHeaders["Content-Length"] = String(payload ? payload.length : 0);
  return new Promise((resolve, reject) => {
    const req = transport.request(url, { method, headers: finalHeaders }, (res) => {
      const chunks = [];
      res.on("data", (chunk) => chunks.push(chunk));
      res.on("end", () => resolve({ status: res.statusCode ?? 0, body: Buffer.concat(chunks).toString("utf8") }));
      res.on("error", reject);
    });
    req.setTimeout(SOCKET_TIMEOUT_MS, () => req.destroy(new Error("The request timed out.")));
    req.on("error", reject);
    if (payload) req.write(payload);
    req.end();
  });
}

function parseJson(text) {
  if (!text) return {};
  try {
    return JSON.parse(text);
  } catch {
    return {};
  }
}

function describeFailure(step, response) {
  const json = parseJson(response.body);
  const detail = json.error?.message ?? json.error_description ?? (typeof json.error === "string" ? json.error : "");
  return `${step} failed with HTTP ${response.status}${detail ? `: ${detail}` : ""}`;
}

async function call(step, url, options) {
  const response = await request(url, options);
  if (response.status < 200 || response.status >= 300) throw new Error(describeFailure(step, response));
  return parseJson(response.body);
}

async function fetchToken(serviceAccount, tokenUri) {
  const assertion = signJwt({
    clientEmail: serviceAccount.client_email,
    privateKey: serviceAccount.private_key,
    tokenUri,
  });
  const form = new URLSearchParams({
    grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
    assertion,
  });
  const json = await call("Token request", tokenUri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: form.toString(),
  });
  if (!json.access_token) throw new Error("Token request returned no access token.");
  return json.access_token;
}

export async function publish({
  package: packageName,
  aab,
  track,
  versionName,
  notesRoot = DEFAULT_NOTES_ROOT,
  status = "completed",
  rollout,
  dryRun = false,
  serviceAccount,
  apiBase = DEFAULT_API_BASE,
  log = () => {},
}) {
  for (const [name, value] of Object.entries({ package: packageName, aab, track, versionName })) {
    if (!value) throw new Error(`Missing option: ${name}`);
  }
  if (!serviceAccount?.client_email || !serviceAccount?.private_key) {
    throw new Error("The service account needs client_email and private_key.");
  }
  if (status !== "completed" && status !== "draft") {
    throw new Error(`Status must be completed or draft, not ${status}.`);
  }
  if (rollout !== undefined && rollout !== null && status === "draft" && rollout < 100) {
    throw new Error("A rollout below 100 cannot use the draft status.");
  }
  const notes = readReleaseNotes({ notesRoot, versionName, track, log });
  const bundle = fs.readFileSync(aab);
  const tokenUri = serviceAccount.token_uri || DEFAULT_OAUTH_ENDPOINT;

  const token = await fetchToken(serviceAccount, tokenUri);
  const auth = { Authorization: `Bearer ${token}` };
  const json = { ...auth, "Content-Type": "application/json" };
  const app = `${apiBase}/androidpublisher/v3/applications/${encodeURIComponent(packageName)}`;

  const edit = await call("Create edit", `${app}/edits`, { method: "POST", headers: json, body: "{}" });
  const editUrl = `${app}/edits/${encodeURIComponent(edit.id)}`;
  log(`edit ${edit.id} created`);

  try {
    const uploaded = await call(
      "Bundle upload",
      `${apiBase}/upload/androidpublisher/v3/applications/${encodeURIComponent(packageName)}/edits/${encodeURIComponent(edit.id)}/bundles?uploadType=media`,
      { method: "POST", headers: { ...auth, "Content-Type": "application/octet-stream" }, body: bundle },
    );
    const versionCode = uploaded.versionCode;
    log(`bundle uploaded as version code ${versionCode}`);

    const release = buildRelease({ versionName, versionCode, status, rollout, notes });
    await call("Track update", `${editUrl}/tracks/${encodeURIComponent(track)}`, {
      method: "PUT",
      headers: json,
      body: JSON.stringify({ track, releases: [release] }),
    });

    let committed = false;
    if (dryRun) {
      await call("Edit validation", `${editUrl}:validate`, { method: "POST", headers: auth });
      await call("Edit delete", editUrl, { method: "DELETE", headers: auth });
      log("dry run: edit validated and deleted");
    } else {
      await call("Edit commit", `${editUrl}:commit`, { method: "POST", headers: auth });
      committed = true;
      log("edit committed");
    }
    return {
      package: packageName,
      track,
      status: release.status,
      versionCode,
      versionName,
      userFraction: release.userFraction ?? null,
      notes: notes.map((note) => note.language),
      committed,
    };
  } catch (error) {
    await request(editUrl, { method: "DELETE", headers: auth }).catch(() => {});
    throw error;
  }
}

function writeSummary(result) {
  const file = process.env.GITHUB_STEP_SUMMARY;
  if (!file) return;
  const lines = [
    "### Google Play",
    "",
    "| Item | Value |",
    "|------|-------|",
    `| Package | ${result.package} |`,
    `| Track | ${result.track} |`,
    `| Status | ${result.status} |`,
    `| Version | ${result.versionName} (${result.versionCode}) |`,
    `| Rollout | ${result.userFraction ?? "n/a"} |`,
    `| Notes | ${result.notes.join(", ") || "none"} |`,
    `| Committed | ${result.committed} |`,
    "",
  ];
  fs.appendFileSync(file, lines.join("\n"));
}

async function main() {
  const { values } = parseArgs({
    options: {
      package: { type: "string" },
      aab: { type: "string" },
      track: { type: "string" },
      "version-name": { type: "string" },
      "notes-root": { type: "string", default: DEFAULT_NOTES_ROOT },
      status: { type: "string", default: "completed" },
      rollout: { type: "string" },
      "dry-run": { type: "boolean", default: false },
    },
  });
  let rollout;
  if (values.rollout !== undefined && values.rollout !== "") {
    if (!/^\d+$/.test(values.rollout)) throw new Error("Rollout must be a whole number from 1 to 100.");
    rollout = Number(values.rollout);
    if (rollout < 1 || rollout > 100) throw new Error("Rollout must be a whole number from 1 to 100.");
  }
  const raw = process.env.PLAY_SERVICE_ACCOUNT_JSON;
  if (!raw) throw new Error("PLAY_SERVICE_ACCOUNT_JSON is not set.");
  let serviceAccount;
  try {
    serviceAccount = JSON.parse(raw);
  } catch {
    throw new Error("PLAY_SERVICE_ACCOUNT_JSON is not valid JSON.");
  }
  const result = await publish({
    package: values.package,
    aab: values.aab,
    track: values.track,
    versionName: values["version-name"],
    notesRoot: values["notes-root"],
    status: values.status,
    rollout,
    dryRun: values["dry-run"],
    serviceAccount,
    apiBase: process.env.PLAY_API_BASE || DEFAULT_API_BASE,
    log: (line) => console.error(line),
  });
  console.log(JSON.stringify(result));
  writeSummary(result);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(`error: ${error.message}`);
    process.exit(1);
  });
}
