import assert from "node:assert/strict";
import { test } from "node:test";
import { createClient } from "./asc-api.mjs";
import {
  autoSubmitOff,
  checkComplete,
  decideVersion,
  describePlan,
  openSubmission,
  pickBuild,
  reviewDetailAttributes,
  runSubmit,
} from "./asc-release.mjs";

const APP = "app1";
const SECRET = "s3cret-demo-value";

// A small in-memory App Store Connect. It answers only the calls the script makes.
function fakeAsc(seed = {}) {
  const state = {
    versions: seed.versions ?? [],
    builds: seed.builds ?? [],
    details: seed.details ?? {},
    locs: seed.locs ?? {},
    submissions: seed.submissions ?? [],
    items: seed.items ?? {},
    calls: [],
    next: 100,
    onBuildPoll: seed.onBuildPoll,
  };
  const ok = (data, extra = {}) => ({ status: 200, body: { data, links: {}, ...extra } });
  const route = (method, url, body) => {
    const u = new URL(url);
    const path = u.pathname;
    const q = u.searchParams;
    let m;
    if (method === "GET" && path === `/v1/apps/${APP}/appStoreVersions`) {
      return ok(state.versions.filter((v) => v.attributes.platform === q.get("filter[platform]")));
    }
    if (method === "POST" && path === "/v1/appStoreVersions") {
      const v = { id: `v${state.next++}`, attributes: { ...body.data.attributes, appStoreState: "PREPARE_FOR_SUBMISSION" } };
      state.versions.push(v);
      return ok(v);
    }
    if (method === "PATCH" && (m = path.match(/^\/v1\/appStoreVersions\/(\w+)$/))) {
      const v = state.versions.find((x) => x.id === m[1]);
      Object.assign(v.attributes, body.data.attributes);
      if (body.data.relationships?.build) v.build = body.data.relationships.build.data.id;
      return ok(v);
    }
    if (method === "GET" && path === "/v1/builds") {
      state.onBuildPoll?.(state);
      return ok(state.builds.filter((b) => b.platform === q.get("filter[preReleaseVersion.platform]") && b.preRelease === q.get("filter[preReleaseVersion.version]")).map((b) => ({ id: b.id, attributes: b.attributes })));
    }
    if (method === "GET" && (m = path.match(/^\/v1\/appStoreVersions\/(\w+)\/appStoreReviewDetail$/))) {
      const d = state.details[m[1]];
      if (!d) return { status: 404, body: { errors: [{ code: "NOT_FOUND", title: "none" }] } };
      return ok({ id: `d-${m[1]}`, attributes: d });
    }
    if (method === "POST" && path === "/v1/appStoreReviewDetails") {
      state.details[body.data.relationships.appStoreVersion.data.id] = body.data.attributes;
      return ok({ id: "d-new" });
    }
    if (method === "GET" && (m = path.match(/^\/v1\/appStoreVersions\/(\w+)\/appStoreVersionLocalizations$/))) {
      return ok((state.locs[m[1]] ?? state.locs.default).map((l) => ({ id: `loc-${l.locale}`, attributes: { locale: l.locale, ...l.attributes } })));
    }
    if (method === "GET" && (m = path.match(/^\/v1\/appStoreVersionLocalizations\/loc-([\w-]+)\/appScreenshotSets$/))) {
      const l = (state.locs[state.currentVersion] ?? state.locs.default).find((x) => x.locale === m[1]);
      return ok(Object.keys(l.shots).map((t) => ({ id: `set-${m[1]}-${t}`, attributes: { screenshotDisplayType: t } })));
    }
    if (method === "GET" && (m = path.match(/^\/v1\/appScreenshotSets\/set-([\w-]+?)-(APP_\w+)\/appScreenshots$/))) {
      const l = (state.locs[state.currentVersion] ?? state.locs.default).find((x) => x.locale === m[1]);
      return ok(Array.from({ length: l.shots[m[2]] }, (_, i) => ({ id: `s${i}` })));
    }
    if (method === "GET" && path === `/v1/apps/${APP}/reviewSubmissions`) {
      return ok(state.submissions.filter((s) => s.attributes.platform === q.get("filter[platform]") && s.attributes.state === q.get("filter[state]")));
    }
    if (method === "POST" && path === "/v1/reviewSubmissions") {
      const s = { id: `rs${state.next++}`, attributes: { ...body.data.attributes, state: "READY_FOR_REVIEW" } };
      state.submissions.push(s);
      return ok(s);
    }
    if (method === "GET" && (m = path.match(/^\/v1\/reviewSubmissions\/(\w+)\/items$/))) {
      return ok((state.items[m[1]] ?? []).map((id) => ({ id: `i-${id}`, relationships: { appStoreVersion: { data: { id } } } })));
    }
    if (method === "POST" && path === "/v1/reviewSubmissionItems") {
      const sid = body.data.relationships.reviewSubmission.data.id;
      (state.items[sid] ??= []).push(body.data.relationships.appStoreVersion.data.id);
      return ok({ id: "item" });
    }
    if (method === "PATCH" && (m = path.match(/^\/v1\/reviewSubmissions\/(\w+)$/))) {
      const s = state.submissions.find((x) => x.id === m[1]);
      if (body.data.attributes.submitted) s.attributes.state = "WAITING_FOR_REVIEW";
      return ok(s);
    }
    return { status: 500, body: { errors: [{ code: "UNEXPECTED", title: `${method} ${path}` }] } };
  };
  const fetchImpl = async (url, init) => {
    const body = init.body ? JSON.parse(init.body) : null;
    const res = route(init.method, url, body);
    state.calls.push(`${init.method} ${new URL(url).pathname}`);
    return { ok: res.status < 400, status: res.status, text: async () => JSON.stringify(res.body) };
  };
  return { state, client: createClient({ fetchImpl, getToken: () => "token" }) };
}

const ver = (id, platform, versionString, appStoreState) => ({ id, attributes: { platform, versionString, appStoreState } });
const build = (id, platform, preRelease, number, processingState, uploadedDate = "2026-11-28T10:00:00Z") => ({
  id,
  platform,
  preRelease,
  attributes: { version: number, processingState, uploadedDate, expired: false },
});
const goodLoc = (types) => ({ locale: "en-US", attributes: { description: "d", keywords: "k", supportUrl: "https://x.test" }, shots: Object.fromEntries(types.map((t) => [t, 3])) });
const detail = { contactFirstName: "A", contactEmail: SECRET, demoAccountPassword: SECRET, notes: SECRET, demoAccountRequired: true };
const wrote = (state) => state.calls.filter((c) => !c.startsWith("GET"));
const deps = () => {
  const lines = [];
  const sleeps = [];
  return { lines, sleeps, log: (l) => lines.push(l), sleep: async (ms) => sleeps.push(ms) };
};
const opts = (over) => ({ appId: APP, platform: "MAC_OS", version: "1.2.0", build: "6.4", waitMinutes: 90, submit: true, ...over });

test("decideVersion: a live or processing version of the target is done", () => {
  for (const state of ["READY_FOR_SALE", "PENDING_DEVELOPER_RELEASE", "PROCESSING_FOR_APP_STORE"]) {
    const d = decideVersion([{ id: "a", versionString: "1.2.0", state }], "1.2.0");
    assert.equal(d.action, "done", state);
  }
});

test("decideVersion: a version in review makes the platform skip", () => {
  for (const state of ["WAITING_FOR_REVIEW", "IN_REVIEW", "PENDING_APPLE_RELEASE", "ACCEPTED"]) {
    const d = decideVersion([{ id: "a", versionString: "1.0", state }], "1.2.0");
    assert.equal(d.action, "skip", state);
    assert.match(d.reason, /1\.0 is /);
  }
});

test("decideVersion: an editable version is renamed or used, else a new one is created", () => {
  for (const state of ["PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED", "INVALID_BINARY"]) {
    assert.equal(decideVersion([{ id: "a", versionString: "1.0", state }], "1.2.0").action, "rename", state);
  }
  assert.equal(decideVersion([{ id: "a", versionString: "1.2.0", state: "PREPARE_FOR_SUBMISSION" }], "1.2.0").action, "use");
  assert.equal(decideVersion([{ id: "a", versionString: "1.0", state: "READY_FOR_SALE" }], "1.2.0").action, "create");
  assert.equal(decideVersion([], "1.2.0").action, "create");
});

test("pickBuild: valid, failed, processing, none, and a chosen number", () => {
  const b = (number, state, uploaded) => ({ id: `b${number}`, number, state, uploaded });
  assert.equal(pickBuild([b("6.3", "VALID", "1"), b("6.4", "VALID", "2")], null).build.number, "6.4");
  assert.equal(pickBuild([b("6.3", "VALID", "1"), b("6.4", "PROCESSING", "2")], null).status, "processing");
  assert.equal(pickBuild([b("6.4", "INVALID", "2")], "6.4").status, "failed");
  assert.equal(pickBuild([b("6.4", "FAILED", "2")], null).status, "failed");
  assert.equal(pickBuild([b("6.3", "VALID", "1")], "6.4").status, "none");
  assert.equal(pickBuild([], null).status, "none");
  assert.equal(pickBuild([b("6.3", "VALID", "1"), b("6.4", "PROCESSING", "2")], "6.3").status, "valid");
});

test("checkComplete needs text and the screenshots of the platform", () => {
  assert.deepEqual(checkComplete("MAC_OS", [goodLoc(["APP_DESKTOP"])]), []);
  assert.deepEqual(checkComplete("IOS", [goodLoc(["APP_IPHONE_67", "APP_IPAD_PRO_3GEN_129"])]), []);
  assert.match(checkComplete("IOS", [goodLoc(["APP_IPHONE_67"])]).join(), /APP_IPAD_PRO_3GEN_129/);
  assert.match(checkComplete("MAC_OS", [{ ...goodLoc([]), attributes: { description: "d" } }]).join(), /keywords.*supportUrl.*APP_DESKTOP/);
  assert.ok(checkComplete("MAC_OS", []).length);
});

test("reviewDetailAttributes keeps the filled fields only", () => {
  assert.deepEqual(reviewDetailAttributes({ attributes: { contactFirstName: "A", notes: "", demoAccountName: null, other: "x", demoAccountRequired: false } }), {
    contactFirstName: "A",
    demoAccountRequired: false,
  });
});

test("openSubmission and autoSubmitOff", () => {
  assert.equal(openSubmission([{ id: "1", state: "COMPLETE" }, { id: "2", state: "READY_FOR_REVIEW" }]).id, "2");
  assert.equal(openSubmission([]), null);
  assert.equal(autoSubmitOff("false"), true);
  assert.equal(autoSubmitOff("FALSE"), true);
  assert.equal(autoSubmitOff("true"), false);
  assert.equal(autoSubmitOff(undefined), false);
});

test("a version that is already live is done with no write", async () => {
  const { state, client } = fakeAsc({ versions: [ver("m1", "MAC_OS", "1.2.0", "READY_FOR_SALE")] });
  const d = deps();
  const r = await runSubmit(client, opts(), d);
  assert.equal(r.status, "done");
  assert.deepEqual(wrote(state), []);
});

test("a version in review is skipped with exit 0 and no write (iOS 1.0 case)", async () => {
  const { state, client } = fakeAsc({ versions: [ver("i1", "IOS", "1.0", "WAITING_FOR_REVIEW")] });
  const d = deps();
  const r = await runSubmit(client, opts({ platform: "IOS" }), d);
  assert.equal(r.status, "skipped");
  assert.match(r.detail, /1\.0 is waiting for review/);
  assert.deepEqual(wrote(state), []);
  assert.match(d.lines.join("\n"), /skipped/);
});

test("an editable version is renamed, gets the build and the review details, and is submitted", async () => {
  const { state, client } = fakeAsc({
    versions: [ver("i1", "IOS", "1.0", "WAITING_FOR_REVIEW"), ver("m1", "MAC_OS", "1.0", "PREPARE_FOR_SUBMISSION")],
    builds: [build("b1", "MAC_OS", "1.2.0", "6.4", "VALID")],
    details: { i1: detail },
    locs: { default: [goodLoc(["APP_DESKTOP"])] },
  });
  state.currentVersion = "default";
  const d = deps();
  const r = await runSubmit(client, opts(), d);
  assert.equal(r.status, "submitted");
  const mac = state.versions.find((v) => v.id === "m1");
  assert.equal(mac.attributes.versionString, "1.2.0");
  assert.equal(mac.attributes.releaseType, "AFTER_APPROVAL");
  assert.equal(mac.build, "b1");
  assert.deepEqual(state.details.m1, reviewDetailAttributes({ attributes: detail }));
  assert.equal(state.submissions.length, 1);
  assert.equal(state.submissions[0].attributes.platform, "MAC_OS");
  assert.equal(state.submissions[0].attributes.state, "WAITING_FOR_REVIEW");
  assert.deepEqual(state.items[state.submissions[0].id], ["m1"]);
  assert.equal(state.versions.find((v) => v.id === "i1").attributes.appStoreState, "WAITING_FOR_REVIEW");
});

test("the log never shows review detail values", async () => {
  const { state, client } = fakeAsc({
    versions: [ver("i1", "IOS", "1.0", "READY_FOR_SALE"), ver("m1", "MAC_OS", "1.2.0", "PREPARE_FOR_SUBMISSION")],
    builds: [build("b1", "MAC_OS", "1.2.0", "6.4", "VALID")],
    details: { i1: detail },
    locs: { default: [goodLoc(["APP_DESKTOP"])] },
  });
  state.currentVersion = "default";
  const d = deps();
  await runSubmit(client, opts(), d);
  assert.ok(!d.lines.join("\n").includes(SECRET));
  assert.match(d.lines.join("\n"), /review details copied/);
});

test("with no version for the platform, a new one is created", async () => {
  const { state, client } = fakeAsc({
    versions: [ver("i1", "IOS", "1.0", "READY_FOR_SALE")],
    builds: [build("b1", "IOS", "1.2.0", "6.4", "VALID")],
    details: { i1: detail },
    locs: { default: [goodLoc(["APP_IPHONE_67", "APP_IPAD_PRO_3GEN_129"])] },
  });
  state.currentVersion = "default";
  const r = await runSubmit(client, opts({ platform: "IOS" }), deps());
  assert.equal(r.status, "submitted");
  const created = state.versions.find((v) => v.id !== "i1");
  assert.equal(created.attributes.versionString, "1.2.0");
  assert.equal(created.attributes.platform, "IOS");
  assert.equal(created.build, "b1");
  assert.ok(state.calls.includes("POST /v1/appStoreVersions"));
});

test("an INVALID build stops the run", async () => {
  const { state, client } = fakeAsc({
    versions: [ver("m1", "MAC_OS", "1.0", "PREPARE_FOR_SUBMISSION")],
    builds: [build("b1", "MAC_OS", "1.2.0", "6.4", "INVALID")],
  });
  await assert.rejects(runSubmit(client, opts(), deps()), /6\.4 is INVALID/);
  assert.deepEqual(wrote(state), []);
});

test("a build that stays in processing times out after the wait", async () => {
  const { state, client } = fakeAsc({
    versions: [ver("m1", "MAC_OS", "1.0", "PREPARE_FOR_SUBMISSION")],
    builds: [build("b1", "MAC_OS", "1.2.0", "6.4", "PROCESSING")],
  });
  const d = deps();
  await assert.rejects(runSubmit(client, opts({ waitMinutes: 3 }), d), /no valid build for 1\.2\.0 after 3 minutes/);
  assert.equal(d.sleeps.length, 3);
  assert.ok(d.sleeps.every((ms) => ms === 60_000));
  assert.deepEqual(wrote(state), []);
});

test("a build that becomes VALID while the script waits is used", async () => {
  let polls = 0;
  const { state, client } = fakeAsc({
    versions: [ver("m1", "MAC_OS", "1.2.0", "PREPARE_FOR_SUBMISSION")],
    builds: [build("b1", "MAC_OS", "1.2.0", "6.4", "PROCESSING")],
    details: { m1: detail },
    locs: { default: [goodLoc(["APP_DESKTOP"])] },
    onBuildPoll: (s) => {
      if (++polls === 3) s.builds[0].attributes.processingState = "VALID";
    },
  });
  state.currentVersion = "default";
  const d = deps();
  const r = await runSubmit(client, opts(), d);
  assert.equal(r.status, "submitted");
  assert.equal(d.sleeps.length, 2);
});

test("with no build and a wait of 0, a pending run skips and changes nothing", async () => {
  const { state, client } = fakeAsc({ versions: [ver("i1", "IOS", "1.0", "READY_FOR_SALE")] });
  const r = await runSubmit(client, opts({ platform: "IOS", build: null, waitMinutes: 0, skipIfNoBuild: true }), deps());
  assert.equal(r.status, "no-build");
  assert.deepEqual(wrote(state), []);
});

test("a pending run uses the newest valid build with no build number", async () => {
  const { state, client } = fakeAsc({
    versions: [ver("m1", "MAC_OS", "1.2.0", "PREPARE_FOR_SUBMISSION")],
    builds: [build("b1", "MAC_OS", "1.2.0", "6.3", "VALID", "2026-11-28T08:00:00Z"), build("b2", "MAC_OS", "1.2.0", "6.4", "VALID", "2026-11-28T09:00:00Z")],
    details: { m1: detail },
    locs: { default: [goodLoc(["APP_DESKTOP"])] },
  });
  state.currentVersion = "default";
  const r = await runSubmit(client, opts({ build: null, waitMinutes: 0, skipIfNoBuild: true }), deps());
  assert.equal(r.status, "submitted");
  assert.equal(state.versions[0].build, "b2");
});

test("a macOS version with no review details and no iOS source stops the run", async () => {
  const { client } = fakeAsc({
    versions: [ver("m1", "MAC_OS", "1.2.0", "PREPARE_FOR_SUBMISSION")],
    builds: [build("b1", "MAC_OS", "1.2.0", "6.4", "VALID")],
    locs: { default: [goodLoc(["APP_DESKTOP"])] },
  });
  await assert.rejects(runSubmit(client, opts(), deps()), /no review details to copy/);
});

test("an incomplete listing stops the run before the submission", async () => {
  const { state, client } = fakeAsc({
    versions: [ver("m1", "MAC_OS", "1.2.0", "PREPARE_FOR_SUBMISSION")],
    builds: [build("b1", "MAC_OS", "1.2.0", "6.4", "VALID")],
    details: { m1: detail },
    locs: { default: [goodLoc([])] },
  });
  state.currentVersion = "default";
  await assert.rejects(runSubmit(client, opts(), deps()), /not complete.*APP_DESKTOP/);
  assert.ok(!state.calls.some((c) => c.includes("reviewSubmission")));
});

test("submit false attaches the build and sends nothing to App Review", async () => {
  const { state, client } = fakeAsc({
    versions: [ver("m1", "MAC_OS", "1.2.0", "PREPARE_FOR_SUBMISSION")],
    builds: [build("b1", "MAC_OS", "1.2.0", "6.4", "VALID")],
    details: { m1: detail },
    locs: { default: [goodLoc(["APP_DESKTOP"])] },
  });
  state.currentVersion = "default";
  const r = await runSubmit(client, opts({ submit: !autoSubmitOff("false") }), deps());
  assert.equal(r.status, "ready");
  assert.equal(state.versions[0].build, "b1");
  assert.ok(!state.calls.some((c) => c.includes("reviewSubmission")));
});

test("an open review submission is reused and an item is not added twice", async () => {
  const { state, client } = fakeAsc({
    versions: [ver("m1", "MAC_OS", "1.2.0", "PREPARE_FOR_SUBMISSION")],
    builds: [build("b1", "MAC_OS", "1.2.0", "6.4", "VALID")],
    details: { m1: detail },
    locs: { default: [goodLoc(["APP_DESKTOP"])] },
    submissions: [{ id: "rs1", attributes: { platform: "MAC_OS", state: "READY_FOR_REVIEW" } }],
    items: { rs1: ["m1"] },
  });
  state.currentVersion = "default";
  await runSubmit(client, opts(), deps());
  assert.equal(state.submissions.length, 1);
  assert.ok(!state.calls.includes("POST /v1/reviewSubmissionItems"));
  assert.equal(state.submissions[0].attributes.state, "WAITING_FOR_REVIEW");
});

test("plan tells what each platform needs", async () => {
  const { client } = fakeAsc({
    versions: [ver("i1", "IOS", "1.0", "WAITING_FOR_REVIEW"), ver("m1", "MAC_OS", "1.0", "PREPARE_FOR_SUBMISSION")],
  });
  assert.equal(await describePlan(client, APP, "IOS", "1.2.0"), "IOS: skip, 1.0 is waiting for review");
  assert.equal(await describePlan(client, APP, "MAC_OS", "1.2.0"), "MAC_OS: rename 1.0 → 1.2.0, wait for build, then submit");
});
