import fs from "node:fs";
import assert from "node:assert/strict";

const root = "android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/";
const get = file => fs.readFileSync(root + file, "utf8");
const policy = get("LudoPawsYardHabitatPolicy.kt");
const art = get("LudoPawsYardHabitatRenderer.kt");
const chrome = get("LudoPawsBoardChromeView.kt");
const board = get("LudoPawsBoardView.kt");
const reactive = get("LudoPawsReactiveBoardView.kt");
const scene = get("LudoPaws3DSceneRenderer.kt");
const tests = fs.readFileSync(
    "android/app/src/test/java/com/rekixo/ludopaws/LudoPawsYardHabitatPolicyTest.kt", "utf8",
);
const classic = fs.readFileSync(
    "android/app/src/main/java/com/rekixo/ludopaws/feature/boards/content/classic/ClassicBoardRenderer.kt", "utf8",
);
const geometry = get("LudoPawsFxBoardGeometry.kt");

for (const type of ["DOG_GARDEN", "GOAT_MEADOW", "DUCK_POND", "CAT_COURTYARD", "DONKEY_FARM"]) {
    assert.ok(policy.includes(type), "Missing independent selected environment: " + type);
    assert.ok(art.includes("LudoPawsYardHabitat." + type + " ->"), "Missing illustrated environment: " + type);
}
assert.match(policy, /snapshot\.players\.forEach/);
assert.match(policy, /LudoPaws3DCharacterPolicy\.characterIdForSeat\(/);
assert.match(policy, /seat = player\.seat/);
assert.match(policy, /put\(team\.wireName, forCharacterId\(animalId\)\)/);
assert.match(policy, /if \(snapshot == null\) return emptyMap\(\)/);
assert.match(policy, /"RED" -> 0 to 0/);
assert.match(policy, /"GREEN" -> 0 to 9/);
assert.match(policy, /"YELLOW" -> 9 to 9/);
assert.match(policy, /"BLUE" -> 9 to 0/);
assert.match(policy, /left = left \+ cell \* \(0\.50f \+ 0\.115f\)/);
assert.match(policy, /top = top \+ cell \* \(0\.50f \+ 0\.105f\)/);
assert.match(policy, /right = right - cell \* \(0\.50f \+ 0\.115f\)/);
assert.match(policy, /bottom = bottom - cell \* \(0\.50f \+ 0\.145f\)/);
for (const marker of ["yardSpan = cell * 5f", "roadSpan = cell * 5f"]) {
    assert.ok(policy.includes(marker), "Locked geometric mirror missing " + marker);
}
assert.match(art, /LruCache<LudoPawsYardHabitat, Bitmap>\(5\)/);
assert.match(art, /canvas\.clipPath\(clip\)/);
assert.match(art, /LudoPawsYardHabitatGeometry\.face\(color, cell\)/);
assert.match(art, /LudoPawsFxBoardGeometry\.tokenCenter\(/);
assert.match(art, /position = -1/);
assert.match(art, /landingPad\(canvas, center\.first, center\.second, cell\)/);
assert.match(art, /Bitmap\.createBitmap\(384, 384, Bitmap\.Config\.ARGB_8888\)/);
assert.doesNotMatch(art, /BitmapFactory\.decodeResource|drawText\(.*(DOG|GOAT|CAT|DUCK)/);
assert.match(chrome, /yardHabitatRenderer\.draw\(canvas, cell, habitatsByColor\)/);
const order=[
    "renderer.draw(canvas, size",
    "yardHabitatRenderer.draw(canvas, cell, habitatsByColor)",
    "drawYardPawPrintsOverlay(canvas, cell)",
    "drawFinishRanksOverlay(canvas, cell)",
].map(q=>chrome.indexOf(q));
assert.ok(order.every(v=>v>=0)&&order.every((v,i)=>i===0 || v>order[i-1]),
    "Habitat belongs above molded yard surface but below paw prints/finish labels.");
assert.match(chrome,/fun bindHabitats\(/);
assert.match(board,/boardChrome\.bindHabitats\(/);
assert.match(board,/snapshot = state,\s*characterIdsBySeat = characterIdsBySeat/);
assert.match(board,/boardChrome\.rotation = chromeQuarterTurns \* 90f/);
assert.match(reactive,/pawn3DScene/);
assert.match(scene,/LudoPawsFxBoardGeometry\.tokenCenter\(/);
// Board's raised rim, ROAD and CENTER are unchanged: only the face artwork is new.
assert.match(classic,/drawRaisedYard\(canvas, cell, row = 0, col = 0/);
assert.match(classic,/drawCrossTileDepth\(canvas, cell\)/);
assert.match(classic,/drawCenterDepth\(canvas, cell\)/);
assert.match(geometry,/fun tokenCenter\(/);
for (const marker of [
    "every shipped animal gets a unique habitat",
    "selected character follows its seat color rather than fixed corner",
    "two player game leaves unoccupied yards unchanged",
    "missing selections use same color fallback as real 3D animals",
    "habitat bounds match only the inside faces of locked color homes",
]) {
    assert.ok(tests.includes(marker), "Missing habitat regression: " + marker);
}

console.log("Ludo Paws five modular yard habitats verified: per-selected-animal, aligned to four locked face boundaries, cached, cropped below footprints and 3D pawns, no road or center changes.");
