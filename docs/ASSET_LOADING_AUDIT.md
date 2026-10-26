# Asset loading audit

Scope: Home startup, Store Paws/Boards/Dice, and entering a match after Play.
The user reports slow loading in those areas. Findings below come from source
inspection, not a measured device trace. No build, tests or installation ran.

## Findings and changes

- Each Board thumbnail started a full reactive game view: an EGL context,
  shader compilation, 3D house construction/uploads, movement audio preloading
  and a frame loop. These views also consumed the prepared house instance
  intended for gameplay. Thumbnails now use the shared base board and cached
  2D theme artwork. Animated 3D houses remain in the actual game.
- Paws requested portraits for every card before those cards were visible.
  Requests now begin when an attached avatar is drawn in the visible viewport.
  The existing vector avatar remains an immediate fallback.
- Portraits were cached per team color even though character materials no
  longer contain team accessories. One natural portrait is reused across team
  colors. External player badges and rings keep their own colors.
- Each Board/Dice card reread encrypted account ownership and equipment.
  Those catalogs now read ownership and selection once per render; Paws cards
  share one ownership set too. Purchase/equipment actions retain their existing
  fresh account and server authorization checks.
- Only two board artwork entries were retained, so the three available themes
  and gameplay-sized artwork could evict each other. The cache now uses a
  byte-bounded 12 MiB LRU rather than an entry-count limit.
- Custom boards waited for Classic yard textures they do not draw. Only
  themes with the habitat enabled now wait for that preparation.
- Home started Classic assets before constructing its UI. Warming now starts
  after the first frame opportunity, uses the equipped board, and replenishes
  prepared houses when returning Home.
- The selected character and original four starter models warm their CPU
  geometry on a dedicated worker. Their existing renderers run against a no-op
  draw context, so anatomy definitions are shared and no extra EGL context is
  created. Gameplay does not add a gate waiting for this worker.
- Movement and online dice audio prewarming now run once per process on a
  worker instead of scanning/loading asset families during scene construction
  or Play entry. Disabled sound and failures allow a future retry.
- Background music listed/opened assets and synchronously prepared MediaPlayer
  during the application's foreground lifecycle callback on the UI thread.
  Preparation now runs on a worker; installation and audio focus stay with the
  controller. Completion rechecks current foreground/preferences, and a release
  generation prevents an obsolete load from resurrecting a released player.
- Dice previews already use a static Canvas renderer rather than a live EGL
  scene or downloaded bitmap. Their renderer did not require replacement.

## Device verification

`LudoPawsLoad` logs report Store UI construction duration by tab, board artwork
readiness including queue/preparation, portrait queue/render durations, CPU
model preparation, audio/music preparation, and GL-worker-to-first-game-frame duration.
Only content IDs, dimensions, durations, tabs and success flags are logged.

On the user's test device, capture:

```bash
adb logcat -s LudoPawsLoad:D
```

Compare cold launch, first/repeated Paws visits, scrolling to later Paws,
first/repeated Boards and Dice visits, and Play-to-game entry on the same phone.
A board cache hit does not emit a new bake log. The game first-frame metric
excludes navigation, account and matchmaking time before its GL worker starts.

GPU shader compilation/uploads still occur in the real game context. Home's
estimated board size can differ from the measured gameplay size, requiring a
second correctly sized bake. The game startup cover still waits for ready
artwork and a submitted 3D frame, or the existing playable fallback; no timer
bypasses it. Improvement needs confirmation from actual device timings.
