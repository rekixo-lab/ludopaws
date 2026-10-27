# Ludo Paws — Real-device 3D performance QA

This is the final device-side performance evidence pass for the shared Ludo Paws 3D board runtime. It does not change game rules, Fair Dice, online authority, or presentation behavior.

## Prerequisites

- Windows PowerShell.
- One authorized Android phone connected over USB debugging.
- Ludo Paws debug build installed with `node scripts/android-local-testing.mjs dev` (also VS Code `Ctrl+Shift+B`).
- If more than one Android device is connected, set `ANDROID_SERIAL` first.

The capture script resolves `adb.exe` from `PATH`, `LUDOPROOF_ANDROID_SDK`, `ANDROID_SDK_ROOT`, `ANDROID_HOME`, the shared FinWorkar SDK location, or the normal `%LOCALAPPDATA%\Android\Sdk` location.

## 1. Active gameplay sample

Start a Computer or Pass & Play match and exercise the expensive visible moments during the capture: dice, legal halos, pawn movement, capture return, home celebration, and animal reactions.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\measure-ludopaws-performance.ps1 `
  -DurationSeconds 60 `
  -Label active-gameplay `
  -EnforceBudget
```

Default evidence budget:

- janky frames: no more than 5%;
- app frame-time P95: no more than 33 ms;
- no Android `FATAL EXCEPTION` / ANR entries;
- no `LudoPaws3D` runtime-fallback warning.

The 3D renderer internally targets the higher cadence while movement, capture return, home celebration, or reactions are active. The `gfxinfo` budget intentionally remains device-tolerant because it measures Android app/UI frame delivery rather than the private OpenGL render-loop interval directly.

## 2. Waiting / idle board sample

Leave a live board visible without moving pieces and capture a second sample:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\measure-ludopaws-performance.ps1 `
  -DurationSeconds 60 `
  -Label idle-board `
  -EnforceBudget
```

This verifies that the lower-cost idle cadence still keeps the screen responsive without introducing app-level jank.

## 3. Background / foreground lifecycle check

During a normal match:

1. Press Home so Ludo Paws is no longer visible.
2. Leave it backgrounded for at least 20 seconds.
3. Return to the app.
4. Confirm the board resumes immediately, no historical capture/home reaction is replayed, and gameplay authority is unchanged.
5. Run another short capture after resuming if anything looks abnormal.

The renderer is expected to suspend draw/swap work while the view/window is hidden and wake when visible again.

## Evidence produced

Each run writes a timestamped directory under `artifacts\real-device-performance\` unless `-OutputDirectory` is supplied. It contains:

- `performance-report.json` — parsed summary and budget result;
- `gfxinfo-framestats.txt` — Android frame statistics;
- `meminfo.txt` — process memory snapshot;
- `battery.txt` and `thermalservice.txt` — device context;
- `display.txt` — display/refresh-rate context;
- `activities.txt` — current activity state;
- `surface-layers.txt` — SurfaceFlinger layer evidence;
- `ludopaws-logcat.txt` — AndroidRuntime errors plus `LudoPaws3D` runtime logs.

Do not commit device evidence directories to source control unless a release investigation explicitly needs them.

## Useful options

Use `-SkipLaunch` when the exact gameplay screen is already open. Use `-MaxJankPercent` and `-MaxP95FrameTimeMs` only when testing a known device-specific budget. Without `-EnforceBudget`, the script still captures evidence and reports `WARN` instead of returning a failing exit code.

A release candidate is device-QA complete only after at least one representative physical phone passes the active sample and the idle sample, plus the background/foreground lifecycle check.
