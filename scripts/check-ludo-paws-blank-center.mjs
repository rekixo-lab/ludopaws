import fs from "node:fs";
import assert from "node:assert/strict";

const root = "android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/";
const read = path => fs.readFileSync(path, "utf8");
const chrome = read(root + "LudoPawsBoardChromeView.kt");
const board = read(root + "LudoBoardView.kt");
const finish = read(root + "LudoPawsFxBoardGeometry.kt");
const yard = read(root + "LudoPawsYardHabitatRenderer.kt");
const classic = read("android/app/src/main/java/com/rekixo/ludopaws/feature/boards/content/classic/ClassicBoardRenderer.kt");

// Blank is intentional: the stock four-color finish triangles are the only
// CENTER artwork. Keep it empty until future one-component-at-a-time changes.
for (const path of [
  root + "LudoPawsCenterHouseRenderer.kt",
  root + "LudoPawsCenterHouseGeometry.kt",
  "android/app/src/test/java/com/rekixo/ludopaws/LudoPawsCenterHouseGeometryTest.kt",
  "scripts/check-ludo-paws-center-house.mjs",
]) assert.ok(!fs.existsSync(path), "Experimental house code was not cleaned: " + path);
assert.doesNotMatch(chrome, /LudoPawsCenterHouseRenderer|centerHouseRenderer|centerHouse/);
assert.match(chrome, /yardHabitatRenderer\.draw\(canvas, cell\)/,
  "Do not change the approved colored Yard design.");
assert.match(chrome, /footprintSource\?\.drawYardPawPrintsOverlay\(canvas, cell\)/);
assert.match(chrome, /footprintSource\?\.drawFinishRanksOverlay\(canvas, cell\)/);

// The underlying Ludo board still draws exactly four colored triangles,
// with the original 6..9 logical boundaries and the exact common midpoint.
assert.match(board, /private fun drawCenter\(/);
const centerStart = board.indexOf("    private fun drawCenter(");
const centerEnd = board.indexOf("    private fun triangle(", centerStart);
assert.ok(centerStart >= 0 && centerEnd > centerStart, "Base center drawing disappeared.");
const center = board.slice(centerStart, centerEnd);
for (const boundary of [
  "val left = axisBoundary(6, cell)",
  "val top = axisBoundary(6, cell)",
  "val right = axisBoundary(9, cell)",
  "val bottom = axisBoundary(9, cell)",
  "val cx = (left + right) / 2f",
  "val cy = (top + bottom) / 2f",
]) assert.ok(center.includes(boundary), "Original finish bound changed: " + boundary);
for (const team of ["RED", "GREEN", "YELLOW", "BLUE"]) {
  assert.ok(center.includes('colorFor("' + team + '")'),
    "Missing original colored finish triangle: " + team);
}
assert.equal((center.match(/\btriangle\s*\(/g) || []).length, 4,
  "Blank center must consist of exactly four original triangles.");
assert.match(classic, /private fun drawCenterDepth\(/,
  "Preserve the existing board finish surface.");
assert.match(classic, /axisBoundary\(6, cell\)/);
assert.match(classic, /axisBoundary\(9, cell\)/);

// Pawns remain on the authoritative finished-home cells.
for (const [team, point] of [
  ["RED", "cx - offset to cy"],
  ["GREEN", "cx to cy - offset"],
  ["YELLOW", "cx + offset to cy"],
  ["BLUE", "cx to cy + offset"],
]) assert.ok(finish.includes('"' + team + '" -> ' + point),
  "Finish pawn coordinates changed for " + team);
assert.match(yard, /private fun paintHome\(/,
  "Approved open colored yards must remain intact.");

console.log("Blank Ludo center verified: four original color triangles only, no house roof artwork, Yard and finish geometry preserved.");
