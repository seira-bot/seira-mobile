import assert from "node:assert/strict";
import { test } from "node:test";

import { checkLine, checkPath } from "./check-legacy-names.mjs";

test("current names pass", () => {
  assert.deepEqual(checkLine("android/app/A.kt", "package bot.seira.companion\nclass SeiraApp OpenMausBot"), []);
  assert.deepEqual(checkPath("android/app/src/main/kotlin/bot/seira/companion/A.kt"), []);
});

test("legacy names fail", () => {
  assert.equal(checkLine("android/app/A.kt", "import com.openmausbot.companion.X").length, 1);
  assert.equal(checkLine("android/app/A.kt", "class OpenMausApp").length, 1);
  assert.ok(checkLine("android/app/themes.xml", 'parent="Theme.OpenMausMobile"').length >= 1);
  assert.equal(checkPath("android/app/src/main/kotlin/com/openmausbot/companion/A.kt").length, 1);
});

test("the launcher alias in the manifest is allowed", () => {
  const line = '<activity-alias android:name="com.openmausbot.companion.MainActivity"';
  assert.deepEqual(checkLine("android/app/src/main/AndroidManifest.xml", line), []);
  assert.equal(checkLine("android/app/A.kt", line).length, 1);
  assert.deepEqual(checkLine("android/app/src/test/kotlin/bot/seira/companion/NativeSurfacesManifestTest.kt", 'component("activity-alias", "com.openmausbot.companion.MainActivity")'), []);
});
