# Next update implementation and source recheck

Branch: `codex/next-update-comeback-bonus`. User explicitly prohibited tests and builds, including after clarification. No tests, Gradle, app execution, CI, deployment, live prices, referral campaign activation or Play release were performed. JavaScript parsing (`node --check`), source/diff review and XML/manifest/dependency inspection are static review, not evidence of runtime success.

## Implemented

- Shared installation/day/session budget for unsolicited review, referral and saved-item prompts; manual user actions stay available. Fullscreen interactions suppress subsequent promotions. Referral opportunity remains pending until displayed; dismiss defers seven days and automatic impressions stay capped at three.
- Native Google Play review 2.0.2 after three completed matches on at least two observed days, independent of win/loss; 90-day attempt interval, no reward or sentiment pre-screen. Manual rating opens Google Play directly. Attempts are never reported as submitted reviews. Official Maven POM/AAR checksums and strict dependency locks are included without running Gradle.
- Explicit Home Rewards entry: server-owned daily 3 Gems, one free weighted spin (1/2/3/5 Gems at 50/30/15/5%), seven-claim cosmetic milestone without streak reset, local play goals using existing XP/mastery, optional one-per-server-day signed-ad +5 Gems, and curated cosmetic collections. Normal play does not require claims, ads, payments or Google linking.
- Server UTC date windows, atomic wallet+receipt writes, same-request replay and independent same-day deduplication; delayed signed-ad rewards use a server-issued ticket and persist recovery hints. Account/session ownership is checked on server and client. Claim outcome is committed before wheel animation. Ad availability and catalog outages cannot turn a free reward into a charge. Account deletion removes new server/local account data.
- Collections charge only the discounted missing-item price and check the current quote/ownership before charging. Existing ownership and Play SKU quantities are unchanged. Monthly featured collection is server-selected, with no false countdown/expiry.
- Two new native board themes (Dawn Meadow/Moonlit Grove) with installed renderers, manifest entries and matching server Gem pricing; Gems only, no unprovisioned Play SKU. Existing characters are reused in collections, without claiming new character art/voices.
- Separate local two-player Quick Match with two racing paws per player (other slots home), explicit setup description, immutable local ruleset, opening/comeback rules, save/restore and rematch preservation. Available for Pass & Play and Robot from Rewards; no wallet awards or online ranked matchmaking were added for this local variant.
- Comeback follow-up repair: offline invariant validator now accepts v7 and the authoritative bonus-entry legal set, instead of rejecting the earlier branch change.
- Local aggregate counters for completed games, prompt attempts, rewards, delivery and collections; no analytics SDK, account IDs or outbound telemetry in those counters. AdMob/Play console revenue and cohort retention require actual release observations and cannot be manufactured by source changes.

## Source review passes

1. Rating/referral: call sites, shared reservation/release, lifecycle generation, cooldown, no rating incentives, manual listing fallback.
2. Rewards: authentication/rate limits, SQL uniqueness, transaction boundaries, midnight/retry, no client currency authority, deletion, slow callbacks/account switch and signed SSV recovery.
3. Economy/catalog: unchanged purchased grants, owned-item pricing, availability, no new Play products, explicit prices and no fees for free spins.
4. Local Quick Match/comeback: setup seat normalization, initial tokens, first six, bonus legal set/destination, current/legacy save validation and rematch ruleset preservation.
5. Final static review: edited JS syntax, whitespace, XML and manifest structure, content registry consistency, official review dependency hashes and call-site/package signatures.

## Runtime/release verification remains required

The code is not claimed to be compiled, tested or device-verified. Before any authorized release: backend/client tests and build, physical-device lifecycle/font/TalkBack/reduced-motion review, reward concurrency and rollback, signed SSV including late mediation callbacks, account switching/deletion, Quick Match completion/restore, collection purchase/restore, popup collisions, native review quota behavior, existing billing refunds and old clients. Deploy matching backend support before distributing the new app. Reconnect and purchase/reward recovery reuse existing mechanisms; source review alone cannot certify their reliability or actual revenue.

## Scope boundaries

No new animal species/voice packs, subscription, paid season pass, cash rewards, paid spins, changed live prices, changed legacy Gem pack quantities, or automatic notification campaigns were introduced. These were later/conditional ideas in the recommendations, not an instruction to fabricate assets or provision live products. Store growth here is concrete new board content, owned-aware collections, existing-character mastery and explicit purchase/ad opportunities. Expansion can use the existing content pipeline after actual art/audio and product definitions exist.
