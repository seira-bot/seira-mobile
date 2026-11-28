#!/usr/bin/env node
// Configures android/app with each kind of release signing environment and
// checks that the build accepts or refuses it. A refused case must stop the
// build, so it can never give an unsigned or wrongly signed release.
//
//   node scripts/check-release-signing.mjs [extra Gradle arguments]
//
// It needs a JDK and the Android SDK, as a Gradle build does.
import { spawnSync } from "node:child_process";
import { existsSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const android = join(dirname(fileURLToPath(import.meta.url)), "..", "android");

if (existsSync(join(android, "keystore.properties"))) {
  console.error("check-release-signing: android/keystore.properties exists. Move it away, then run this check again.");
  process.exit(2);
}

const scratch = mkdtempSync(join(tmpdir(), "seira-signing-"));
const keystore = join(scratch, "placeholder.p12");
writeFileSync(keystore, "");

const seira = {
  SEIRA_KEYSTORE_FILE: keystore,
  SEIRA_KEYSTORE_PASSWORD: "placeholder",
  SEIRA_KEY_ALIAS: "upload",
};
const retired = {
  OPENMAUSBOT_KEYSTORE_FILE: keystore,
  OPENMAUSBOT_KEYSTORE_PASSWORD: "placeholder",
  OPENMAUSBOT_KEY_ALIAS: "upload",
};

const cases = [
  { name: "no signing material", env: {}, accept: true },
  { name: "complete SEIRA_ set", env: seira, accept: true },
  { name: "partial SEIRA_ set", env: { SEIRA_KEYSTORE_FILE: keystore }, refuse: /half-configured/ },
  { name: "only the OPENMAUSBOT_ set", env: retired, refuse: /were renamed/ },
  { name: "one OPENMAUSBOT_ name", env: { OPENMAUSBOT_KEY_PASSWORD: "placeholder" }, refuse: /were renamed/ },
  { name: "both sets", env: { ...seira, ...retired }, refuse: /were renamed/ },
];

const base = Object.fromEntries(
  Object.entries(process.env).filter(([key]) => !/^(?:SEIRA|OPENMAUSBOT)_KEY/.test(key)),
);
const gradleArgs = ["--no-daemon", "-q", ...process.argv.slice(2), ":app:help"];

let failed = 0;
try {
  for (const c of cases) {
    const run = spawnSync("./gradlew", gradleArgs, {
      cwd: android,
      env: { ...base, ...c.env },
      encoding: "utf8",
      maxBuffer: 1 << 26,
    });
    const output = `${run.stdout}\n${run.stderr}`;
    const ok = c.accept ? run.status === 0 : run.status !== 0 && c.refuse.test(output);
    console.log(`${ok ? "pass" : "FAIL"}  ${c.name}: exit ${run.status}`);
    if (!ok) {
      failed += 1;
      console.log(output.split("\n").filter((line) => line.trim()).slice(-15).join("\n"));
    }
  }
} finally {
  rmSync(scratch, { recursive: true, force: true });
}

process.exit(failed ? 1 : 0);
