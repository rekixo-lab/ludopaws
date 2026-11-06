# Next-update UI theme refresh

Branch: `codex/next-update-comeback-bonus`, after rating commit `6f0a94c4`.

## Actual screen changes

- Rewards now has Gifts / Challenges / Collections tabs, a selected production-animal hero, Gem wallet row and fixed Back/Refresh navigation. Uses the existing arcade/plywood backdrop, carved wood cards, rounded typography and native game green/blue action buttons instead of the flat teal background and default Android button stack.
- Gifts: production Gem art, daily claim card, actual milestone progress, a gold-rimmed colour wheel with a paw hub and geometric pointer, readable odds, and a separate optional ad card. Reopening a claimed spin positions the wheel at the actual recorded prize. Reward details and purchase/Quick Match confirmations use the existing plywood dialog builder.
- Challenges: real progress bars, grey/earned gold Weekly Paws medal, optional share, play goals and a two-animal Quick Match entry. Profile's new badges use the same medal/pill style. Quick Match setup's new explanatory banner is now a themed card.
- Collections: separate owned-aware cards with the actual registered animal / board / dice previews, featured marker, owned count, clear missing-item Gem price and Shop preview action. Unknown/unavailable previews show a status label rather than substituting another item. No purchase or ad starts from rendering a preview.
- Room invitation: matching native wood card, decorative game pals, selectable room code, clear join action and short approval/expiry guidance. Browser fallback uses the same cream/wood, green/blue and paw language, with accessible focus and responsive layout. Routes, validation and security headers are unchanged.
- Exported share images reuse the real production 3D portrait cache and native plywood renderer; the previous flat teal / fallback 2D portrait is removed. Portrait preparation can report a retry instead of sharing the wrong animal. Screenshot/PNG output still contains no player name, account, wallet or room token. Temporary URI grants and background image compression remain.

## Code recheck

Reviewed new view factories against existing constructor/method signatures, tab state restoration, stable scroll updates, busy-action guards, wheel attachment / confirmed outcome callbacks, account-owned purchase/reward actions, and Back/Refresh availability. Domain/request/ledger code and reward odds/amounts are unchanged. The lifecycle/business methods below the Rewards renderer remain intact apart from themed dialogs, clearer Quick Match wording and loading/unavailable presentation copy.

Reviewed bounded 560dp content, wrap-content text/buttons, weighted narrow-screen layouts, cutout/system-bar insets, 48dp navigation targets, TalkBack status/progress labels, decorative preview descendants, cached/static artwork and reduced-motion spin behavior. These are source layout decisions, **not** evidence that clipping/accessibility or frame performance was verified on a device.

`git diff --check` and `node --check server/src/room-link-landing.js` completed. No tests, Gradle/build, app/emulator/device execution, screenshots, CI, deployment, main merge or Play release were performed. Native Google review UI is provider-owned and was not restyled. Browser appearance needs the matching future backend deployment; none was performed.

## Device review locations

Home → Rewards → each of Gifts / Challenges / Collections; Quick Match setup and its mode chooser; Profile → Paws Collection badges; finished match → Share; a shared Friends link on another phone. Check normal and large font, small screens, loading/offline/account changes, repeated tab switches, successful claim/spin/collection confirmation, canceled ads/dialogs and returning from the chooser. Live backend without next-update reward support shows the friendly offline/unavailable state; it cannot demonstrate server-backed claims or collection quotes.
