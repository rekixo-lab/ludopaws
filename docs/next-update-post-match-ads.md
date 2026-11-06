# Next update: completed-match ads

Prepared on `codex/next-update-comeback-bonus`. No deployment or Play release is included.

- Completed Pass & Play, Computer, Online, Friends and Team Up results qualify for both winners and losers. Unfinished matches do not qualify; remote results must be authoritative and the current player must participate.
- The first completed match can qualify, without the old three-minute/two-match restriction. Interstitials retain a five-minute app-wide cooldown; the last display timestamp is saved across process restarts. Eligibility is an opportunity, not guaranteed inventory.
- EXIT/Back and offline REMATCH use the same controller, ownership and frequency gate. Android 13+ online back now uses that gate too. Remote modes currently have no equivalent in-place rematch action.
- Ads preload on gameplay entry/resume. Load/consent failures receive up to three automatic retries at 15/30/60 seconds, with a five-minute pause before further lifecycle/result-triggered attempts. Cached ads refresh after 55 minutes. Pending timers stop when the host is destroyed, and timer retries only run with window focus.
- Authenticated Remove Ads ownership is prepared during gameplay, refreshed every 30 seconds while focused, and usable for at most 60 seconds. Account changes, unknown/stale ownership, wallet failures or a locally confirmed Remove Ads purchase suppress display. Exit never waits for a wallet or ad request. Delayed requests cannot trigger a later surprise ad.
- Existing UMP/Family ad treatment, production ad-unit configuration and user-requested rewarded ads remain independent.

Per user instruction, no tests, build, CI or deployment were run. Existing policy test expectations and source-readiness markers were updated but not executed. Before the eventual release, verify first-match win/loss across modes, EXIT/REMATCH/Android back, cooldown and process restart, missing inventory, retries/expiry, slow or offline wallet, account changes, Remove Ads purchases, repeated taps and lifecycle teardown on a real device.
