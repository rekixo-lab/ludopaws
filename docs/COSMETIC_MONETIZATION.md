# Cosmetic pricing and release setup

Cock, Pig, Elephant, Horse and Rabbit cost 300 Gems or a non-consumable Google Play purchase each. All six released non-Classic boards use the same options. Astral Garden additionally supports 15 verified rewarded ads. Classic remains free. Lumi and Donkey retain their existing ad unlocks.

## Google Play Console prerequisites

Create and activate these one-time non-consumable products for the existing Android application. Set each product's USD base price to **$1.00**, then configure regional prices and availability in Play Console:

- `character_cock`
- `character_pig`
- `character_elephant`
- `character_horse`
- `character_rabbit`
- `board_astral`
- `board_cloud_lagoon`
- `board_autumn_forest`
- `board_blossom_garden`
- `board_winter_haven`
- `board_monsoon_mire`

The app displays the localized price returned by Google Play, not a hardcoded dollar amount. Missing products show price unavailable and cannot complete checkout. Source changes do not create or price products in Play Console.

## Backend rollout

Ship the Android and matching account/billing Worker changes together. The server enforces the 300-Gem price and permitted unlock channels even if an older remote catalog still describes these items as free. The existing authenticated, account-bound Play verification and purchase ledger grant ownership; merely choosing the Play unlock channel never grants an item.

Purchases are non-consumable and support restore. Repeated fulfillment is idempotent. Refunding a Play purchase removes Play-only ownership when no other valid grant remains and resets an affected selection to its free default. Gems and legacy ownership are retained.

Existing server-recorded board ownership remains unchanged. Accounts that existed before this pricing migration retain the five previously free characters through a one-time server migration. New accounts must unlock them. Offline preferences alone are not accepted as proof of paid ownership.

## Store presentation

Paws no longer contains the Coming Soon footer. Boards and Dice display only catalog entries with available content; unfinished placeholders remain in the underlying catalog but are not shown. Paid cosmetics do not appear in the Gem-pack list. Cards and featured previews route to the same unlock chooser and show Gems, localized Play pricing, and Astral's ad progress where appropriate.

No build, test, lint, install, deployment or Play Console changes were performed as part of this source update. Production purchasing requires the Console setup and backend rollout above.
