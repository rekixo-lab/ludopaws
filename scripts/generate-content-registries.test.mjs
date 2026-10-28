import { test } from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),"..");
test("manifest matches generated Kotlin sources and declarations",()=>{
  assert.match(execFileSync(process.execPath,["scripts/generate-content-registries.mjs","--check"],
    {cwd:root,encoding:"utf8"}),/manifest validated/);
});
test("manifest covers all supported classic compatibility IDs",()=>{
 const manifest=JSON.parse(execFileSync(process.execPath,["-e",
  "console.log(require('fs').readFileSync('content/content-manifest.json','utf8'))"],
  {cwd:root,encoding:"utf8"}));
 assert.equal(manifest.schemaVersion,1);
 assert.equal(manifest.characters.find(x=>x.id==="donkey").audio.voiceOwner,null);
 assert.equal(manifest.characters.find(x=>x.id==="goat_lumi").audio.voiceOwner,"goat");
 assert.ok(manifest.boards.some(x=>x.id==="board_classic"));
 assert.ok(manifest.dice.some(x=>x.id==="dice_classic"));
});
