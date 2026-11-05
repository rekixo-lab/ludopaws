# Environment dice — next update

Four native Canvas skins are registered in the existing Dice registry and Shop → Dice:

| Shop name / ID | Artwork | Matching environments |
| --- | --- | --- |
| FOREST DEW / `dice_forest_dew` | Moss-green enamel, warm metal rim, leaf carving and dew | Autumn Forest, Dawn Meadow, Blossom Garden |
| LAGOON PEARL / `dice_lagoon_pearl` | Turquoise pearl finish, sandy rim, shell and wave engraving | Cloud Lagoon, Monsoon Mire |
| FROST CRYSTAL / `dice_frost_crystal` | Ice-blue crystal, beveled facets, snowflakes and diamond pips | Winter Haven |
| STARLIGHT / `dice_starlight` | Deep violet enamel, gold pips, crescent and constellation engraving | Astral, Moonlit Grove |

These are manually selectable cosmetics on any board; selecting a board does not replace the player's chosen die. Each costs 100 Gems, matching the existing Pearl Paw price, through the existing account-authoritative unlock path. No new Play product, direct purchase, ad unlock, collection change or automatic ownership grant is introduced. Published remote catalog values retain their existing precedence.

The renderer draws the face supplied by `DiceView`; it has no outcome-selection logic. Every face uses the conventional 1–6 pip layout, including a single pip for one. Decorative motifs stay near the edges/top, away from the pip grid. All four use distinct cached gradients and geometry, with an elevated top/right side. Paints, paths, pip arrays and shaders are allocated once per renderer, not per frame. Artwork scales with the existing view without bitmap assets or animation timers. The player's team color and the existing rolling/attention/deadline frame remain visible around the die, and the returned bounds feed the existing countdown drawing.

## Review and release boundary

Source review covered definitions → manifest → generated factories → Shop previews → selected in-game renderer, all six pip coordinate layouts, canvas save/restore balance, paint-state resets, matching Android/server IDs, categories and 100-Gem pricing. Generated sources were refreshed with the existing authoring script. Server JavaScript syntax and diff whitespace were checked. No tests, Android build, installation, CI, merge to main, backend deploy or Play release was run.

The app update contains the preview/renderers. The matching server update must also ship before the live backend can unlock these new IDs; until then, a new-ID purchase is rejected by server validation. Existing owned dice and referral/collection rewards are preserved.

Device checks still pending, after the user builds the update branch:

1. Open Shop → Dice; confirm all four real previews, names and 100-Gem offers. An older installed APK will not contain these renderers.
2. With the matching backend and sufficient Gems, unlock one, select it, reopen the app and confirm ownership/selection persistence. Verify an unlock spends only the shown price once.
3. Play local and online matches using the selected die; check each face, team outline, roll/attention feedback and online turn countdown. Also check the smallest supported screen and reduced-motion setting.
4. Switch boards and dice independently; confirm Classic/Pearl Paw and existing rewards still select normally.

This is source-reviewed implementation, not a claim of compiled or device-verified artwork.
