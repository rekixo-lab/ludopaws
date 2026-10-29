# Board selection behavior

- Settings and Store share `CosmeticInventoryStore` ownership and selection rules.
- Content-ready, level-zero free cosmetics shipped in the app remain available even when an older account wallet does not include their IDs. Both the shipped definition and current catalog offer must be free; remote-only offers and paid unlocks still require server inventory.
- Selecting these free cosmetics writes a device-local appearance preference immediately, without a network request or synchronous disk write. Server wallet refreshes do not replace that preference. Selecting a server-owned item successfully clears the local override for that category.
- Store board cards update their selected state, checkmark, equipped badge and accessibility description in place. Board previews and the page are not rebuilt, preserving scroll position. Other unlocked boards show `TAP TO SELECT`.
- Server-backed selections show saving feedback and reject overlapping cosmetic updates. Failures retain the previous selection.
- Settings excludes unavailable content and refreshes the complete visible board, including chrome and the 3D scene. Selecting the current item does not reload it.

## Device verification pending

No tests, builds or installations were run, as requested. On a real device, check a pre-existing account with an older wallet: choose each free board in Settings and Store, reopen both screens and restart the app. Confirm exactly one equipped board, preserved Store scroll position, and matching gameplay artwork. Also check offline free selection, rapid taps, and successful/failed server-owned selections without altering paid ownership.
