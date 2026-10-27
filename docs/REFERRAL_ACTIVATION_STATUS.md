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

## Validation actually performed

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
