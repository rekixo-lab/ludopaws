# Rewarded Ads: 2 Gems, no daily app cap — safe rollout

## Decision and scope
- New Android builds grant **2 Gems per completed AdMob SSV-verified rewarded ad**. The player initiates every view; there is no app-imposed count/day cap.
- Old installed Android builds retain their **original 5-Gem AdMob rewarded unit** and their original 10/day client preflight. They cannot be retroactively updated without a new APK. Keep honoring 5-Gem signed callbacks for those users. Do **not** edit the legacy AdMob unit's reward.
- The wallet, Play Gem Pack SKUs, Play prices, match ranking rewards, referral bundle, Remove Ads bonus (5 Gems), earned Gems, legacy Panda/Monkey grants and previous cosmetic-targeted ad progress do not change.
- Direct-ad cosmetic progress (Lumi 10, Donkey 15, Pearl Paw Dice 20, Astral 15) remains recoverable and each valid ad advances one step. A new valid v2 ad also awards its signed 2 Gems; v1 awards 5 Gems. Do not delete earned progress.

## Manual AdMob + GitHub production configuration (required before new rewarded ads can run)
1. In **Google AdMob**, **create a NEW Rewarded ad unit** for the existing Ludo Paws AdMob app. Set **Reward amount = 2** and **Reward item = gems**; configure its server-side verification postback to the **same existing verified** endpoint: `https://api.ludopaws.rekixo.com/api/admob/ssv`. Keep the existing 5-Gem rewarded unit active, unchanged.
2. In GitHub repository **Settings → Secrets and variables → Actions → Variables** set BOTH `ADMOB_REWARDED_AD_UNIT_ID_V2` and `LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID_V2` to that identical **new** AdMob rewarded-unit ID (`ca-app-pub-<16 digits>/<10 digits>`). Do not overwrite legacy `ADMOB_REWARDED_AD_UNIT_ID` / `LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID`. Keep required safety flags and reward item (`gems`).
3. Dispatch the production **Cloudflare** workflow from the tested main commit and verify `/ready` exact `deploymentRevision`, `/api/store/status` returns `rewardedAdsAvailable: true` and `rewardedAdsV2Available: true`. The server accepts both signed 5-Gem and signed 2-Gem units, without trusting client-selected reward amounts.
4. **Only after backend verification**, build a signed new Android release from the **same main SHA**. The signed-release workflow rejects incomplete, equal or missing 2-Gem v2 settings, and requires live v2 availability.
5. Test on a **Play-installed physical device** in Internal Testing: SSV signature, new unit shows +2 Gems exactly once, 11+ voluntary views in one UTC day if AdMob fill allows, delayed SSV recovery, purchase/replay and same-account ownership, 5-Gem old APK, 2-Gem new APK, and prior targeted cosmetic progress. No synthetic ad loops; Google ad inventory/consent may still limit availability.

## Deployment safeguards
- No new AdMob unit configured: backend can deploy safely with **legacy unit still available** and `rewardedAdsV2Available: false`. The updated Android app must fail closed and not show ads until v2 is configured and deployed.
- An invalid/mismatched 2-Gem unit config fails Cloudflare preflight; an absent or equal v2 unit blocks new signed Android releases if ads are enabled.
- Old Android 10/day display cap cannot be removed retroactively. Rolling out the new Android build removes it for updated clients only.
- Signed SSV `reward_amount` must be 5 for legacy unit or 2 for v2 unit; mismatches fail closed. Google signature and idempotency rules are unchanged; never grant client-side Gems.
- No guarantee of unlimited filled ads, revenue or eligibility. Follow Google AdMob policies, user consent and family-oriented ad safety controls.

## Verification
CI Server tests cover old/new signed amounts and mismatches, replay, 14+ verified 2-Gem transactions, wallet progress, rollout configuration; Android CI + full instrumentation and CodeQL must be green before merge.

Full emulator instrumentation must run **on the PR before merge** via a `[run-instrumentation]` title on a same-repository pull request; the merge commit also includes the marker to rerun it on exact `main`. Check public and in-app disclosures for the version-specific reward and no cap.
