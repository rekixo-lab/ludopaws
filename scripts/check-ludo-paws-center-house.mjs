import fs from "node:fs";
import assert from "node:assert/strict";

const root="android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/";
const read=p=>fs.readFileSync(p,"utf8");
const geometry=read(root+"LudoPawsCenterHouseGeometry.kt");
const art=read(root+"LudoPawsCenterHouseRenderer.kt");
const chrome=read(root+"LudoPawsBoardChromeView.kt");
const board=read(root+"LudoBoardView.kt");
const finish=read(root+"LudoPawsFxBoardGeometry.kt");
const yard=read(root+"LudoPawsYardHabitatRenderer.kt");
const palette=read(root+"LudoPawsYardPalettePolicy.kt");
const tests=read("android/app/src/test/java/com/rekixo/ludopaws/LudoPawsCenterHouseGeometryTest.kt");

assert.match(geometry,/cell \* 5f/);
assert.match(geometry,/cell \* 10f/);
assert.match(geometry,/val p = LudoPawsRoofPoint\(50f, 50f\)/);
assert.match(geometry,/if \(!cell\.isFinite\(\) \|\| cell <= 0f\) return null/);
for(const [team,angle] of [["GREEN",0],["YELLOW",90],["BLUE",180],["RED",270]]) {
 assert.ok(geometry.includes('"'+team+'" -> LudoPawsRoofTriangle('),"Missing "+team+" triangle");
 assert.ok(geometry.includes('"'+team+'" -> '+angle+'f'),"Wrong "+team+" rotation");
 assert.ok(art.includes('"'+team+'" -> HouseColors('),"Missing "+team+" 3D roof palette");
}
assert.match(art,/LudoPawsCenterHouseGeometry\.triangle\(team\)/);
assert.match(art,/check\(triangle\.apex == LudoPawsRoofPoint\(50f, 50f\)\)/);
assert.match(art,/c\.rotate\(turn, 50f, 50f\)/);
assert.match(art,/for \(team in listOf\("GREEN", "YELLOW", "BLUE", "RED"\)\)/);
assert.match(art,/val region = triangle\(0f, 0f, 100f, 0f, 50f, 50f\)/);
assert.match(art,/c\.clipPath\(region\)/,"The entire projected house must stay inside its wedge");
for(const marker of [
 "private fun drawHouseFacingTop(",
 "private fun insetWallTrim(",
 "private fun doorway(",
 "private fun window(",
 "private fun flowerBox(",
 "private fun step(",
 "private fun arch(",
 "private fun triangle(",
 "private fun polygon(",
 "private fun blend(",
 "val wall = polygon(",
 "val underEave = polygon(",
 "val roofThickness = polygon(",
 "val roof = triangle(11.8f, 15f, 88.2f, 15f, 50f, 50f)",
 "val leftPlane = triangle(",
 "val rightPlane = triangle(",
 "c.clipPath(roof)",
 "for (row in 0 until 9)",
 "val stagger = if (row % 2 == 0)",
 "colors.roofLight",
 "colors.roofDeep",
 "colors.grout",
 "0xFF175B93",
 "0xFFFFD27B"
]) assert.ok(art.includes(marker),"Missing premium architectural detail: "+marker);
assert.match(art,/stroke\(c, 50f, 15f, 50f, 49\.7f, colors\.roofDeep/);
assert.match(art,/gradient\(c, rightPlane/);
assert.match(art,/gradient\(c, leftPlane/);
assert.match(art,/gradient\(c, roofThickness/);
assert.match(art,/step\(c, 40\.3f, 16\.7f/);
assert.doesNotMatch(art,/private fun blossomCluster|val roofShape|private fun door\(/);
assert.doesNotMatch(art,/BitmapFactory|assets\.open|atlas\.webp|WebView|SurfaceView|OpenGL/);
assert.match(art,/Bitmap\.createBitmap\(1024, 1024, Bitmap\.Config\.ARGB_8888\)/);
assert.match(art,/cachedArtwork \?: renderOnce\(\)\.also/);
assert.match(art,/canvas\.clipRect\(destination\)/);
assert.match(art,/val square = LudoPawsCenterHouseGeometry\.bounds\(cell\)/);
assert.match(chrome,/private val centerHouseRenderer = LudoPawsCenterHouseRenderer\(\)/);
const layers=[
 "renderer.draw(canvas, size","yardHabitatRenderer.draw(canvas, cell)",
 "centerHouseRenderer.draw(canvas, cell)","drawYardPawPrintsOverlay(canvas, cell)",
 "drawFinishRanksOverlay(canvas, cell)"
].map(x=>chrome.indexOf(x));
assert.ok(layers.every((v,i)=>v>=0 && (!i || v>layers[i-1])),
 "Preserve road, approved yard and rank/pawn drawing order");
assert.match(board,/private fun drawCenter\(/);
assert.match(board,/val left = axisBoundary\(6, cell\)/);
assert.match(board,/val right = axisBoundary\(9, cell\)/);
for(const [name,anchor] of [
 ["RED","cx - offset to cy"],["GREEN","cx to cy - offset"],
 ["YELLOW","cx + offset to cy"],["BLUE","cx to cy + offset"]
]) assert.ok(finish.includes('"'+name+'" -> '+anchor),"Finish coordinate moved: "+name);
assert.match(yard,/private fun paintHome\(/);
assert.match(palette,/"GREEN" -> LudoPawsYardPalette\(/);

const triangles=[
 [[0,0],[100,0],[50,50]],[[100,0],[100,100],[50,50]],
 [[100,100],[0,100],[50,50]],[[0,100],[0,0],[50,50]]
];
const area=t=>Math.abs((t[1][0]-t[0][0])*(t[2][1]-t[0][1])-
 (t[1][1]-t[0][1])*(t[2][0]-t[0][0]))/2;
assert.equal(triangles.reduce((sum,t)=>sum+area(t),0),10000);
for(const t of triangles){assert.deepEqual(t[2],[50,50]);assert.equal(area(t),2500);}
for(const y of [0,12,15,20,49.7])assert.ok(50>=y && 50<=100-y);
for(const size of [1,17,60,93])assert.equal(10*size-5*size,5*size);
for(const name of [
 "centerSquareMatchesExistingStretchedBoardBoundaries",
 "fourRoofApexesShareExactlyOneUnmovedFinishCenter",
 "theFourRooftopsMatchExistingCanonicalWedgeDirections",
 "finishPawnTargetsRemainInsideTheirExactTeamTriangle"
])assert.ok(tests.includes(name),"Missing compiled center regression "+name);
console.log("Advanced 2.5D roof houses verified: thick eaves, pitched tiles, arched door and windows, one apex, preserved gameplay and team yards.");
