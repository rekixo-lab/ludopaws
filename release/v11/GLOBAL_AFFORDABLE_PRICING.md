# LudoPaws global affordable pricing — Play Console handoff

Status: Android/backend implementation is for the **second Play Store update**.
These are manually requested country-specific **India targets**, not prices
currently published in Google Play. Other 176 markets must be checked in Play
Console. The app must always display the native Google Play
`ProductDetails.formattedPrice`, never guessed INR/USD conversion.

## Set each one-time product in Google Play Console

| Product ID | Fulfillment | India target |
| --- | --- | --- |
| `remove_ads` | Permanent standard-ad removal + 5 Gems | ₹99 |
| `gems_300` | 300 Gems | ₹39 |
| `gems_1500` | 1,200 Gems | ₹129 |
| `gems_3500` | 3,000 Gems | ₹249 |
| `gems_20000` | 7,500 Gems | ₹499 |
| `gems_40000` | 16,000 Gems | ₹999 |
| `gems_200000` | 35,000 Gems | ₹1,999 |
| `character_panda` | Panda | ₹19 |
| `character_monkey` | Monkey | ₹19 |
| `character_cock` | Cock | ₹39 |
| `character_pig` | Pig | ₹39 |
| `character_elephant` | Elephant | ₹39 |
| `character_horse` | Horse | ₹39 |
| `character_rabbit` | Rabbit | ₹39 |
| `board_astral` | Astral Garden | ₹39 |
| `board_cloud_lagoon` | Cloud Lagoon | ₹39 |
| `board_autumn_forest` | Autumn Forest | ₹39 |
| `board_blossom_garden` | Blossom Garden | ₹39 |
| `board_winter_haven` | Winter Haven | ₹39 |
| `board_monsoon_mire` | Monsoon Mire | ₹39 |

All six Gem packs remain in the app Store. **Do not rename existing product IDs.**
The SKU `gems_1500` grants **1,200** Gems and `gems_3500` grants **3,000**
Gems. Google Play descriptions must match fulfillment, not legacy SKU suffixes.

Remove Ads and every direct cosmetic are **non-consumable**.
Gem packs are **consumable**. New Panda and Monkey products require creation
and regional activation in Play Console before their real-money option works.
Missing/unavailable Play details must never be replaced with hardcoded cash
prices; purchases must be verified server-side before grants.

## In-game catalog economy

The updated APK grants **2 Gems per server-verified rewarded ad**, with
**no app-imposed daily viewing cap**. Existing installed APKs continue using
the legacy 5-Gem ad unit and may keep their old 10/day preflight until updated.
Never change the original AdMob unit's reward from 5 to 2: the old APK promises
5. Each AdMob unit must sign its exact configured amount and the server must
match the signed unit to 5 (legacy) or 2 (updated). Cosmetic-targeted ads
continue granting one valid pre-existing unlock-progress step as well as
the unit's correct Gems; do not reset anyone's historic ad progress.
A verified delayed SSV must still be credited and token replays are idempotent.
No unlimited ad supply or AdMob policy exemption is implied.

- Dog, Cat, Goat, Duck remain free.
- Panda and Monkey cost 150 Gems (75 updated 2-Gem ads from a zero-Gem wallet) or a Play SKU.
  Previous accounts retain their former free ownership via migration v2.
- Lumi Goat: 50 Gems or legacy 10 verified cosmetic-targeted ads.
- Donkey: 75 Gems or legacy 15 verified cosmetic-targeted ads.
- Cock, Pig, Elephant, Horse, Rabbit: 300 Gems each; no separate cosmetic-ad
  progress from a new wallet ad. They may also use their existing Play SKU.
- Classic Board free. All six shipped premium Boards 300 Gems each or Play
  purchase. Astral's existing 15-targeted-ad offer remains, honoring progress.
- Classic Dice free. Pearl Paw Dice 100 Gems or its original 20 targeted ads.
- Eight standard Avatars free; Avatar 2 and Avatar 10 cost 100 Gems each.
- Coming Soon Boards: Checkers free, Chess 50 Gems, Diwali 75, Denim 75,
  Neon 150, Pirate 200, Alien 250, Penguin 250.
- Coming Soon Dice: Pumpkin free, Diwali 50, Football 75, Cricket 100,
  Summers 150, Colors 150, Heart 200.
- Coming Soon Packs: Safari requires verified level 8; Farm 300 Gems,
  Cozy 100 Gems, Wild requires a verified event, Pet 450, Jungle 550.
  **No Coming Soon content can charge the player before assets are complete.**

## Worldwide Play Store / production release checks

1. Configure/check **each** one-time product's price and availability across
   every country supported by the app, including the 177 distributions.
   Preview localized taxes/rounding and missing SKUs.
2. Use Play-installed license testers to test at least India, US,
   tax-inclusive EU, and an offer-unavailable Play country. Do not assume
   prices are universal or convert INR inside Android.
3. Confirm existing SKU entitlements, purchase cancellation, pending,
   refunds, duplicate tokens, account recovery, and offline state.
4. Confirm first-time users cannot equip unowned Panda/Monkey and legacy users
   keep their previously free entitlements on account re-open.
5. Test direct Panda/Monkey SKU purchase only **after** creating those IDs in
   Console, and verify their purchase/restore/refund ledger.
6. Validate separate 5-Gem legacy and 2-Gem updated AdMob callbacks, the
   updated no-daily-cap flow, safe delayed/replayed SSV, AdMob consent and
   historic cosmetic-targeted ad progress on Play-installed devices.
7. Check family-safe ads, neutral age screen, privacy consent,
   user-visible prices, and availability in all target territories.
8. Require green Android/server CI and Cloudflare deploy health before
   rolling out the second Play Store update.

The planning assumption of ~₹1 per 2–3 completed ads is **not** a measured
guarantee. Actual revenue varies with geography, fill, eCPM, and audience.
