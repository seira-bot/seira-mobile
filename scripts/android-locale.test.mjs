import assert from "node:assert/strict";
import { test } from "node:test";

import { codeFor, decodeAndroid, encodeAndroid, folderFor, formatArguments, parseResourceFile } from "./android-locale.mjs";

test("escaping round-trips quotes, entities and leading references", () => {
  for (const logical of [`l'ordinateur "ok"`, "@home", "?help", "a & b <c>", "line\nbreak", "100%% sure"]) {
    assert.equal(decodeAndroid(encodeAndroid(logical)), logical);
  }
  assert.equal(encodeAndroid("@home"), "\\@home");
  assert.equal(encodeAndroid("l'eau"), "l\\'eau");
});

test("format arguments ignore a literal percent sign", () => {
  assert.deepEqual(formatArguments("%1$s has %2$d bots, 100%% sure"), ["%1$s", "%2$d"]);
});

test("folders and codes map both ways", () => {
  assert.equal(folderFor("pt-br"), "values-pt-rBR");
  assert.equal(folderFor("zh-hans"), "values-b+zh+Hans");
  assert.equal(folderFor("de"), "values-de");
  assert.equal(codeFor("values-pt-rBR"), "pt-BR");
  assert.equal(codeFor("values-b+zh+Hant"), "zh-Hant");
});

test("plurals and untranslatable strings parse in order", () => {
  const entries = parseResourceFile(`<resources>
    <string name="app_name" translatable="false">Seira</string>
    <plurals name="n"><item quantity="one">%1$d row</item><item quantity="other">%1$d rows</item></plurals>
    <string name="s">Couldn\\'t</string>
  </resources>`);
  assert.deepEqual(entries.map((entry) => [entry.name, entry.kind, entry.translatable]), [
    ["app_name", "string", false],
    ["n", "plural", true],
    ["s", "string", true],
  ]);
  assert.equal(entries[2].text, "Couldn't");
  assert.equal(entries[1].quantities.other, "%1$d rows");
});
