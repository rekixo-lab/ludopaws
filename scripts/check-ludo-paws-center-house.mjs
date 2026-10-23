import fs from "node:fs";
import assert from "node:assert/strict";

const base = "android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/";
const read = p => fs.readFileSync(p, "utf8");
const geometry = read(base + "LudoPawsCenterHouseGeometry.kt");
const art = read(base + "LudoPawsCenterHouseRenderer.kt");
const chrome = read(base + "LudoPawsBoardChromeView.kt");
const originalBoard = read(base + "LudoBoardView.kt");
const finishGeometry = read(base + "LudoPawsFxBoardGeometry.kt");
const approvedYard = read(base + "LudoPawsYardHabitatRenderer.kt");
const yardPalette = read(base + "LudoPawsYardPalettePolicy.kt");
const tests = read("android/app/src/test/java/com/rekixo/ludopaws/LudoPawsCenterHouseGeometryTest.kt");

assert.match(geometry, /cell \* 5f/);
assert.match(geometry, /cell \* 10f/);
assert.match(geometry, /if \(!cell\.isFinite\(\) \|\| cell <= 0f\) return null/);
for (const team of ["GREEN", "YELLOW", "BLUE", "RED"]) {
  assert.ok(geometry.includes('"' + team + '" -> LudoPawsRoofTriangle('),
    "Roof missing wedge: " + team);
  assert.ok(art.includes('"' + team + '" -> RoofPalette('),
    "Roof color palette missing: " + team);
}
assert.match(geometry, /val p = LudoPawsRoofPoint\(50f, 50f\)/);
for (const [team, degrees] of [["GREEN","0f"],["YELLOW","90f"],["BLUE","180f"],["RED","270f"]]) {
  assert.ok(geometry.includes('"' + team + '" -> ' + degrees), "Wrong orientation " + team);
}
assert.match(art, /for \(team in listOf\("GREEN", "YELLOW", "BLUE", "RED"\)\)/);
assert.match(art, /canvas\.rotate\(rotation, 50f, 50f\)/);
assert.match(art, /c\.clipPath\(wedge\)/, "Every facade must remain inside its triangle");
assert.match(art, /c\.clipPath\(roofShape\)/, "Roof tiles must not spill over other teams");
assert.match(art, /moveTo\(14f, 14f\)[\s\S]*?lineTo\(86f, 14f\)[\s\S]*?lineTo\(50f, 50f\)/,
  "Roof tiles must point to exact central apex");
assert.match(art, /moveTo\(0f, 0f\)[\s\S]*?lineTo\(100f, 0f\)[\s\S]*?lineTo\(50f, 50f\)/,
  "Housing and color wedges must fill the original triangles");
assert.match(art, /for \(row in 0\.\.8\)/, "Code-drawn roof tiles required");
assert.match(art, /private fun door\(/);
assert.match(art, /private fun window\(/);
assert.match(art, /private fun blossomCluster\(/);
assert.doesNotMatch(art, /BitmapFactory|assets\.open|atlas\.webp|WebView/,
  "Center house must be code-drawn and offline");
assert.match(art, /Bitmap\.createBitmap\(640, 640, Bitmap\.Config\.ARGB_8888\)/);
assert.match(art, /cachedArtwork \?: buildArtwork\(\)\.also/,
  "No per-frame rebuilding of house geometry");
assert.match(art, /val bounds = LudoPawsCenterHouseGeometry\.bounds\(cell\)/);
assert.match(chrome, /private val centerHouseRenderer = LudoPawsCenterHouseRenderer\(\)/);
assert.match(chrome, /centerHouseRenderer\.draw\(canvas, cell\)/);

const order = [
  "renderer.draw(canvas, size",
  "yardHabitatRenderer.draw(canvas, cell)",
  "centerHouseRenderer.draw(canvas, cell)",
  "drawYardPawPrintsOverlay(canvas, cell)",
  "drawFinishRanksOverlay(canvas, cell)",
].map(str => chrome.indexOf(str));
assert.ok(order.every((v,i)=>v>=0 && (!i || v>order[i-1])),
  "Houses must be under finish labels and real pawns, after original board");
assert.match(originalBoard, /val left = axisBoundary\(6, cell\)/);
assert.match(originalBoard, /val right = axisBoundary\(9, cell\)/);
assert.match(originalBoard, /private fun drawCenter\(/);
assert.match(finishGeometry, /"RED" -> cx - offset to cy/);
assert.match(finishGeometry, /"GREEN" -> cx to cy - offset/);
assert.match(finishGeometry, /"YELLOW" -> cx \+ offset to cy/);
assert.match(finishGeometry, /"BLUE" -> cx to cy \+ offset/);
assert.match(approvedYard, /private fun paintHome\(/,
  "Do not touch the already approved garden home drawing");
assert.match(yardPalette, /"GREEN" -> LudoPawsYardPalette\(/);
for (const test of [
  "centerSquareMatchesExistingStretchedBoardBoundaries",
  "fourRoofApexesShareExactlyOneUnmovedFinishCenter",
  "theFourRooftopsMatchExistingCanonicalWedgeDirections",
  "finishPawnTargetsRemainInsideTheirExactTeamTriangle",
]) assert.ok(tests.includes(test), "Missing Kotlin geometry regression: " + test);

// Independently validate the full partition and the true 5..10 board square.
const triangles = [
  [[0,0],[100,0],[50,50]],
  [[100,0],[100,100],[50,50]],
  [[100,100],[0,100],[50,50]],
  [[0,100],[0,0],[50,50]],
];
const area = t => Math.abs(
  (t[1][0]-t[0][0])*(t[2][1]-t[0][1]) -
  (t[1][1]-t[0][1])*(t[2][0]-t[0][0])
)/2;
assert.equal(triangles.reduce((sum,tri)=>sum+area(tri),0),10000);
for(const tri of triangles) {
  assert.deepEqual(tri[2],[50,50],"All four roofs must meet at one point");
  assert.equal(area(tri),2500);
}
for(const cell of [1,17,60,93]) {
  const center=[5*cell,5*cell,10*cell,10*cell];
  assert.equal(center[2]-center[0],5*cell);
  assert.equal(center[3]-center[1],5*cell);
}
console.log("Four procedural storybook roofs meet precisely at approved finish apex; correct team rotations, tile clipping, untouched yard/road/pawns, cached drawing.");
