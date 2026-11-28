import assert from "node:assert/strict";
import { test } from "node:test";
import { ensureMac } from "./asc-device.mjs";

function fakeClient(macs) {
  const calls = [];
  return {
    calls,
    listAll: async (path) => {
      calls.push(["GET", path]);
      return macs;
    },
    api: async (method, path, body) => {
      calls.push([method, path, body]);
      return { data: { id: "D1" } };
    },
  };
}

test("an enabled Mac exists: nothing is registered", async () => {
  const client = fakeClient([{ id: "M1" }]);
  const result = await ensureMac(client, { udid: "U1", name: "CI" }, () => {});
  assert.equal(result.status, "present");
  assert.equal(client.calls.filter(([m]) => m === "POST").length, 0);
  assert.match(client.calls[0][1], /filter\[platform\]=MAC_OS/);
  assert.match(client.calls[0][1], /filter\[status\]=ENABLED/);
});

test("no enabled Mac: the build Mac is registered", async () => {
  const client = fakeClient([]);
  const result = await ensureMac(client, { udid: "U1", name: "CI" }, () => {});
  assert.equal(result.status, "registered");
  const post = client.calls.find(([m]) => m === "POST");
  assert.equal(post[1], "/v1/devices");
  assert.deepEqual(post[2].data.attributes, { name: "CI", platform: "MAC_OS", udid: "U1" });
});

test("no enabled Mac and no UDID: it fails and registers nothing", async () => {
  const client = fakeClient([]);
  await assert.rejects(ensureMac(client, { udid: "", name: "CI" }, () => {}), /no UDID/);
  assert.equal(client.calls.filter(([m]) => m === "POST").length, 0);
});
