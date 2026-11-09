# Google Play update experience (Ludo Paws V1)

## Scope
Only `com.rekixo.ludopaws` Android V1. This does not change the Ludo Paws V2 Godot repository.

Home checks Google Play for app updates **after** its existing privacy/startup gates and before optional review/referral prompts. Play decides eligibility per account, device, installation source, track, country and rollout. Offline users, APK sideloads and Play API failures continue without any update block.

- A normal eligible update shows a dismissible in-app explanation, then the Play **flexible** flow. The app can continue while the download runs.
- A Play release explicitly marked priority 5 may use the **immediate** flow if Play permits it. Do not mark ordinary feature releases priority 5.
- Completed flexible downloads show an install/restart action on Home, and unfinished immediate flows are resumed on return.
- Normal prompts are throttled to once per available version every 72 hours. Release notes are displayed once per installed upgrade, not on fresh installs.
- The post-install message displays the installed version and sends users to Google Play for **exact** release notes. It does not invent features or change purchase/reward eligibility.
- The existing automatic prompt gate prevents simultaneous review/referral dialogs when an update message is active. No push-notification permission is requested.
- The Play In-App Update API is not a general push-notification service and cannot guarantee a dialog for every existing user; updates must be live and eligible in the user's Play track.

## Release checklist
1. Merge only after all required Android and server CI checks pass. The existing signed release workflow must run from `main`.
2. Increase `LUDOPAWS_VERSION_CODE` above every uploaded code and provide the matching `LUDOPAWS_VERSION_NAME`. The Gradle defaults are for local builds and are not the release version.
3. Publish the signed AAB to the intended Google Play track separately: the GitHub signed-build workflow only uploads a GitHub artifact.
4. Write accurate Google Play release notes for **that specific version**.
5. Validate on a Play-installed test device: old build installed from Play, newer build available on the same track/account; verify flexible prompt, Later, download, install, app restart, What's New once, and save/account persistence.
6. For priority-5 security/compatibility releases only, verify immediate flow and cancellation/resume behavior separately.
7. Confirm first-time installations, offline mode, canceled updates, and non-Play installs remain playable.

Automated JVM regression cases are in `UpdateExperiencePolicyTest.kt`. Real Play install behavior requires track-enabled device testing.
