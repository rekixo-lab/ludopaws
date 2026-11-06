# Next update: capture comeback bonus

Prepared only on `codex/next-update-comeback-bonus`. No deployment or Play release is part of this change.

## Rule

After a capture leaves a player with no unfinished token on the track or home lane, arm a per-player counter. Finished tokens do not prevent eligibility. The first two resolved non-six rolls consume the turn normally. On the third resolved roll, a natural six follows existing rules; a non-six grants entry of one yard token to position zero instead of a normal move. The displayed/raw dice is unchanged. Bonus entry consumes the roll, does not move further, does not grant an extra turn, and is recorded as `comebackBonusApplied`.

The lowest-index yard token is the single automatic legal move (yard tokens are interchangeable). Reset the counter after entry. A later capture that strands the player starts a fresh counter. An existing unfinished token anywhere outside the yard prevents assistance. Timeouts do not increment the counter. Human and bot seats use identical rules.

## Compatibility and release

Classic uses new `CLASSIC_V7` / `ludoproof-standard-v7`; Team Up uses `TEAM_UP_V6` / `ludoproof-team-v6`. Existing ruleset definitions and server default selection remain unchanged. The next Android app explicitly requests the new versions; matchmaking separates rulesets. Old online matches and restored local saves retain their original ruleset. New local matches use v7. Counters survive server persistence and local saves and enter the authoritative randomness state. Raw dice receipts remain unchanged.

When the user authorizes the next release, coordinate backend support before publishing the app that requests these new rules. Deploying backend support does not upgrade existing matches or old clients. Do not publish this app ahead of matching backend support.

## Validation still required before release

Per user instruction, no tests, builds, CI or deployment were run for this commit. Review only was performed. Before the eventual release, verify capture into full-yard state, first/second misses and third-roll entry, natural six and extra turns, finished tokens plus last-token capture, existing track/home-lane tokens, repeated captures, timeout/retry/reconnect, pending local-save restore, bot auto-moves, Team Up partner handoff, old saves/ruleset proofs, animation and visible bonus explanation. Compare wait time, capture-related quits and win rates during playtesting.
