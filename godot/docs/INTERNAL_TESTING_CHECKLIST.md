# Internal testing checklist (Play internal track, production package)

These flows only work in a build **installed from Google Play** (internal testing track) with
the production package `com.rekixo.ludopaws` and release signing. They are **untested on device**
until the owner runs them.

| Flow | How to check | Expected (live behaviour) |
|---|---|---|
| In-app update, flexible | Upload a higher versionCode, install the lower one from the track, open Home | "Ludo Paws Update Available · Update / Later"; download continues in the background ("Downloading update… %"); then "Update Ready to Install · Restart to update" |
| In-app update, critical | Same, with in-app update priority 5 in Play | Immediate (full-screen) Play update flow |
| What's New | Update over an older install | "What's New in Ludo Paws" once, before any other prompt |
| In-app review | Adult age band; ≥ 3 completed matches on ≥ 2 days; Home | Play review sheet may appear (Play decides); not again for 90 days; no reward |
| Install referrer + referral | Install through a friend's `ludopaws.rekixo.com/r/…` link (Play click), link Google, accept the optional disclosure, Verify | Invitation verified (BOUND); the inviter gets the server reward after a qualifying match |
| Play Integrity | Part of the referral bind | Server accepts the integrity token |
| Real ads | ads_enabled + real units; rewarded / daily bonus / post-win | SSV credits Gems; post-win ad at most once per match, 5 min apart |
| Billing | Test purchase with a license tester | Server verifies, Gems / item granted, pending token cleared |
| Google sign-in / Play Games | Adult band | Save / Restore with Google; Play Games auto sign-in |
