# Natural Google Play rating flow

Branch: `codex/next-update-comeback-bonus`, on the latest-main integration. Source review only; no tests/build, CI, deploy or Play release performed.

- Home requests the unmodified Google Play review card after at least three locally recorded completed matches across two observed UTC days, with a 90-day attempt interval and the shared automatic prompt budget. Eligible privacy/lifecycle checks remain in force. Wins and losses have the same eligibility; no sentiment question, five-star request, currency or other rating incentive is introduced. Google controls whether a request actually displays a card and does not report submission.
- Manual Home Rate opens the official Play Store package directly; if unavailable, it uses the existing web listing fallback. It does not call the quota-limited popup API or use an undocumented direct-review URL. It cannot promise automatic scrolling to the stars.
- Corrected the previous eligibility gap: experience was written only through profile-stat completion, which excluded Friends and Team Up, and eligibility read totalGames. Completed match IDs/days now determine eligibility independently of ranked XP. Remote authoritative natural-completion handling records all three remote modes; completed local result recovery records Robot, Pass & Play and Quick Match. Abandoned-loss profile bookkeeping no longer adds review experience.
- Duplicate results are ignored, shared writes are synchronized across store instances, late local recovery uses its original timestamp and pinned account/installation scope, old recovery does not replace today's count, and deleted-account recovery is ignored. Existing saved experience remains; past unrecorded Friends/Team Up games are not reconstructed or invented.

Recheck: followed Home Rate and automatic review call sites, package/import, thresholds/cooldown, all completion hooks, win/loss neutrality, account/deletion and repeated/late result paths. `git diff --check` passed. Device validation remains necessary; a local debug build cannot demonstrate production Google Play review quota behavior.

Reference: https://developer.android.com/guide/playcore/in-app-review#quotas
