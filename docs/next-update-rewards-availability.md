# Rewards visibility and live-service diagnosis

On 2026-11-05, the user authorized inspecting the connected Samsung A71. Read-only ADB screen inspection showed the new themed Rewards page, but Gifts displayed the unavailable/empty state; Challenges showed the weekly card and the daily goal far below it. No build, install, gameplay, ad, claim or purchase was performed during this inspection.

A separate unauthenticated GET to `https://api.ludopaws.rekixo.com/api/rewards` returned HTTP **404** with `{"error":"NOT_FOUND","message":"route not found"}`. This is a missing deployed endpoint, not evidence that the user's internet connection is absent. The matching server route and account reward implementation exist in the update branch. That server code has not been deployed by this work.

Fixes in the branch:

- Daily Gift and Daily Free Spin cards and the actual wheel now remain visible before status loads and when the service is unavailable. They are explicitly marked as a preview; no dummy spin result, fake claimed state or local Gem minting is introduced. Claim/spin actions remain disabled until the authenticated reward status is available.
- HTTP 404 now shows that daily gifts/spins are not active yet, rather than asking the user to reconnect or repeatedly implying a temporary break. A stale previously loaded snapshot is cleared on 404. A successful status response clears the unavailable flag and restores the existing genuine server-claim flow.
- Challenges now starts with a separately titled **Daily Challenge** card, displaying the actual `0/1` or `1/1` completed-match goal and its progress, before the weekly challenge. This is a play goal; Gems are explicitly collected separately in Gifts. Existing mastery goals remain below the weekly challenge.

Rechecked null/loading/404/offline/ready branches, disabled buttons, true daily progress and repeated refresh behavior, account-owned claims and spin callbacks; `git diff --check` passed. The new UI change has not been compiled, installed or visually inspected on the phone. No tests, CI, deployment, merge to main or Play release was performed. Screens inspected were the already-installed UI commit `1a623d6f` behavior; the phone was left at the existing daily-goals section.

Complete functional validation still needs a matching isolated testing backend, or a later explicitly authorized production backend rollout. Pulling/rebuilding the Android branch alone cannot make an absent server endpoint fulfill wallet rewards. Existing instructions prohibit deployment, so no server was changed as a workaround.
