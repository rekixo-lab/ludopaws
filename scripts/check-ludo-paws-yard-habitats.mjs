import fs from "node:fs";
import assert from "node:assert/strict";

const root = "android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/";
const source = file => fs.readFileSync(root + file, "utf8");
const geometry = source("LudoPawsYardGeometry.kt");
const palette = source("LudoPawsYardPalettePolicy.kt");
const art = source("LudoPawsYardHabitatRenderer.kt");
const chrome = source("LudoPawsBoardChromeView.kt");
const board = source("LudoPawsBoardView.kt");
const scene = source("LudoPaws3DSceneRenderer.kt");
const reactive = source("LudoPawsReactiveBoardView.kt");
const geomTests = fs.readFileSync("android/app/src/test/java/com/rekixo/ludopaws/LudoPawsYardGeometryTest.kt", "utf8");
const paletteTests = fs.readFileSync("android/app/src/test/java/com/rekixo/ludopaws/LudoPawsYardPalettePolicyTest.kt", "utf8");
const classic = fs.readFileSync("android/app/src/main/java/com/rekixo/ludopaws/feature/boards/content/classic/ClassicBoardRenderer.kt", "utf8");

for (const stale of [
  root + "LudoPawsYardHabitatPolicy.kt",
  "android/app/src/test/java/com/rekixo/ludopaws/LudoPawsYardHabitatPolicyTest.kt",
  "android/app/src/main/assets/yard_habitats/atlas.webp",
]) assert.ok(!fs.existsSync(stale), "Obsolete habitat code or atlas still present: " + stale);

assert.doesNotMatch(art, /BitmapFactory|decodeStream|assets\.open|atlas\.webp|drawPremiumHabitat/);
assert.match(art, /private fun paintHome\(/);
assert.match(art, /Bitmap\.createBitmap\(512, 512, Bitmap\.Config\.ARGB_8888\)/);
assert.match(art, /LruCache<String, Bitmap>\(4\)/);
assert.match(art, /fun draw\(canvas: Canvas, cell: Float\)/);
assert.match(art, /canvas\.drawBitmap\(cachedBitmap\(color, palette\), null, rect, imagePaint\)/);
assert.match(art, /LudoPawsYardGeometry\.face\(color, cell\)/);
assert.doesNotMatch(art, /habitatsByColor|landingPad\(|LudoPawsFxBoardGeometry\.tokenCenter\(/);
assert.match(art, /for \(color in listOf\("RED", "GREEN", "YELLOW", "BLUE"\)\)/);
assert.match(art, /RectF\(14\.0f, 14\.1f, 86\.0f, 85\.8f\)/);
for (const detail of [
  "cornerTimber(c)", "cornerFoliage(c, palette)", "flower(c, x, y, size)",
  "rock(c, 45f, 5.6f, 1f)", "palette.sandTop, palette.sandBottom",
  "rect(c, 0f, 0f, 100f, 100f, palette.rimDark)",
]) assert.ok(art.includes(detail), "Approved clean yard detail missing: " + detail);

for(const color of ["RED","GREEN","YELLOW","BLUE"]) {
  assert.ok(geometry.includes('"' + color + '" ->'), "Home location missing: " + color);
  assert.ok(palette.includes('"' + color + '" -> LudoPawsYardPalette('), "Palette missing: " + color);
}
for(const bound of ["left = left,","top = top,","right = right,","bottom = bottom,"]) {
  assert.ok(geometry.includes(bound), "Full 5x5 yard geometry changed: " + bound);
}
assert.doesNotMatch(geometry, /MatchSnapshot|characterIdsBySeat|LudoPawsYardHabitatPolicy/);
assert.doesNotMatch(chrome, /bindHabitats|habitatsByColor/);
assert.doesNotMatch(board, /boardChrome\.bindHabitats\(/);
assert.match(chrome, /yardHabitatRenderer\.draw\(canvas, cell\)/);
assert.match(board, /boardChrome\.rotation = chromeQuarterTurns \* 90f/);
assert.match(reactive, /pawn3DScene/);
assert.match(scene, /LudoPawsFxBoardGeometry\.tokenCenter\(/);
const drawOrder=[
  "renderer.draw(canvas, size", "yardHabitatRenderer.draw(canvas, cell)",
  "drawYardPawPrintsOverlay(canvas, cell)", "drawFinishRanksOverlay(canvas, cell)",
].map(x=>chrome.indexOf(x));
assert.ok(drawOrder.every((v,i)=>v>=0 && (!i || v>drawOrder[i-1])), "Art draw order must remain unchanged");
assert.match(classic, /drawCrossTileDepth\(canvas, cell\)/);
assert.match(classic, /drawCenterDepth\(canvas, cell\)/);
assert.match(geomTests, /clean yards fill all four complete five-cell quadrants without color strips/);
assert.match(geomTests, /LudoPawsFxBoardGeometry\.tokenCenter\(color, tokenIndex, -1, cell\)/);
assert.match(paletteTests, /allFourTeamPalettesAreDistinctAndSupported/);
assert.match(paletteTests, /groundStaysPaleAndClearUnderEnlargedCharacters/);
assert.match(paletteTests, /teamColorPaletteIsIndependentOfCharacterSpecies/);
console.log("Approved procedural yards retained: old pet habitat mapping and WebP removed, true slots plus road/center protected.");
