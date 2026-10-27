# Pearl Paw dice

`dice_pearl_paw` is the second shipped dice skin, based on the approved Pearl Paw
concept: warm pearl enamel, blush/lavender reflections, dimensional top/side faces,
champagne-gold bevels, rose inset pips and a paw emblem for the one face.

The appearance is drawn by a dedicated native Canvas renderer, not a flat concept
image or a recolored Classic skin. Paths, gradients, paints and pip layouts are
reused per view. Existing DiceView roll/settle transforms, verified outcomes, turn
deadlines and team-colored attention remain authoritative. Only the front face
shows the outcome; decorative top/side surfaces do not invent extra roll values.
Faces two through six show their exact standard pip counts. The paw represents one.
Store and promotional previews show the one face without changing equipped dice.

## Unlock and availability

- Locked by default, not included in free/default inventory or legacy free grants.
- Unlock with **100 Gems OR 20 server-verified rewarded ads**; no dollar/Play product.
- Gem spending uses the existing confirmation, account ownership and idempotency flow.
- Ad progress is durable and capped at 20. Completing the twentieth verified ad grants
  ownership; the player can then select the dice. The configured ad unit's existing
  five-Gem reward is preserved separately from cosmetic progress.
- Client and account backend enforce the same two channels and prices, including
  when merging remote catalogs. Play product registration remains unchanged.
- Once owned, it is selectable in Store/settings and available to gameplay. It also
  participates in Home/gameplay promotions only while locked; no Coming Soon cards
  are enabled by this addition.

Ship the Android changes together with the matching account Worker changes. No
Play Console product is required. No build, test, lint, installation, deployment
or PR was performed; real-device visual and purchase/ad validation remains pending.
