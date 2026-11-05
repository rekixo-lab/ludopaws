# Single 2-Gem Rewarded Ad and post-win Interstitial rollout

No production players currently use the deleted 5-Gem AdMob placements.

## AdMob production settings
- App ID: `ca-app-pub-9987799703505828~1411336271`
- Rewarded (2 gems): `ca-app-pub-9987799703505828/2670544646`
- Interstitial: `ca-app-pub-9987799703505828/9874247247`
- Reward amount: `2`, item: `gems`, SSV: `https://api.ludopaws.rekixo.com/api/admob/ssv`

## Operator actions after verified merge
1. Set GitHub Actions repository Variables `ADMOB_APP_ID`, `ADMOB_REWARDED_AD_UNIT_ID`, `LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID` and `ADMOB_INTERSTITIAL_AD_UNIT_ID` to the three IDs above. Both rewarded variables must use the SAME 2-Gem unit.
2. Remove obsolete `ADMOB_REWARDED_AD_UNIT_ID_V2` and `LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID_V2` if present. Keep `LUDOPROOF_ADMOB_REWARD_ITEM=gems`, ad safety approvals and signed callback protection.
3. From merged main, manually dispatch `deploy-cloudflare.yml`; check `/ready` exact `deploymentRevision` and `/api/store/status` with `rewardedAdsAvailable=true` and `rewardedAdsV2Available=true` (compatibility field).
4. Only then sign a new Android build from the SAME main commit. Internal Testing on a Play-installed device must confirm 2 Gems once for signed ad, replay protection, voluntary repeated ads (subject to inventory), SSV recovery and post-win interstitial. No ad cap or automatic loop.

No separate Cloudflare secret is required for these public Ad Unit IDs. Keep existing Worker secrets intact.
