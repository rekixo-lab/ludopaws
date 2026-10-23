import fs from "node:fs";
import assert from "node:assert/strict";

const root = "android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/";
const source = file => fs.readFileSync(root + file, "utf8");
const policy = source("LudoPawsYardHabitatPolicy.kt");
const palette = source("LudoPawsYardPalettePolicy.kt");
const art = source("LudoPawsYardHabitatRenderer.kt");
const chrome = source("LudoPawsBoardChromeView.kt");
const board = source("LudoPawsBoardView.kt");
const reactive = source("LudoPawsReactiveBoardView.kt");
const scene = source("LudoPaws3DSceneRenderer.kt");
const tests = fs.readFileSync(
    "android/app/src/test/java/com/rekixo/ludopaws/LudoPawsYardHabitatPolicyTest.kt", "utf8",
);
const paletteTests = fs.readFileSync(
    "android/app/src/test/java/com/rekixo/ludopaws/LudoPawsYardPalettePolicyTest.kt", "utf8",
);
const classic = fs.readFileSync(
    "android/app/src/main/java/com/rekixo/ludopaws/feature/boards/content/classic/ClassicBoardRenderer.kt", "utf8",
);
const geometry = source("LudoPawsFxBoardGeometry.kt");

// Entirely offline Canvas-based art. The previous high-noise atlas is gone.
assert.ok(!fs.existsSync("android/app/src/main/assets/yard_habitats/atlas.webp"),
    "No noisy old habitat image should ship in the APK.");
assert.doesNotMatch(art, /BitmapFactory|decodeStream|assets\.open|atlas\.webp|drawPremiumHabitat|drawOpenPlayArea/);
assert.match(art, /private fun paintHome\(/, "Clean art is drawn in Kotlin");
assert.match(art, /Bitmap\.createBitmap\(512, 512, Bitmap\.Config\.ARGB_8888\)/);
assert.match(art, /LruCache<String, Bitmap>\(4\)/, "Each team uses cached vector artwork");
assert.match(art, /bitmapCache\.get\(color\)/);
assert.match(art, /canvas\.drawBitmap\(cachedBitmap\(color, palette\), null, rect, imagePaint\)/);

for (const color of ["RED", "GREEN", "YELLOW", "BLUE"]) {
    assert.ok(palette.includes('"' + color + '" -> LudoPawsYardPalette('),
        "Missing team color palette: " + color);
    assert.ok(art.includes('"' + color + '"'), "Missing quadrant: " + color);
}
assert.match(art, /for \(color in listOf\("RED", "GREEN", "YELLOW", "BLUE"\)\)/,
    "All four matching homes render even in two-player matches.");
assert.match(art, /LudoPawsYardPalettePolicy\.forTeam\(color\)/);
assert.match(art, /canvas\.clipRect\(rect\)/);
assert.doesNotMatch(art, /addRoundRect\(rect,/);
assert.match(art, /rect\(c, 0f, 0f, 100f, 100f, palette\.rimDark\)/);
assert.match(art, /roundRect\(c, 0\.7f, 0\.7f, 99\.3f, 99\.3f/,
    "Outer colored border must fill the house.");
assert.match(art, /RectF\(14\.0f, 14\.1f, 86\.0f, 85\.8f\)/,
    "Approximately 72 percent open sand ground is required.");
assert.match(art, /palette\.sandTop, palette\.sandBottom/);
assert.match(art, /repeat\(35\)/, "Only faint central earthen texture.");
assert.match(art, /cornerTimber\(c\)/);
assert.match(art, /cornerFoliage\(c, palette\)/);
assert.match(art, /flower\(c, x, y, size\)/);
assert.match(art, /rock\(c, 45f, 5\.6f, 1f\)/);
assert.doesNotMatch(art, /landingPad\(|LudoPawsFxBoardGeometry\.tokenCenter\(/,
    "No fake white animal pedestals or per-slot scenery.");
assert.doesNotMatch(art, /"DOG_GARDEN" ->|LudoPawsYardHabitat\.DUCK_POND ->/,
    "Same design regardless of selected species.");

for (const color of ["RED", "GREEN", "YELLOW", "BLUE"]) {
    assert.ok(policy.includes('"' + color + '" ->'), "Home mapping missing " + color);
}
for (const marker of ["left = left,", "top = top,", "right = right,", "bottom = bottom,"]) {
    assert.ok(policy.includes(marker), "Full 5x5 yard bounds must remain unchanged: " + marker);
}
assert.match(policy, /snapshot\.players\.forEach/);
assert.match(policy, /LudoPaws3DCharacterPolicy\.characterIdForSeat\(/);
assert.match(policy, /put\(team\.wireName, forCharacterId\(animalId\)\)/);
assert.match(chrome, /LudoPawsYardHabitatRenderer\(\)/);
assert.match(chrome, /yardHabitatRenderer\.draw\(canvas, cell, habitatsByColor\)/);
const order = [
    "renderer.draw(canvas, size",
    "yardHabitatRenderer.draw(canvas, cell, habitatsByColor)",
    "drawYardPawPrintsOverlay(canvas, cell)",
    "drawFinishRanksOverlay(canvas, cell)",
].map(part => chrome.indexOf(part));
assert.ok(order.every(n => n >= 0) && order.every((n, i) => i === 0 || n > order[i-1]),
    "Clean scenery must be below foot markers and real 3D animals.");
assert.match(board, /boardChrome\.bindHabitats\(/);
assert.match(board, /boardChrome\.rotation = chromeQuarterTurns \* 90f/);
assert.match(reactive, /pawn3DScene/);
assert.match(scene, /LudoPawsFxBoardGeometry\.tokenCenter\(/);
assert.match(classic, /drawCrossTileDepth\(canvas, cell\)/);
assert.match(classic, /drawCenterDepth\(canvas, cell\)/);
assert.match(geometry, /fun tokenCenter\(/);
for (const name of [
    "habitats fill all four complete five-cell yard quadrants without color strips",
    "two player game leaves unoccupied yards unchanged",
]) {
    assert.ok(tests.includes(name), "Legacy placement tests missing: " + name);
}
for (const name of [
    "allFourTeamPalettesAreDistinctAndSupported",
    "groundStaysPaleAndClearUnderEnlargedCharacters",
    "teamColorPaletteIsIndependentOfCharacterSpecies",
]) {
    assert.ok(paletteTests.includes(name), "Palette tests missing: " + name);
}
console.log("Clean code-drawn four-color yards: open sand centers, spare timber/flower/leaf border, no WebP atlas or oval pads; paths and Center untouched.");
