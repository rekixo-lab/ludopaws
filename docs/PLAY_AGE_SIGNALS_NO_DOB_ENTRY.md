# DOB-free entry / Play Age Signals v0.0.4

## What ships
- The current app has no date-of-birth UI. The unused pure DOB fallback is removed.
- Home requests Google Play age-range sharing at most once per **app process**, after the first-run game guide is dismissed. Google Play may show its **own** sharing prompt depending on account/region; an entirely silent age check is NOT guaranteed or promised.
- If shared, fetch age range and apply existing fail-closed `AgeAccessPolicy.fromPlayRange`. Never read, store or transmit install ID, Google account ID, DOB, age tier evidence, or a raw response. The in-memory band is only used for offline-mode explanation.
- On NOT_SHARED, VERIFICATION_REQUIRED, no Play Store, failed request, or ambiguous range, stay UNKNOWN. Offline Robot and Pass & Play work without a DOB page.
- Adult signals on Android **never** enable online play, voice, chat, friends, payment or ads. BuildConfig hard disables connected gameplay, and the backend outermost age-access guard fails closed regardless of flags.

## Not included and cannot be honestly claimed
Play Age Signals data is client-side, can be declined, and isn't an independently verified server entitlement. Do NOT merge the old PR #293 or legacy Families DOB dialog. Do not use the result for advertising, analytics, identity or fraud profiling. Google Play/Families target age, approval, region, Play testing, publisher legal review and trusted account-bound authorization remain required before enabling online play.

## Testing
- Android unit tests for UNKNOWN, underage, mixed ranges and offline UI copy.
- `node scripts/check-age-entry-policy.mjs` runs in CI.
- All Android builds/lint/emulator, Worker tests, JS/Kotlin CodeQL, release-readiness on exact merge commit.
- Physical Play internal-track supervised, unshared, shared adult, no Play Store, cross-account switch and prompt collision tests still require operator evidence.

## Production
Only offline-only safe release configuration. Cloudflare production deployment is manual, protected, and must not be bypassed. If deployed, the backend must continue replying 403 on restricted endpoints.
