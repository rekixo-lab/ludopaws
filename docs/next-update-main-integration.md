# Latest-main integration for local device review

The next-update branch now merges `origin/main` at `ed27bbb3` into the existing feature history through `a1b8f441`. Main and the live deployment are unchanged. This merge is for pulling the combined source into the user's local repository and building on their device.

Resolution decisions:

- Keep latest main's result-card overlay, readable standings/XP presentation, centered promotion Save hint, animal identity/ownership authorization, billing/Remove Ads fixes, released shop shelves, artwork, animal renderers and audio. Old parallel imports before the next-update feature baseline are not allowed to replace main's current assets.
- Preserve capture comeback classic-v7/team-v6, post-match ads, prompt budget/native review, daily gift/free spin/collections/new boards, local Quick Match, result Share, Friends links and weekly local badges.
- Keep main's human-count setup and Robot/human seat identities. Quick Match is an additional optional parameter after `humanCount`, preserving existing positional call signatures. Rematch retains both human count and Quick Match ruleset. Quick setup ignores a saved normal Robot match's player-count preference, so a prior four-player setup cannot break the two-player variant.
- Add Share to main's current result action row and use its current opaque result card and asynchronous XP refresh rather than reinstating the older celebration text panel.
- Keep main's signed 2-Gem rewarded unit and unlimited Shop rewarded placement. The optional once-per-day Rewards placement now also displays/credits 2 Gems, using the signed server `gemAmount`; free daily/spin amounts and probabilities are unchanged. The old branch's 5-Gem daily-ad copy is superseded. Status reports the current bonus amount, and the client checks the same v2 availability capability as Shop. Both signed amount and daily ticket are forwarded to the account ledger.
- Preserve current catalog prices, paid-Paws ownership checks and legacy purchase grants alongside owned-aware collection quotes. No live catalog or price write was performed.

Review: conflict regions and their surrounding methods/callers were reread; checked Quick Match creation/restore/rematch, result-card fields/imports, v7 negotiation with current character-auth tokens, daily-ticket/amount forwarding, latest main audio/production assets, review dependency/import, and workflow triggers. JS parsing and XML/JSON structure checks cover the 21 affected source/config files; no tests, Gradle, app build or execution, CI, deploy, merge to main or Play upload was run. `[skip ci]` is used for the merge commit.

User-side next step: fetch, switch to `codex/next-update-comeback-bonus`, pull fast-forward, refresh the local testing script's public build configuration, clean the Android build and run on the device. This is still source-reviewed integration, not a compiled or device-verified merge. The user's successful build of `a1b8f441` predates this integration and does not validate it. Online v7, daily/spin server rewards and browser room landing require the matching next-update backend; no backend was deployed by this merge.
