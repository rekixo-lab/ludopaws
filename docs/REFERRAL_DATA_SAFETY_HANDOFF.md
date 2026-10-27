# Next-update referral disclosure handoff

This worksheet records implementation facts, not submitted Play declarations or legal approval. Do not edit the current in-review submission. Review the whole shipped application and all SDKs, not only this optional feature, before submitting the next update's Data safety answers.

## Publisher and distribution

Play Console organization: FINWORKAR; developer name: Rekixo; support: AI@rekixo.com. The latest releases overview inspected on October 27, 2026 displayed production version 9 in review and 178 of 178 countries/regions. Earlier dashboard evidence said 177; use the current Console selections, not a hard-coded country count. App distribution is not proof of referral eligibility under every jurisdiction.

## Feature processing to assess

| Processing | Purpose / handling |
| --- | --- |
| Internal account IDs, linked Google-subject hashes, Friend/profile identities | Account attribution, eligibility and duplicate-reward prevention; pseudonymous, not anonymous |
| Opaque invite/click tokens, Play click/install times | Attribute an explicitly requested invitation download; not a contact list |
| Consent version/time and withdrawal state | Optional participation; ordinary gameplay and ordinary sharing remain available |
| Match finish evidence and reward/wallet records | Server-authoritative qualification and exactly-once fulfillment |
| Standard Play Integrity SDK/provider signals | Google processes app/package/version/signing, licensing and device integrity signals; backend transiently verifies token, does not persist/log it |

Do not answer that no identifiers are processed merely because referral code does not request Advertising ID. Evaluate Google's SDK disclosures and the Console definitions of collection, sharing, ephemeral processing, security/fraud exceptions, purposes and optionality. Do not invent a legal classification from variable names alone. Google linking and other monetization SDK behavior must also be included in the final form.

## Retention and user controls

Use the exact published privacy policy and referral terms: click records up to 21 days; challenges five minutes; qualifying match references 14 days; campaign attribution/participant hashes and bounded terminal-event outbox expire no later than campaign end plus 14 days. Wallet/idempotency evidence remains with an active account. Account deletion and withdrawal have different effects; already queued minimal outbox evidence can survive deletion until its disclosed bound but cannot recreate deleted rewards. Provider retention is independent.

## Publication and acceptance

The publisher explicitly approved next-update notice publication on October 27, 2026. This is not Google approval of the incentive, a certification of all regional laws, or permission to change production version 9. Do not mark release validation complete until the actual licensed Play build, token decode and referral fulfillment are observed. A QA sideload or mocked token is insufficient. Production campaign activation remains independently gated.

References: [Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311), [Play Integrity data disclosures](https://developer.android.com/google/play/integrity/terms), [Install Referrer](https://developer.android.com/google/play/installreferrer), [ratings, reviews and installs policy](https://support.google.com/googleplay/android-developer/answer/9898684).
