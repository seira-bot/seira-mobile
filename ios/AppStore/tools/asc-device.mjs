#!/usr/bin/env node
// Xcode automatic signing makes a Mac Catalyst development profile before it
// archives, and Apple refuses that profile while the team has no Mac device
// ("Your team has no devices from which to generate a provisioning profile").
// This registers the build Mac once, when the team has no enabled Mac.
//
//   node asc-device.mjs ensure-mac --udid <provisioning UDID> [--name <name>]
//
// Reads ASC_KEY_ID, ASC_ISSUER_ID and ASC_KEY_PATH. The key needs the Admin role.
import { pathToFileURL } from "node:url";
import { createClient } from "./asc-api.mjs";

export async function ensureMac(client, { udid, name }, log = console.log) {
  const macs = await client.listAll("/v1/devices?filter[platform]=MAC_OS&filter[status]=ENABLED&limit=200");
  if (macs.length) {
    log(`The team has ${macs.length} enabled Mac device(s). Nothing to register.`);
    return { status: "present", count: macs.length };
  }
  if (!udid) throw new Error("The team has no enabled Mac device, and no UDID was given.");
  await client.api("POST", "/v1/devices", {
    data: { type: "devices", attributes: { name, platform: "MAC_OS", udid } },
  });
  log(`Registered the build Mac as "${name}".`);
  return { status: "registered" };
}

function parseArgs(argv) {
  const [command, ...rest] = argv;
  const opts = { command, name: "CI Mac for Mac Catalyst signing" };
  for (let i = 0; i < rest.length; i += 2) {
    const key = rest[i].replace(/^--/, "");
    opts[key] = rest[i + 1];
  }
  return opts;
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const opts = parseArgs(process.argv.slice(2));
  if (opts.command !== "ensure-mac") {
    console.error("Usage: node asc-device.mjs ensure-mac --udid <provisioning UDID> [--name <name>]");
    process.exit(2);
  }
  try {
    await ensureMac(createClient(), opts);
  } catch (e) {
    console.error(`::error::${e.message}`);
    process.exit(1);
  }
}
