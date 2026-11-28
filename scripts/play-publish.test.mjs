import assert from "node:assert/strict";
import { createVerify, generateKeyPairSync } from "node:crypto";
import fs from "node:fs";
import http from "node:http";
import os from "node:os";
import path from "node:path";
import { after, before, test } from "node:test";
import { fileURLToPath } from "node:url";

import { buildRelease, publish, readReleaseNotes, signJwt } from "./play-publish.mjs";

const PACKAGE = "bot.seira.companion";
const APP = `/androidpublisher/v3/applications/${PACKAGE}`;
const here = path.dirname(fileURLToPath(import.meta.url));

const { privateKey, publicKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
const privatePem = privateKey.export({ type: "pkcs8", format: "pem" });

let tmp;
let aabPath;
const aabBytes = Buffer.from([0x50, 0x4b, 0x03, 0x04, 0x00, 0xff, 0x10, 0x20, 0x30]);

before(() => {
  tmp = fs.mkdtempSync(path.join(os.tmpdir(), "play-publish-"));
  aabPath = path.join(tmp, "app-release.aab");
  fs.writeFileSync(aabPath, aabBytes);
});

after(() => {
  fs.rmSync(tmp, { recursive: true, force: true });
});

function notesRoot(files) {
  const root = fs.mkdtempSync(path.join(tmp, "notes-"));
  for (const [relative, text] of Object.entries(files)) {
    const file = path.join(root, relative);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, text);
  }
  return root;
}

async function startMock({ failTrack = false } = {}) {
  const requests = [];
  const server = http.createServer((req, res) => {
    const chunks = [];
    req.on("data", (chunk) => chunks.push(chunk));
    req.on("end", () => {
      const url = new URL(req.url, "http://localhost");
      const record = {
        method: req.method,
        path: url.pathname,
        query: url.search,
        headers: req.headers,
        body: Buffer.concat(chunks),
      };
      requests.push(record);
      const send = (status, json) => {
        res.writeHead(status, { "Content-Type": "application/json" });
        res.end(json === undefined ? "" : JSON.stringify(json));
      };
      if (req.method === "POST" && url.pathname === "/token") return send(200, { access_token: "test-token" });
      if (req.method === "POST" && url.pathname === `${APP}/edits`) return send(200, { id: "edit-1" });
      if (req.method === "POST" && url.pathname.endsWith("/bundles")) return send(200, { versionCode: 10201 });
      if (req.method === "PUT" && url.pathname.includes("/tracks/")) {
        if (failTrack) return send(403, { error: { code: 403, message: "The caller does not have permission" } });
        return send(200, {});
      }
      if (req.method === "POST" && url.pathname.endsWith(":validate")) return send(200, {});
      if (req.method === "POST" && url.pathname.endsWith(":commit")) return send(200, {});
      if (req.method === "DELETE") return send(204);
      return send(404, { error: { message: "unexpected request" } });
    });
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  return {
    base,
    requests,
    close: () => new Promise((resolve) => server.close(resolve)),
  };
}

function serviceAccount(base) {
  return {
    client_email: "publisher@example.iam.gserviceaccount.com",
    private_key: privatePem,
    token_uri: `${base}/token`,
  };
}

async function run(mock, overrides = {}) {
  return publish({
    package: PACKAGE,
    aab: aabPath,
    track: "internal",
    versionName: "9.9.9",
    notesRoot: notesRoot({}),
    serviceAccount: serviceAccount(mock.base),
    apiBase: mock.base,
    ...overrides,
  });
}

const summary = (requests) => requests.map((r) => `${r.method} ${r.path}`);

test("commit flow calls the API in order and sends the bundle bytes", async () => {
  const mock = await startMock();
  try {
    const root = notesRoot({
      "9.9.9/en-US.txt": "English notes\n",
      "9.9.9/id.txt": "Catatan Indonesia\n",
    });
    const result = await run(mock, { notesRoot: root, track: "production" });

    assert.deepEqual(summary(mock.requests), [
      "POST /token",
      `POST ${APP}/edits`,
      `POST /upload${APP}/edits/edit-1/bundles`,
      `PUT ${APP}/edits/edit-1/tracks/production`,
      `POST ${APP}/edits/edit-1:commit`,
    ]);
    const [token, create, upload, track, commit] = mock.requests;

    assert.equal(token.headers["content-type"], "application/x-www-form-urlencoded");
    const form = new URLSearchParams(token.body.toString());
    assert.equal(form.get("grant_type"), "urn:ietf:params:oauth:grant-type:jwt-bearer");

    for (const call of [create, upload, track, commit]) {
      assert.equal(call.headers.authorization, "Bearer test-token");
    }
    assert.equal(upload.query, "?uploadType=media");
    assert.equal(upload.headers["content-type"], "application/octet-stream");
    assert.ok(upload.body.equals(aabBytes));

    assert.deepEqual(JSON.parse(track.body.toString()), {
      track: "production",
      releases: [
        {
          name: "9.9.9",
          versionCodes: ["10201"],
          status: "completed",
          releaseNotes: [
            { language: "en-US", text: "English notes" },
            { language: "id", text: "Catatan Indonesia" },
          ],
        },
      ],
    });

    assert.deepEqual(result, {
      package: PACKAGE,
      track: "production",
      status: "completed",
      versionCode: 10201,
      versionName: "9.9.9",
      userFraction: null,
      notes: ["en-US", "id"],
      committed: true,
    });
  } finally {
    await mock.close();
  }
});

test("the JWT carries the claims and a valid RS256 signature", async () => {
  const mock = await startMock();
  try {
    await run(mock);
    const form = new URLSearchParams(mock.requests[0].body.toString());
    const [header, claims, signature] = form.get("assertion").split(".");
    assert.deepEqual(JSON.parse(Buffer.from(header, "base64url")), { alg: "RS256", typ: "JWT" });
    const payload = JSON.parse(Buffer.from(claims, "base64url"));
    assert.equal(payload.iss, "publisher@example.iam.gserviceaccount.com");
    assert.equal(payload.scope, "https://www.googleapis.com/auth/androidpublisher");
    assert.equal(payload.aud, `${mock.base}/token`);
    assert.equal(payload.exp - payload.iat, 3600);
    const verifier = createVerify("RSA-SHA256").update(`${header}.${claims}`);
    assert.ok(verifier.verify(publicKey, Buffer.from(signature, "base64url")));
  } finally {
    await mock.close();
  }
});

test("signJwt uses the given time", () => {
  const jwt = signJwt({ clientEmail: "a@b.c", privateKey: privatePem, tokenUri: "https://t.example/token", now: 1000 });
  const claims = JSON.parse(Buffer.from(jwt.split(".")[1], "base64url"));
  assert.equal(claims.iat, 1000);
  assert.equal(claims.exp, 4600);
});

test("dry run validates and deletes the edit without a commit", async () => {
  const mock = await startMock();
  try {
    const result = await run(mock, { dryRun: true });
    assert.deepEqual(summary(mock.requests).slice(-2), [
      `POST ${APP}/edits/edit-1:validate`,
      `DELETE ${APP}/edits/edit-1`,
    ]);
    assert.ok(!mock.requests.some((r) => r.path.endsWith(":commit")));
    assert.equal(result.committed, false);
  } finally {
    await mock.close();
  }
});

test("a rollout below 100 sends inProgress with the user fraction", async () => {
  const mock = await startMock();
  try {
    const result = await run(mock, { rollout: 20 });
    const track = mock.requests.find((r) => r.method === "PUT");
    const release = JSON.parse(track.body.toString()).releases[0];
    assert.equal(release.status, "inProgress");
    assert.equal(release.userFraction, 0.2);
    assert.equal(result.status, "inProgress");
    assert.equal(result.userFraction, 0.2);
  } finally {
    await mock.close();
  }
});

test("a rollout of 100 keeps the completed status", () => {
  const release = buildRelease({ versionName: "1.0.0", versionCode: 10000, rollout: 100 });
  assert.equal(release.status, "completed");
  assert.equal(release.userFraction, undefined);
});

test("a rollout with the draft status fails before any request", async () => {
  const mock = await startMock();
  try {
    await assert.rejects(run(mock, { rollout: 20, status: "draft" }), /draft/);
    assert.equal(mock.requests.length, 0);
  } finally {
    await mock.close();
  }
});

test("the production track without a notes folder fails before any request", async () => {
  const mock = await startMock();
  try {
    await assert.rejects(run(mock, { track: "production" }), /notes folder not found/i);
    assert.equal(mock.requests.length, 0);
  } finally {
    await mock.close();
  }
});

test("another track without a notes folder sends no release notes", async () => {
  const mock = await startMock();
  const lines = [];
  try {
    const result = await run(mock, { track: "internal", log: (line) => lines.push(line) });
    const track = mock.requests.find((r) => r.method === "PUT");
    assert.equal(JSON.parse(track.body.toString()).releases[0].releaseNotes, undefined);
    assert.deepEqual(result.notes, []);
    assert.ok(lines.some((line) => line.startsWith("warning")));
  } finally {
    await mock.close();
  }
});

test("a notes file over 500 characters fails and names the file", async () => {
  const mock = await startMock();
  try {
    const root = notesRoot({ "9.9.9/en-US.txt": "x".repeat(501) });
    await assert.rejects(run(mock, { notesRoot: root }), (error) => {
      assert.match(error.message, /en-US\.txt/);
      assert.match(error.message, /500/);
      return true;
    });
    assert.equal(mock.requests.length, 0);
  } finally {
    await mock.close();
  }
});

test("a notes file of exactly 500 characters passes", () => {
  const root = notesRoot({ "9.9.9/en-US.txt": `${"y".repeat(500)}\n` });
  const notes = readReleaseNotes({ notesRoot: root, versionName: "9.9.9", track: "internal" });
  assert.equal(notes[0].text.length, 500);
});

test("an empty notes file fails", () => {
  const root = notesRoot({ "9.9.9/id.txt": "  \n" });
  assert.throws(
    () => readReleaseNotes({ notesRoot: root, versionName: "9.9.9", track: "internal" }),
    /empty.*id\.txt/,
  );
});

test("an API error on the track update deletes the edit and does not commit", async () => {
  const mock = await startMock({ failTrack: true });
  try {
    await assert.rejects(run(mock), (error) => {
      assert.match(error.message, /403/);
      assert.match(error.message, /The caller does not have permission/);
      return true;
    });
    assert.deepEqual(summary(mock.requests).slice(-2), [
      `PUT ${APP}/edits/edit-1/tracks/internal`,
      `DELETE ${APP}/edits/edit-1`,
    ]);
    assert.ok(!mock.requests.some((r) => r.path.endsWith(":commit")));
  } finally {
    await mock.close();
  }
});

test("the release notes of the repository version parse", () => {
  const notesDir = path.join(here, "..", "android", "PlayStore", "release-notes");
  const notes = readReleaseNotes({ notesRoot: notesDir, versionName: "1.2.0", track: "production" });
  assert.deepEqual(notes.map((note) => note.language), ["en-US", "id"]);
  for (const note of notes) assert.ok([...note.text].length <= 500);
});
