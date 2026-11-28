// Shared App Store Connect API client for the scripts in this folder.
// It reads ASC_KEY_ID, ASC_ISSUER_ID and ASC_KEY_PATH when it makes a token.
import crypto from "node:crypto";
import { readFileSync } from "node:fs";

export const API_BASE = "https://api.appstoreconnect.apple.com";

export function env(name) {
  const v = process.env[name];
  if (!v) throw new Error(`Set ${name}`);
  return v;
}

export function token() {
  const b64u = (x) => Buffer.from(x).toString("base64url");
  const now = Math.floor(Date.now() / 1000);
  const head = b64u(JSON.stringify({ alg: "ES256", kid: env("ASC_KEY_ID"), typ: "JWT" }));
  const body = b64u(JSON.stringify({ iss: env("ASC_ISSUER_ID"), iat: now, exp: now + 1100, aud: "appstoreconnect-v1" }));
  const key = crypto.createPrivateKey(readFileSync(env("ASC_KEY_PATH")));
  const sig = crypto.sign("sha256", Buffer.from(`${head}.${body}`), { key, dsaEncoding: "ieee-p1363" });
  return `${head}.${body}.${b64u(sig)}`;
}

export function createClient({ fetchImpl = fetch, getToken = token } = {}) {
  async function api(method, path, body) {
    const res = await fetchImpl(path.startsWith("http") ? path : `${API_BASE}${path}`, {
      method,
      headers: { Authorization: `Bearer ${getToken()}`, "Content-Type": "application/json" },
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

  async function listAll(path) {
    const out = [];
    let next = path;
    while (next) {
      const res = await api("GET", next);
      out.push(...res.data);
      next = res.links?.next ?? null;
    }
    return out;
  }

  return { api, listAll };
}

const defaultClient = createClient();
export const api = defaultClient.api;
export const listAll = defaultClient.listAll;
