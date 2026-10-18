# Ludo Paws — Final Gameplay Presentation Behavior Spec

Status: Product/presentation contract for implementation

This document defines how a complete Ludo Paws match must look, feel, sound and respond. It is intentionally presentation-focused: authoritative rules, native Fair Dice receipts, legal moves, captures, turn ownership, winner calculation and network authority remain outside this document.

The current Android codebase is the source of truth for gameplay state. Presentation may visualize an already-authoritative transition, but it must never invent or alter game outcomes.

## 1. Product goal

A player should understand the match without reading debug-style status text. Turn ownership, dice state, legal animals, movement, capture, safe-cell arrival, home arrival, extra turn, penalties and match result must be communicated primarily through motion, highlight, sound, haptic and concise UI state.

The final game should feel like one coherent game across:

- Computer
- Pass & Play
- Online
- Friends
- Team Up

The game-mode controller/data source may differ. The core in-match visual hierarchy and event behavior should not.

## 2. Non-negotiable architecture rules

1. Game state is authoritative; animation is disposable.
2. Dice animation never chooses the outcome. It only reveals the already-authoritative result.
3. Animal/pawn animation never changes token coordinates or legal moves.
4. Offline and online use the same presentation behaviors for the same committed event.
5. Every committed gameplay event must be idempotent at sound/haptic/FX boundaries.
6. Reduced Motion must preserve meaning while reducing spatial motion.
7. A visual failure must never block the match. The board and touch geometry remain usable if 3D rendering fails.
8. Do not duplicate a real pawn visually under a 3D animal. Hit-test geometry and visible pawn rendering must be separable.

## 3. Target in-match screen hierarchy

The final in-match screen should be game-first rather than form-first.

### Top layer

- compact back/settings affordance;
- connection/reconnect indicator only when relevant;
- Fair Dice/verified state as a small badge, not a large audit panel;
- timer/deadline indicator only when the mode requires it.

### Player layer

Each active seat shows:

- actual equipped animal portrait;
- player name;
- player/team color accent;
- local/CPU/team identity where relevant;
- home/racing/yard progress in compact form;
- strong active-turn state;
- dice attached visually to the active player.

Inactive players must remain readable without competing with the active player.

### Board layer

- board geometry remains authoritative and fixed;
- only the final animal pawn renderer is visible during normal supported-device play;
- legal-move halos are visual-only;
- safe cells, home lanes and finish area remain clearly readable beneath animals and FX;
- overlapping animals must remain distinguishable.

### Bottom/action layer

During normal play, show only actions a normal player needs. Developer/audit actions such as Engine Map must not occupy the primary match surface.

Fair Dice history/audit remains available through a secondary details surface.

## 4. Match lifecycle behavior map

### 4.1 Match enters ACTIVE

Visual:
- board fades/settles in;
- all active player cards become visible;
- current player card receives the turn emphasis;
- active player's dice appears beside that card;
- legal pawn highlights are hidden until a resolved roll exists.

Audio/haptic:
- optional short match-start cue;
- no repeated voice line on every rebind/reconnect.

Input:
- only the acting player can roll/move according to authoritative state.

### 4.2 Turn begins

Visual:
- previous turn highlight transitions out;
- new active card gets color/gold emphasis;
- subtle pulse around active card/dice;
- active animal may perform a short attentive idle pose;
- status copy is secondary, e.g. `YOUR TURN` or `<Name>'s turn`.

Audio/haptic:
- local-player turn may use one light cue/haptic;
- do not spam sound for every remote state refresh.

### 4.3 Dice press

Input behavior:
- press feedback is immediate;
- duplicate taps are blocked while roll is in flight;
- the control remains visually readable while disabled.

Animation sequence:
1. touch-down compression, roughly 60–90 ms;
2. release/pop, roughly 80–120 ms;
3. tumble/spin while the authoritative roll resolves;
4. one or two short impacts/bounces;
5. settle to the verified result;
6. result stays clearly readable while the player selects a move.

Audio/haptic:
- press/click cue optional;
- dice shake/tumble cue;
- landing cue on settle;
- light impact haptic on final landing.

Reduced Motion:
- replace tumble/translation with a short face-transition/fade and final result emphasis.

### 4.4 Dice result = normal value

Visual:
- final face is stable;
- legal animals receive an animated halo/pulse;
- illegal animals remain visually calm and non-interactive;
- if exactly one move is legal, the game may still wait for the player unless product explicitly enables auto-move.

Audio:
- no generic celebration cue.

### 4.5 Dice result = six

Visual:
- final six receives a short accent glow;
- eligible yard animals receive strong legal-move emphasis;
- active player card gets a short excited pulse;
- animal reaction may use EXCITED/SIX behavior.

Audio/haptic:
- unique six cue, not a reused safe/home sample;
- medium positive haptic.

Rule note:
- presentation reflects authoritative extra-turn behavior; it does not decide it.

### 4.6 Third-six / roll penalty

Visual:
- dice briefly desaturates/locks;
- active animal displays frustration/sad reaction;
- turn emphasis transfers after authoritative state changes.

Audio/haptic:
- dedicated third-six/penalty cue;
- short negative haptic pattern.

### 4.7 Legal animal selected

Visual:
- selected animal gets a short anticipation squash/lift;
- other legal halos fade;
- input for another move is blocked while committed movement presentation plays.

Audio:
- subtle selection cue if needed; do not reuse generic UI click when a movement-specific cue is more appropriate.

### 4.8 Forward movement

Movement must be cell-by-cell using canonical board path geometry.

For each visual step:
- animal moves to the next canonical cell;
- short hop/run step with eased horizontal motion;
- small vertical lift appropriate to species;
- landing/compression at the destination cell;
- path must never visually cut across the board to reach the final destination.

Species identity:
- Dog: energetic hop/run, tail/ear response;
- Goat: springy step, head/body confidence;
- Duck: short waddling/bobbing step;
- Cat: controlled soft leap, tail/head attitude.

Audio:
- movement sound should be rhythmically rate-limited; do not play an overpowering full-volume clip for every frame;
- optional species-light step texture may be layered later.

Haptic:
- normally no heavy haptic per cell; reserve stronger haptics for meaningful landings.

### 4.9 Leave yard

Visual:
- animal performs a slightly stronger first hop from yard to start cell;
- start cell briefly acknowledges arrival;
- legal halo disappears after committed movement begins.

Audio/haptic:
- unique leave-yard/entry accent may be used;
- light positive haptic.

### 4.10 Land on ordinary cell

Visual:
- normal soft settle;
- no unnecessary particle burst.

Audio:
- final movement landing cue only.

### 4.11 Land on safe cell

Visual:
- safe-cell ring/shield glow around the landed animal;
- animal performs a confident/relieved reaction;
- effect stays short enough not to delay the next turn.

Audio/haptic:
- unique safe cue;
- light positive haptic.

### 4.12 Capture opponent

This is a priority moment and must not be represented by merely hiding the captured animal.

Sequence:
1. attacker arrives at the capture cell;
2. impact flash/shake at collision;
3. captured animal performs shocked/hit pose;
4. captured animal pops/lifts away from the cell;
5. captured animal follows a curved return-to-yard path or a short stylized return sequence;
6. captured animal lands and settles in its yard;
7. attacker gets a short victory/confident reaction;
8. captured animal gets sad/angry/frustrated reaction where appropriate.

Audio/haptic:
- dedicated capture-hit cue;
- separate return/landing texture if useful;
- strong but short capture haptic;
- animal voice/reaction must not drown the core gameplay SFX.

Reduced Motion:
- collision flash + destination yard flash + reaction icon/pose; avoid long travel animation.

### 4.13 Reach home

Sequence:
1. final path step into home;
2. gold/positive destination burst;
3. animal performs HOME celebration;
4. player progress updates visibly;
5. if the turn continues by rules, turn emphasis remains; otherwise it transitions normally.

Audio/haptic:
- unique home cue;
- medium positive haptic.

### 4.14 Player completes all animals / match victory

Visual:
- normal board controls lock;
- winner card expands/emphasizes;
- winner animal/full-body presentation becomes the hero;
- board celebration/confetti runs once;
- losing animals may use subdued defeat reactions without obscuring winner UI;
- clear primary action: rematch/new game/continue depending on mode;
- secondary action: view result/Fair Dice details.

Audio:
- dedicated victory sting, longer than normal SFX but short enough for repeat play;
- background music ducks under the victory sting and restores appropriately.

Haptic:
- success pattern once.

### 4.15 Defeat

Visual:
- losing local player's card/animal receives subdued defeat reaction;
- winner remains the visual focus;
- avoid punitive flashing or prolonged negative animation.

Audio:
- dedicated defeat cue distinct from capture.

### 4.16 Reconnect / stale online state

Visual:
- board remains visible using last accepted state;
- compact reconnect banner/status;
- active controls disable only when authority/network state requires it;
- on fresh snapshot, presentation reconciles without replaying every historical animation.

Audio:
- do not replay capture/home/victory SFX merely because a state was re-fetched.

## 5. Animal visual contract

The starter playable identities are the currently canonical four:

- Dog
- Goat
- Duck
- Cat

For final production character presentation, each character needs:

- board pawn/model representation;
- portrait;
- selection/store/full-body representation;
- idle motion;
- forward move motion;
- anticipation/select motion;
- capture-attacker reaction;
- captured reaction;
- safe reaction;
- home reaction;
- six/excited reaction;
- frustrated/third-six reaction;
- victory reaction;
- defeat reaction.

If rigged 3D assets are adopted, they should be optimized for mobile and share a constrained animation/runtime contract. If procedural geometry remains as fallback, it must not be treated as the final authored character asset.

Player/team color must be applied as an accent/ring/accessory and must not recolor the animal into an unnatural solid pawn color.

## 6. Pawn rendering and hit-testing contract

Current/future architecture must separate:

- board painting;
- token coordinate/hit-test geometry;
- visible animal rendering;
- legal-move indicator;
- transient FX.

When the 3D renderer is operational:
- classic token bodies must not be visible underneath;
- invisible/geometry-only hit targets remain available;
- legal halo follows the same canonical token center;
- accessibility still exposes legal-token information.

When the 3D renderer is unavailable:
- a readable classic/fallback pawn may be shown;
- gameplay must remain fully functional.

## 7. Sound design matrix

Every production sound must be semantically distinct unless deliberately designed as a family variation.

Required core cues:

- UI click
- dice press
- dice tumble/shake
- dice final land
- normal movement/step
- leave yard
- six
- safe
- capture impact
- captured return/yard land
- home
- extra-turn acknowledgement (optional subtle cue)
- third-six/penalty
- timer warning where applicable
- reconnect/connection restored (optional)
- victory
- defeat

Do not ship with click=move, capture=defeat, or home=safe=victory duplicates.

Background music:
- actual authored/licensed loop, not a placeholder-sized MIDI;
- seamless loop;
- low enough in mix to preserve dice/animal/SFX clarity;
- proper audio focus handling;
- ducks under animal voice and victory sting.

## 8. Animal voice contract

On-device TTS is acceptable only as development fallback.

Production target:
- species/personality-appropriate licensed or created clips;
- short reactions rather than long spoken commentary;
- multiple variants for frequently repeated events to avoid fatigue;
- normalized loudness;
- no copyrighted third-party dialogue.

Priority rule:
- gameplay SFX first;
- one highest-priority animal reaction at a time;
- music ducks under voice;
- no overlapping chatter from multiple seats.

## 9. Haptic contract

Suggested hierarchy:

- UI press: very light
- dice landing: light
- six/safe/home: light-to-medium positive
- capture: short strong impact
- penalty/third-six: short negative double pulse
- victory: success pattern

Haptics must respect the existing user setting and platform capability.

## 10. Reduced Motion contract

Reduced Motion must not remove information.

Replace:
- long translations with short fades/highlights;
- spin/tumble with face transition;
- capture travel with impact + yard destination flash;
- dense confetti with sparse/static celebration;
- repeated shake/rotation with scale/color emphasis.

Keep event ordering and control locking identical to normal motion mode.

## 11. Performance contract

Target behavior on supported Android devices:

- active animation aims for smooth frame pacing;
- idle board should not require unconditional full-scene 60 FPS rendering if nothing is changing;
- pause or reduce rendering when screen/app is not visible;
- avoid allocating/rebuilding complete player rails on every snapshot when stable views can be rebound;
- no leaked render threads, MediaPlayers, SoundPools, TTS instances or animators after screen destruction;
- character assets load lazily and remain within existing memory budgets;
- animation degradation must never alter gameplay state.

Performance acceptance should include a low/mid-range physical Android device, not emulator-only evidence.

## 12. Accessibility contract

- Dice exposes current state/result through content description.
- Player cards expose player, animal, color/team, progress and active-turn state.
- Legal moves must be discoverable without depending on color alone.
- Reduced Motion is supported.
- Essential game state must not exist only inside transient particles/animation.
- Touch targets meet practical mobile minimums.
- Final pawn interaction should support accessible legal-token selection, not only one monolithic `Ludo board` node where feasible.

## 13. Offline/online parity contract

For the same committed event, Computer/Pass & Play/Online/Friends/Team Up should use the same:

- dice reveal behavior;
- legal-move highlight;
- animal movement;
- capture sequence;
- safe/home sequence;
- sound mapping;
- haptic mapping;
- result presentation.

Only authority/latency/network messaging differs.

## 14. Presentation events that must be idempotent

These must play once per authoritative committed event, even across rebind/reconnect/render refresh:

- dice result SFX/settle;
- six reaction;
- movement sequence;
- capture impact/return;
- safe reaction;
- home celebration;
- victory/defeat;
- animal voice;
- strong haptic events.

Use event/revision/receipt-derived presentation keys rather than view-instance timing alone.

## 15. First implementation vertical slice

The first production-quality slice is Computer mode with two seats because it exercises the full loop without network latency complicating visual debugging.

Required completion order:

1. Final game HUD shell and stable player-card layout.
2. Separate board hit-test/token geometry from visible pawn bodies.
3. Final dice interaction and reveal animation.
4. Stable legal-animal highlight.
5. Cell-by-cell animal movement with input lock.
6. Real 3D capture return sequence instead of hide/reappear.
7. Safe and home presentation.
8. Six and third-six reactions.
9. Victory/defeat presentation.
10. Production SFX mapping and haptic sync.
11. Replace development TTS/placeholder art/audio with production assets.
12. Real-device performance/accessibility pass.
13. Reuse the same presentation shell in Pass & Play and remote modes.

## 16. Acceptance scenario for the vertical slice

A test match must demonstrate, in one uninterrupted session:

1. player turn emphasis;
2. dice press/tumble/verified settle;
3. six result;
4. animal leaves yard;
5. multi-cell movement;
6. ordinary landing;
7. safe landing;
8. capture with attacker/captured reactions and visible return to yard;
9. third-six/penalty presentation;
10. home arrival;
11. CPU turn feedback without duplicate animation/sound;
12. match victory and defeat presentation;
13. sound off / music off / animal voices off / haptics off settings;
14. Reduced Motion behavior;
15. app background/foreground without leaked or replayed presentation events.

The slice is not complete if any of these behaviors is represented only by status text.

## 17. Current codebase gaps this spec intentionally closes

Current implementation already has useful foundations: canonical path geometry, reaction detection, FX policy, SoundPool infrastructure, music focus/ducking, haptics, reduced-motion settings, online/offline state adapters and an ES3 animal scene.

However, final product work is still required because:

- character catalog visual/audio readiness remains false for starter animals;
- authored character assets are not yet present in the reserved paths;
- fallback art is still used by portraits;
- animal voices use TTS fallback;
- several SFX resources are duplicate audio blobs;
- background music is still placeholder-grade;
- dice roll is a face-cycling view rather than a physical-feeling reveal;
- current 3D capture behavior hides the captured animal instead of animating its return;
- gameplay screen hierarchy still exposes development/audit controls too prominently;
- remote presentation is layered onto an older form-like screen instead of sharing one final match shell;
- real-device performance/accessibility/game-feel validation is still required.

This document is the implementation contract for closing those gaps without weakening gameplay authority or Fair Dice integrity.
