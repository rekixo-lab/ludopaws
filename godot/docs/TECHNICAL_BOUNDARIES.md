# Live app → Godot V2: technical boundaries

The live product is **rekixo-lab/ludopaws** (private): native Kotlin Android, custom OpenGL/Canvas
rendering, and a Cloudflare Worker / Durable Object backend. This repository,
**rekixo-lab/ludopaws-v2 (private)**, is its Godot 4 migration and will ship as the Play Store
update of the same app.

## What carries over

| Live | V2 |
|---|---|
| Android application ID `com.rekixo.ludopaws` | Same package ID (release export preset) |
| Play Console release track, Play App Signing | Owner uploads; owner signs with the existing upload key |
| Cloudflare backend, accounts, wallet, match rooms | Same backend and protocol; V2 is a client |
| Server-verified online dice and proofs | Shown as delivered by the server |
| Offline EntroNex receipts | Not ported yet (local RNG, decision 1a); decide before release |
| Kotlin/OpenGL renderers, characters, boards, dice, FX, audio | Ported to GDScript/Godot and parity-tested |
| Live UI (activities, layouts, dialogs, drawables) | **Not** ported; V2 Meadow UI only |
| Google Play Billing, AdMob (+ UMP, SSV), Play Games, Credential Manager | Godot Android plugins |
| Local data (SharedPreferences, encrypted session) | Migrated on first launch of the update |

## Read-only systems

The live repository, the live backend, Play Console and the AdMob account are **never modified**
from this project. V2 reads the live source to port behaviour and talks to the backend only as a
normal client.

## Secrets and release configuration

Never commit: signing/upload keys or passwords, real AdMob App ID and ad unit IDs, the backend
base URL, OAuth client secrets, tokens, player data. They belong in the git-ignored file
`config/release.local.cfg`; `config/release.example.cfg` documents its keys with placeholder
values only. Development builds use Google's published **test** ad unit IDs and local stubs for
billing, ads and sign-in. The owner signs release builds; the repository contains the export
preset, never the key.

## Parity

Behaviour must match the live code. Rule parity is proven with golden vectors generated from the
unchanged live server engine and Kotlin Robot policy (`tests/fixtures`). Presentation parity is
checked with screenshots against the live geometry, timings and materials. Every feature, its live
sources and its status are tracked in `docs/PARITY_INVENTORY.md`.

## Device-only checks

Billing, ads, sign-in, Play services, haptics and old-data migration can only be verified on a
phone. They are implemented against desktop stubs, marked **untested on device**, and verified
with an ADB-connected phone in their phase.
