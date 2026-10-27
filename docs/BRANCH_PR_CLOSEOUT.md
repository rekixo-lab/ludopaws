# Historical branch and PR disposition — October 27, 2026

Closed without merge does not imply that an old patch should be restored. The audit distinguishes commit ancestry from functionality subsequently implemented in the migrated package and current protocol.

| Original game PR | Disposition in current source |
| --- | --- |
| #224, #188 | Original head commits are already ancestors of main. |
| #275 | Launch-background icon removal is present in `ludo_paws_launch_window_background.xml`; the old head itself was not merged. |
| #241 | Final `com.rekixo.ludopaws` application/package identity is present; the old migration patch must not be reapplied. |
| #157 | Earlier board-first UI was superseded by the current gameplay/lobby presentation, board environment and compact promotion header. Preserve the current requested design. |
| #144, #147, #148 | Old client-seed commit/reveal recovery belongs to the retired protocol. Current V1 server-generated dice uses authoritative snapshots and `OnlineRollActionPolicy`; pending rolls wait for synchronization. Reintroducing client-seed paths would conflict with this protocol. |
| #145 | Old automatic-turn feedback patch is superseded by the current presentation feedback dispatcher and feedback ledger. Its original head is not an ancestor; no claim of exact behavioral equivalence is made. |
| #17 | Old external EntroNex service binding belongs to the retired entropy transport. Current server dice implementation does not require restoring it. |
| #293 | WIP age-entry architecture was superseded by the current family safety and Play age-signal handling. The Play Age Signals dependency remains; unknown/declined ranges do not establish adulthood or server authorization. Do not revive the old WIP entry flow merely to merge its head. |

Game PR #307 retains the audit status update and corrects four obsolete visual source assertions to check the shipped promotion slot, selected-board habitat, scoped gameplay backdrop and content-ready character presentation. Purchase guards, authority checks and branch checks are retained. Website PR #4 was merged and published after both `site` and Cloudflare builds passed. No branch protection was disabled.

Retained historical branch names are not pending deploys. They are not deleted as part of this review. The backend runtime is the deployment recorded in `REFERRAL_ACTIVATION_STATUS.md`; gate/documentation-only cleanup does not require another runtime rollout.

Genuine referral device acceptance and final publisher policy/declaration decisions remain explicit activation conditions. They must not be marked passed by merging source or running mocked tests. The in-review production artifact is outside this cleanup.
