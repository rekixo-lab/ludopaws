# Age-appropriate access rollout — release block

This branch introduces a **client-only safety prototype**. It is not an independently validated age verification or a certified child-compliance solution. Do **not** merge or deploy this branch as a Families-policy compliance claim.

## Google Play policy and required publisher decisions
- Play Console target age groups, app content, ads and Data Safety declarations must agree with the actual published build.
- Determine whether the game is marketed to children. Review Play Families, COPPA where applicable, India's DPDP commencement, and local child-data laws with counsel.
- Google Play Age Signals v0.0.4 is available only when signals are shared. The API never guarantees exact DOB; `NOT_SHARED`, `VERIFICATION_REQUIRED` and errors require a suitable fallback. Never prompt users into sharing age to bypass a declined request.
- Age Signals must not be used for advertising, analytics, marketing, personalization, or tracking. Never log/store raw age responses or Google's install ID.

## Security blockers
1. **Server-side enforcement is not implemented in this branch.** App-side gates are bypassable by modified clients and existing standalone API credentials. Before rollout, protect registration, friends requests/invites/messages, voice signaling, matchmaking, online matches, purchases and reward endpoints with trusted server-held eligibility assertions; deny unknown and minors unless independently verified parent permissions and policies allow them.
2. Obtain issuer-authenticated attestation and bind it to the app account and an expiring session, including Google Play Integrity nonce verification and account-switch invalidation. A client-provided `age=adult` flag or shared preferences is **not** a trustworthy proof.
3. A Play Age Signals response may not be permitted to be copied or repurposed as generic backend identity verification. Review API usage terms and independent legal basis for the enforcement architecture.
4. Ads and analytics must have their **own** child-directed/privacy controls, independent of Play Age Signals. Keep all ad units disabled until the publisher has approved appropriate target treatment, Families-certified ads SDK use when necessary, UMP handling and independent consent.
5. Review optional Friends voice (WebRTC, IP leakage), free-text chat, user IDs and public matchmaking for supervision, reporting, moderation, parental consent, privacy and abuse handling. This prototype blocks under-18 access rather than treating those features as approved for minors.
6. LudoProofApplication currently initializes Play Games and starts background guest/account activity on app startup. Audit all pre-consent processing; avoid social-presence and third-party data processing before a compliant user-selection stage.

## QA blocker matrix
| Condition | Expected behavior |
| --- | --- |
| Play verified 18+ age band shared | Connected UI eligible; backend independently verifies and authorizes |
| Shared child/teen age range | Local offline play only; no ads, chat, voice, purchase, matchmaking |
| Unknown / not shared / verification required | Neutral fallback, offline-only, no repeated coercive sharing |
| Missing Play Store or disconnected | Safe offline fallback, no crash |
| User switches Google/Play account | Old grant invalidated; reverify before connected use |
| Age range changes | Reevaluate, revoke previously granted permissions |
| New install, reinstall, restore, multiple devices | No inheritance of unrelated account's age |
| Billing, rewarded/interstitial ads | Age-independent privacy/consent safeguards |
| Tampered APK / direct backend call | Server denies absent trusted eligibility proof |
| App resumed during age dialog | No duplicate prompts |
| Regional age laws | Approved locale-specific obligations, do not rely only on 13/18 cutoff |

## Release gates
- Unit tests, lint, Android builds and emulator instrumentation fully green.
- Real Google Play internal-track tests across supervised, shared, declined, verified, and unavailable conditions.
- Backend security contract tests for every covered endpoint; penetration tests including replay and account mismatch.
- Publisher's policy/legal review and updated public privacy notice, terms, store listing and consent declarations.
- Deployment intentionally separate from GitHub merge. Do not enable production advertising or billing while unresolved.
