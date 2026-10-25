import fs from "node:fs";

const read = (path) => fs.readFileSync(path, "utf8");
const requireText = (source, token, message) => {
  if (!source.includes(token)) {
    throw new Error(message);
  }
};
const requireAbsent = (path, message) => {
  if (fs.existsSync(path)) {
    throw new Error(message);
  }
};

const boardPath =
  "android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/LudoPawsBoardView.kt";
const canonicalBoardPath =
  "android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/LudoBoardView.kt";
const boardChromePath =
  "android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/LudoPawsBoardChromeView.kt";
const footprintPolicyPath =
  "android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/LudoPawsYardFootprintPolicy.kt";
const footprintTestsPath =
  "android/app/src/test/java/com/rekixo/ludopaws/LudoPawsYardFootprintPolicyTest.kt";
const reactiveBoardPath =
  "android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/LudoPawsReactiveBoardView.kt";
const scenePath =
  "android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/LudoPaws3DSceneView.kt";
const rendererPath =
  "android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/LudoPaws3DSceneRenderer.kt";
const policyPath =
  "android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/LudoPaws3DCharacterPolicy.kt";
const cadencePolicyPath =
  "android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/LudoPaws3DRenderCadencePolicy.kt";
const layoutPath =
  "android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/LudoPawsPawnLayout.kt";
const activityPath =
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/OfflineGameActivity.kt";
const screenPath =
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/gameplay/OfflineGameplayScreenUi.kt";
const controlsPath =
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/gameplay/OfflineGameplayControlsUi.kt";
const characterStatePath =
  "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/presentation/state/OfflineCharacterPresentationState.kt";
const testsPath =
  "android/app/src/test/java/com/rekixo/ludopaws/LudoPawsPawnLayoutTest.kt";
const policyTestsPath =
  "android/app/src/test/java/com/rekixo/ludopaws/LudoPaws3DCharacterPolicyTest.kt";
const cadenceTestsPath =
  "android/app/src/test/java/com/rekixo/ludopaws/LudoPaws3DRenderCadencePolicyTest.kt";

const board = read(boardPath);
const canonicalBoard = read(canonicalBoardPath);
const boardChrome = read(boardChromePath) + "\n" + read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/boards/content/classic/ClassicBoardRenderer.kt",
);
const footprintPolicy = read(footprintPolicyPath);
const footprintTests = read(footprintTestsPath);
const reactiveBoard = read(reactiveBoardPath);
const scene = read(scenePath);
const renderer = read(rendererPath);
const visualLayoutTests = read("android/app/src/test/java/com/rekixo/ludopaws/LudoPawsPawnLayoutVisualPolicyTest.kt");
const policy = read(policyPath);
const cadencePolicy = read(cadencePolicyPath);
const layout = read(layoutPath);
const activity = read(activityPath);
const screen = read(screenPath);
const controls = read(controlsPath);
const characterState = read(characterStatePath);
const tests = read(testsPath);
const policyTests = read(policyTestsPath);
const cadenceTests = read(cadenceTestsPath);

for (const [token, message] of [
  ["class LudoPawsBoardView", "Production board shell is missing."],
  ["LudoBoardView(context)", "Production shell must keep the authoritative LudoBoardView underneath."],
  ["density(380f)", "Approved 380dp board-size lock must remain intact."],
]) {
  requireText(board, token, message);
}

for (const token of [
  "setClassicTokenDrawingEnabled(false)",
  "setLudoPawsYardFootprintsEnabled(true)",
  "boardSurface.setLudoPawsYardFootprintsEnabled(!visible)",
  "baseBoard.setLudoPawsYardFootprintsEnabled(visible)",
  "boardSurface.bind(",
  "state = state",
  "characterIdsBySeat = characterIdsBySeat",
]) {
  requireText(
    board,
    token,
    `Production Ludo Paws board is missing footprint state wiring: ${token}`,
  );
}

for (const token of [
  "LudoPaws3DSceneView",
  "LudoPawsGameFxOverlayView",
  "pawn3DScene.bind(",
  "characterIdsBySeat = characterIdsBySeat",
  "board.setClassicPawnFallbackVisible(false)",
  "onOperationalChanged",
  "windowVisibility == View.VISIBLE",
]) {
  requireText(
    reactiveBoard,
    token,
    `Production reactive board is missing 3D runtime marker: ${token}`,
  );
}

if (
  reactiveBoard.includes("LudoPaws3DLegalHaloView") ||
  reactiveBoard.includes("pawn3DLegalHalo")
) {
  throw new Error(
    "Production 3D gameplay must not render a follow-circle/legal-halo layer around animal pawns.",
  );
}

for (const token of [
  "TextureView",
  "EGL_OPENGL_ES3_BIT_KHR",
  "LudoPawsPawnAnimationPolicy",
  ".plans(",
  "LudoPawsPawnMotionKind.CAPTURE_RETURN",
  "LudoPaws3DCaptureReturnState",
  "captureReturns",
  "characterIdsBySeat",
  "bindRenderAssignments",
  "LudoPawsGameplayPacingPolicy",
  ".forwardAnimationDurationMillis(",
  "LudoPaws3DRenderCadencePolicy",
  ".frameDelayMillis(",
  "onVisibilityChanged",
  "onWindowVisibilityChanged",
  "setPresentationVisible",
  "awaitPresentationVisible",
  "ReentrantLock",
  "visibilityChanged.signalAll()",
]) {
  requireText(
    scene,
    token,
    `Shared 3D scene runtime is missing required marker: ${token}`,
  );
}

for (const token of [
  "internal fun drawYardPawPrintsOverlay(",
  "drawYardPawPrints(canvas, cell)",
  "pawPrintArtwork",
  "pawPrintGlowPaint",
  "pawPrintEdgePaint",
  "pawPrintTexturePaint",
  "toePad(",
  "val scale = (cell * 0.25f)",
  "pawPrintDetailPaint.color = Color.argb(48, 38, 33, 29)",
  "classicTokenDrawingEnabled",
]) {
  requireText(
    canonicalBoard,
    token,
    `Canonical board is missing safe footprint rendering marker: ${token}`,
  );
}

for (const token of [
  "footprintSource?.drawYardPawPrintsOverlay(canvas, cell)",
  "fun setFootprintSource(source: LudoBoardView)",
  "Ludo Paws requires clean white token bays",
  "fillPaint.color = Color.WHITE",
]) {
  requireText(
    boardChrome,
    token,
    `Topmost board chrome must expose white token bays and visible footprints: ${token}`,
  );
}
requireText(
  board,
  "boardChrome.setFootprintSource(if (visible) baseBoard else boardSurface)",
  "Footprint drawing must follow the currently visible board copy across 3D fallback transitions.",
);
requireText(
  board,
  "boardChrome.invalidate()",
  "Top-layer footprints must redraw whenever authoritative match state changes.",
);

for (const token of [
  "LudoPaws3DCharacterPolicy.speciesForSeat",
  "LudoPathEncoding.normalizeLegacyEntry(rawPosition)",
  "position == -1",
]) {
  requireText(
    footprintPolicy,
    token,
    `Yard footprint policy is missing state/identity marker: ${token}`,
  );
}

for (const token of [
  "footprints appear only for tokens no longer in their yard slots",
  "selected character identity wins over team default species",
  "missing character assignments use deterministic existing team fallbacks",
  "a token returned to its yard no longer leaves a visible footprint",
  "legacy team color strings normalize before footprint placement",
  "null match state produces no stale footprints",
]) {
  requireText(
    footprintTests,
    token,
    `Yard footprint regression coverage is missing: ${token}`,
  );
}

for (const token of [
  "ACTIVE_FRAME_DELAY_MILLIS",
  "IDLE_FRAME_DELAY_MILLIS",
  "HOME_CELEBRATION_TAIL_MILLIS",
  "hasActiveAnimation",
  "captureReturns",
  "activeReactions",
]) {
  requireText(
    cadencePolicy,
    token,
    `Adaptive 3D cadence policy is missing required marker: ${token}`,
  );
}

for (const token of [
  "speciesForCharacterId",
  "speciesForSeat",
  "LudoPawsCharacterCatalog",
  "bindRenderAssignments",
  "ThreadLocal",
  "RenderAssignments",
  "current.snapshot === snapshot",
  "current.characterIdsBySeat === characterIdsBySeat",
]) {
  requireText(
    policy,
    token,
    `3D character identity policy is missing selected-character/cache marker: ${token}`,
  );
}

for (const token of [
  "LudoPawsFxBoardGeometry",
  "LudoPathEncoding",
  ".positionAtVisualStep(",
  "LudoPawsPawnLayout",
  ".tokenOffsetFraction(",
  ".roadPresentation(",
  "val radius = fit.radius",
  "LudoPawsCaptureReturnPlacement",
  "captureReturnVisual(",
  "CharacterRegistry.resolve",
  "pawn.character.renderer.sample",
  "pawn.character.renderer.draw",
  "LudoPawsPawnLayout.rootYForFootContact(",
  "LudoPawsPawnLayout.contactShadowRadius(",
]) {
  requireText(
    renderer,
    token,
    `Shared 3D renderer is missing required production marker: ${token}`,
  );
}

// Readiness must follow a presented board frame, not EGL initialization.
for (const marker of ["if (!egl.swapBuffers())", "!firstBoardSubmitted", "frameState.snapshot != null", "onStartupFailure?.invoke()"]) {
  requireText(scene, marker, "First-board presentation/fallback guard missing: " + marker);
}
const initStart = scene.indexOf("renderer.onSurfaceCreated()");
const frameLoopStart = scene.indexOf("while (running)", initStart);
if (scene.slice(initStart, frameLoopStart).includes("setOperational(true)")) {
  throw new Error("Do not reveal the board before the first complete GL frame is submitted.");
}

for (const token of [
  "radiusScale",
  "val original = cell * radiusScale(position, occupancy)",
  "tokenOffsetFraction",
  "StackPlacementCache",
  "WeakReference",
  "stackPlacementCache",
  "cached.snapshotRef.get() === snapshot",
]) {
  requireText(
    layout,
    token,
    `Production pawn layout/cache policy is missing required marker: ${token}`,
  );
}

// The enlarged 3D model must be planted at its foot pivot, not at the
// model-origin/body-center; otherwise it appears on the NEXT board cell.
for (const marker of [
  "fun rootYForFootContact(",
  "boardCenterY + modelScale * groundPivotY",
  "fun contactShadowRadius(",
  "cell * 0.30f",
]) {
  requireText(layout, marker, "Pawn foot-contact anchoring rule missing: " + marker);
}
requireText(renderer, "boardCenterY = pawn.y", "3D pawns must ground to actual board position");
requireText(renderer, "groundPivotY = pawn.character.definition.groundPivotY", "Each species needs its own physical foot pivot");
requireText(renderer, "pawn.x,\n            pawn.y,\n            -0.80f", "Shadow must mark exact cell, not offset toward next tile");
if (renderer.includes("pawn.radius * pawn.character.definition.rootYOffsetPerRadius") ||
    renderer.includes("pawn.radius * pawn.character.definition.footBaselinePerRadius")) {
  throw new Error("Enlarged pawns must not move their contact center with size-based body offsets.");
}
for (const marker of [
  "each animal species stands on the exact authoritative tile at enlarged scale",
  "contact shadow stays small and tile centered even with larger models",
]) {
  requireText(visualLayoutTests, marker, "Foot-contact geometry regression missing: " + marker);
}

const reactiveHost =
  activity.includes("LudoPawsReactiveBoardView?") &&
  screen.includes("LudoPawsReactiveBoardView(this)") &&
  reactiveBoard.includes("LudoPawsBoardView(context)");
if (!reactiveHost) {
  throw new Error(
    "Offline gameplay must host the shared production 3D runtime through LudoPawsReactiveBoardView.",
  );
}

const consumesPersistedCharacterAssignment =
  controls.includes(".loadActive()") ||
  (controls.includes("resolveOfflineCharacterIds") &&
    characterState.includes(".loadActive()"));
if (!consumesPersistedCharacterAssignment) {
  throw new Error(
    "Offline gameplay must consume the persisted active character assignment directly or through the repaired character resolver.",
  );
}
requireText(
  controls,
  "characterIdsBySeat",
  "Active character assignments must remain bound to the presentation layer by seat.",
);
requireText(
  tests,
  "shared cell reduces animal radius without collapsing it",
  "Shared-cell sizing coverage is missing.",
);
requireText(
  tests,
  "stable snapshot and cell reuse the same stack placement map",
  "Stable 3D stack-placement cache coverage is missing.",
);
requireText(
  tests,
  "stack placement cache invalidates for new snapshot or board cell size",
  "3D stack-placement cache invalidation coverage is missing.",
);
requireText(
  policyTests,
  "seat assignment wins over ludo color for production rendering",
  "Selected-character-over-color rendering coverage is missing.",
);
requireText(
  policyTests,
  "render thread color lookup follows installed seat assignments",
  "Render-thread selected-character binding coverage is missing.",
);
requireText(
  policyTests,
  "render binding refreshes when selected seat list changes",
  "3D character binding cache assignment invalidation coverage is missing.",
);
requireText(
  policyTests,
  "render binding refreshes for a new authoritative snapshot",
  "3D character binding cache snapshot invalidation coverage is missing.",
);
requireText(
  cadenceTests,
  "idle board uses lower cost cadence",
  "Idle 3D cadence regression coverage is missing.",
);
for (const retired of [
  "android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/LudoPawsCaptureReturnOverlayView.kt",
  "android/app/src/main/java/com/rekixo/ludopaws/game/ui/components/LudoPawsCharacterReactionOverlayView.kt",
  "android/app/src/main/res/drawable/lp_starter_duck.xml",
  "android/app/src/main/res/drawable/lp_starter_squirrel.xml",
  "android/app/src/main/res/drawable/lp_starter_hedgehog.xml",
  "android/app/src/main/res/drawable/lp_starter_sheep.xml",
  "android/app/src/main/java/com/rekixo/ludopaws/feature/characters/prototype/Dog3DPrototypeActivity.kt",
  "android/app/src/main/java/com/rekixo/ludopaws/feature/characters/prototype/Dog3DPrototypeView.kt",
  "android/app/src/main/java/com/rekixo/ludopaws/feature/characters/prototype/Dog3DRenderer.kt",
  "android/app/src/main/java/com/rekixo/ludopaws/feature/characters/prototype/Goat3DPrototypeActivity.kt",
  "android/app/src/main/java/com/rekixo/ludopaws/feature/characters/prototype/Goat3DPrototypeView.kt",
  "android/app/src/main/java/com/rekixo/ludopaws/feature/characters/prototype/Goat3DRenderer.kt",
  "android/app/src/main/java/com/rekixo/ludopaws/feature/characters/prototype/Duck3DPrototypeActivity.kt",
  "android/app/src/main/java/com/rekixo/ludopaws/feature/characters/prototype/Duck3DPrototypeView.kt",
  "android/app/src/main/java/com/rekixo/ludopaws/feature/characters/prototype/Duck3DRenderer.kt",
  "android/app/src/main/java/com/rekixo/ludopaws/feature/characters/prototype/Cat3DPrototypeActivity.kt",
  "android/app/src/main/java/com/rekixo/ludopaws/feature/characters/prototype/Cat3DPrototypeView.kt",
  "android/app/src/main/java/com/rekixo/ludopaws/feature/characters/prototype/Cat3DRenderer.kt",
]) {
  requireAbsent(
    retired,
    `Retired 2D/prototype renderer must stay removed: ${retired}`,
  );
}

for (const retainedMotion of ["Dog", "Goat", "Duck", "Cat"]) {
  const path =
    `android/app/src/main/java/com/rekixo/ludopaws/feature/characters/content/${retainedMotion.toLowerCase()}/${retainedMotion}3DMotion.kt`;
  const characterRenderer = read(`android/app/src/main/java/com/rekixo/ludopaws/feature/characters/content/${retainedMotion.toLowerCase()}/${retainedMotion}Renderer.kt`);
  requireText(characterRenderer, `${retainedMotion}3DMotionTimeline`, "Character renderer must use its own motion timeline");
  if (!fs.existsSync(path)) {
    throw new Error(
      `Production renderer still consumes this pure motion timeline, so it must remain: ${path}`,
    );
  }
}

console.log(
  "Ludo Paws pawn rendering gate passed: selected seat animals drive one shared visibility-suspended, adaptive-cadence 3D runtime over the locked authoritative board, with cached stable bindings/stack geometry, movement sync, 3D capture return, shared-cell sizing, circle-free animal presentation and legacy renderer cleanup intact.",
);
