# Referral activation status — October 27, 2026

## Completed

- Play Console dashboard confirmed 177 production countries/regions and the current submission in review. No release, track, listing or review submission was changed.
- With explicit publisher confirmation, linked Play Integrity to Rekixo Ludo Paws, project number `314526321614`, project ID `rekixo-ludo-paws-511506`. Licensing, app-recognition and device-integrity responses became available. The displayed daily quota is 10,000; linking is not a successful real-device attestation.
- Created the separately authorized `ludopaws-referral-integrity` service account without granting Billing, Owner or Editor roles. Existing Billing credentials were not changed.
- The generated key download was unavailable to both the operator and automation. With explicit confirmation, permanently revoked that unused key. No usable copy was sent to chat or committed.
- Attempted the separately authorized public-certificate registration alternative. Google organization policy `iam.disableServiceAccountKeyUpload` blocked it. No organization policy was weakened, no extra administrative role was granted, and no usable replacement key was registered.
- Added explicit referral inputs to the existing manual-only production workflow. Missing inputs reset optional approvals to OFF rather than retaining stale deployment values. Activation requires policy, SDK and release review, Google linking, project number, signing fingerprints, immutable campaign start and minimum eligible versionCode.
- Added a secret-name-only deployment check for both dedicated Integrity bindings. Credential values are never written into generated Wrangler variables.
- Added an owner-only-file credential provisioner. It rejects the Billing identity, authenticates the dedicated identity with Google before an explicitly requested `--apply`, and sends secrets to the existing Cloudflare Worker through stdin without printing them. After the publisher supplied a newly downloaded Google-generated key, Google authentication succeeded and both dedicated Cloudflare secret bindings were stored and independently confirmed by name. The local credential file was restricted to owner-only permissions. No private key or token was printed or committed.

## Validation and rollout evidence

## Subsequent rollout progress — October 27, 2026

- The publisher requested merging all completed work; game `main` reached `3aeb1cc26153961f27fe089cf627b8f978246c46`. Protected signing and deployment were dispatched explicitly, not bypassed.
- Signed internal candidate versionCode 10 / `1.0.0-rc10-referral` passed the complete signing workflow, unit tests, release lint, APK/AAB signature checks and SBOM generation: [run 40456577643](https://github.com/rekixo-lab/ludopaws/actions/runs/40456577643). Its AAB SHA-256 is `5149d32020f2f38397c35c89a9c2527f3d474e19a56f629a8014ccb6498a3a81`. Google Play accepted it and shows it available to internal testers. Production version 9 remains in review; neither its artifact nor declarations were edited.
- The connected Samsung subsequently reported versionCode 10, installer `com.android.vending`, the verified Play app-signing fingerprint and `api.ludopaws.rekixo.com: verified` in Android App Links. Home and the disabled referral screen were opened. The operator did not uninstall or clear the original app. This proves actual Play installation and domain association, not a positive Integrity token decode or referral qualification.
- New backend code deployed successfully through [run 40456581413](https://github.com/rekixo-lab/ludopaws/actions/runs/40456581413); [run 40457051515](https://github.com/rekixo-lab/ludopaws/actions/runs/40457051515) applied verified project number, current Play signing fingerprint and minimum versionCode 10. Live readiness confirms all bindings and dedicated credentials, project/certificate/minimum version configured, normal Billing/ads preserved and referral activation/review flags OFF. Campaign start remains unset until an approved launch; no reward was manufactured.
- The backend suite subsequently passed 296 tests. The final refined local burst regression passed with 250 distinct account/identity fixtures and 1,000 requests, checking unique invitations and duplicate retries. The local run took 885ms; this is synthetic SQLite evidence, not Cloudflare/Google throughput certification. Logs: `/tmp/ludopaws-referral-final-296.log` and `/tmp/ludopaws-referral-burst-final.log`.
- Publisher approved next-update notices on the existing public website and explicitly allowed one website PR. Actual hosting is the separate `rekixo-lab/ludopaws-website` Cloudflare Worker, not GitHub Pages. The unused Pages publication attempt failed before deployment; DNS was not changed. [Website PR 5](https://github.com/rekixo-lab/ludopaws-website/pull/5) preserves protection and adds tested terms/disclosures. See subsequent publication evidence before claiming these pages are live.
- Publisher explicitly declined creation of the proposed error monitor. No monitor, notification destination or paid plan was created. Existing Worker logs and maintenance cron are not a replacement for queue/provider alerting.
- Website PR 5 passed both `site` and Cloudflare Workers Builds, then was squash-merged through the repository's normal protection into website main as `7df65b1797a6bf6dd8fa46886fc6c4fa9a608b4d`. Initial Preview failures were fixed using the documented `previews: {}` configuration and compatible pinned Preview command; no CI-name/tag validation was disabled. The legacy game-repo Pages workflow now requires explicit `LUDOPROOF_WEBSITE_HOSTING=github-pages`, preventing accidental publication attempts against a site actually hosted by Cloudflare.
- [Website release run 40460691051](https://github.com/rekixo-lab/ludopaws/actions/runs/40460691051) completed successfully for that merged website revision. Live HTTPS checks followed redirects and confirmed HTTP 200, FINWORKAR identity, inviter-only three-asset/100-Gem terms, optional participation and the new privacy/retention notice at `https://ludopaws.rekixo.com/referral-terms.html` and `/privacy-policy/`. The Console still showed rc9 production changes in review after the internal rc10 release. No Data safety questionnaire or target-audience declaration was submitted or changed.

### Remaining acceptance boundary

The current server has no isolated pre-release referral cohort/staging acceptance path. The public campaign is intentionally unavailable while its release-validation flag is OFF. Do not set that flag merely to make an end-to-end test possible. Arrange a separately reviewed isolated/pilot acceptance environment before public activation, preserving all real Play package, license, certificate and Integrity checks. Then use an actual inviter and genuinely new friend who downloads through the invitation before installation; the already manually installed tester is not eligible for retroactive attribution. This requires real participation rather than a fabricated match or install. Final next-update Data safety/region review and deployed capacity/provider/queue alerting are still release gates; monitoring creation was explicitly deferred by the publisher.

The older table and source-only rollout statements below describe their recorded checkpoints. This progress section supersedes their claims that no signed artifact, Play install or backend deployment occurred. Real invite-download attribution, positive provider verification and server reward acceptance remain unperformed. Do not relabel the existing manually installed tester as a new referred install or disable security gates to manufacture acceptance.

The publisher explicitly authorized builds and automated tests during this follow-up. These results supersede the earlier source-only report, not the current production release.

| Check | Result |
| --- | --- |
| Referral regression suite, included in backend total | 10 passed, 0 failed |
| Runtime configuration suites, including new activation gates | 18 passed, 0 failed |
| Android debug Kotlin compilation, offline dependencies | Passed, existing warnings remain |
| Android JVM regression suite | 494 passed, 0 failed |
| Complete backend suite on Node 22.23.4 | 295 passed, 0 failed |
| Physical Samsung SM-A715F, Android 13, isolated QA app | 40 passed, 0 failed; includes 3 referral privacy tests |
| Cloudflare Worker packaging dry run | Passed; no deployment |
| Local unsigned release APK/AAB, R8 and release lint | Passed; code 10 candidate, billing/ads disabled; no upload |
| Dedicated Google credential / decode negative probe | OAuth succeeded; invalid input rejected with HTTP 400 INVALID_ARGUMENT |
| Real Play installation → Integrity → qualifying match → bundle | Not performed |

Final backend and configuration validation used Node 22.23.4, matching the production workflow major version. The full backend run used normal Node test isolation. Earlier Node 24 results are superseded.

The previously reported failures were stale shipped-content/product expectations and an incomplete fake SQL interpreter. Tests now assert the actual 11 characters, seven shipped boards, two dice and intended purchase channels. Account tests use real in-memory SQLite and transactions rather than weakening production account queries. Elephant's intentionally lower hop is explicitly asserted. Logs: `/tmp/ludopaws-referral-node22-complete.log`, `/tmp/ludopaws-referral-config-node22.log`, `/tmp/ludopaws-referral-android-final.log`, `/tmp/ludopaws-referral-device-full.log` and `/tmp/ludopaws-referral-worker-dryrun-output.log`.

The isolated `com.rekixo.ludopaws.qa` app was installed only after explicit publisher permission. Its API is loopback and billing/ads are disabled; original app/data were not modified. The restricted-age showcase is now hidden before any network response, including offline failure. Device checks cover consent, withdrawal and restricted UI, not positive Play attestation. An additional local integration case uses real SQLite/wallet and mocked Google responses to cover minimum-version rejection, binding, bot rejection, a naturally finished losing friend and exactly-once three-cosmetic plus 100-gem reward. Mock responses are not provider acceptance evidence.

## Policy assessment and remaining blockers

- Technical review found no reward for a rating/review, no contacts/advertising-ID request in referral code, explicit optional consent and adult privacy-screen gating before referral SDK calls, server-only fulfillment, withdrawal and bounded retention disclosures. These are engineering findings, not Google or worldwide legal approval.
- Google's [ratings, reviews and installs policy](https://support.google.com/googleplay/android-developer/answer/9898684) prohibits placement manipulation. A genuine invite-and-play design must not be described as a blanket exemption. Keep the campaign approval flag OFF pending publisher review of its actual presentation and use.
- Google's [Families policy](https://support.google.com/googleplay/android-developer/answer/9893335) and [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311) require audience-appropriate handling and accurate disclosures. Play Console organization details confirmed FINWORKAR, developer name Rekixo and verified website rekixo.com. Prepared next-update notices reflect this; public pages and in-review declarations were not published/edited. Regional legal eligibility and final Data safety answers still require publisher review; technical checks cannot grant Google or worldwide legal approval.
- The original unavailable generated key was revoked. The rejected local public-certificate alternative and its temporary private files were removed with publisher confirmation. The publisher then supplied a different, successfully downloaded Google-generated credential for the dedicated account. This resolved credential provisioning without changing the organization's public-key-upload restriction or Billing credentials. Maintain secure credential storage and rotation; Google OAuth authentication alone does not validate real-device token decoding.
- Cloudflare secret-name metadata confirmed `LUDOPAWS_INTEGRITY_SERVICE_ACCOUNT_EMAIL` and `LUDOPAWS_INTEGRITY_PRIVATE_KEY` after provisioning. Existing public readiness remained healthy. Secret provisioning changes the Worker's secret configuration, not its application code. No application deployment, campaign activation, Play upload or reward grant occurred.
- A physical Play-certified device/new eligible install and a second player are still required. No emulator, debug sideload or mock Integrity response can substitute for this acceptance.
- Set the immutable campaign start and minimum versionCode only for the actual next validated Play build. Do not recycle the current in-review artifact or infer acceptance from compilation.

**Activation remains OFF.** Credentials and automated/device-QA regressions are complete. Real licensed Play-install acceptance, deployed provider/load checks and final next-update publisher disclosures remain release gates. The protected signing workflow only signs merged `main`; no signing guard was bypassed. No signed internal-track artifact was uploaded. The publisher subsequently requested merging the completed work into `main`; that source merge does not authorize a Play upload or campaign activation. Use the normal protected release process for the next candidate, independently of the current in-review submission. Do not mark release validation true based on QA or mocked responses.

## Safe credential command for an approved local file

Keep the file outside the repository, owner-readable only, and never paste its contents into chat. The helper accepts only the dedicated referral identity in the linked project.

```sh
node scripts/provision-referral-integrity.mjs --key-file "/secure/path/referral-key.json"
node scripts/provision-referral-integrity.mjs --key-file "/secure/path/referral-key.json" --apply
```

The first command checks Google credential authentication without changing Cloudflare. The second stores only the two dedicated secret bindings; it does not deploy application code or enable referral flags. Neither is a substitute for a successful real-device token decode.
